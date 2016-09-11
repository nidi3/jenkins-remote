/*
 * Copyright (C) 2014 Stefan Niederhauser (nidin@gmx.ch)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package guru.nidi.jenkins.remote

import org.slf4j.LoggerFactory
import org.telegram.telegrambots.TelegramBotsApi
import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.api.objects.Update
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import java.io.File
import java.util.*
import java.util.regex.Pattern


class JenkinsBot(val username: String, val token: String) : TelegramLongPollingBot() {
    private val log = LoggerFactory.getLogger(JenkinsBot::class.java)
    val monitors = mutableMapOf<String, JenkinsMonitor>()
    val dataDir = File(System.getenv("DATA_DIR") ?: ".")
    val stateMgr: BotStateManager
    val readInterval = 15 * 60
    val maxProjects = 50

    init {
        stateMgr = BotStateManager(File(dataDir, "jenkins-bot.json"))
        for (monitor in stateMgr.state.monitors) {
            startMonitor(monitor.value, true)
        }
    }

    private fun startMonitor(config: MonitorConfig, load: Boolean) {
        val instance = JenkinsMonitor(JenkinsClient(config.connect), readInterval, dataDir, maxProjects, config.filter) { changes ->
            if (!changes.isEmpty()) {
                for (chat in stateMgr.state.chats) {
                    if (chat.value.running && chat.value.monitors.getOrElse(config.connect.server, { false })) {
                        sendMsg(chat.key, "${config.connect.server}:\n" +
                                changes.map { c -> statusOf(c.second) }
                                        .joinToString("\n"))
                    }
                }
            }
        }
        if (load) {
            instance.loadState()
        }
        instance.start()
        monitors.put(config.connect.server, instance)
    }

    fun statusOf(s: BuildState): String {
        fun symbolOf(color: String): String = when (color) {
            "SUCCESS" -> "\u2705"
            "UNSTABLE" -> "\u26a0"
            "FAILURE" -> "\u26d4"
            else -> "?"
        }

        val name = s.name
        val symbol = symbolOf(s.color)
        val culprits = if (s.culprits.isEmpty()) ""
        else s.culprits.joinToString(prefix = "(", postfix = ")")
        return "$symbol $name $culprits"
    }

    fun sendMsg(chatId: Long, text: String) {
        val sm = SendMessage()
        sm.chatId = chatId.toString()
        sm.text = text
        sendMessage(sm)
    }

    override fun onUpdateReceived(update: Update) {
        val msg = update.message
        val parts = msg.text.split(Pattern.compile("\\s+|_"))

        fun send(text: String) = sendMsg(msg.chatId, text)

        fun withChat(chat: Chat?, action: (Chat) -> Unit) {
            if (chat == null) {
                send("No servers configured. Use /start <url>?<filter> <username> <token>")
            } else {
                action(chat)
            }
        }

        fun status(chat: Chat?) {
            withChat(chat) { chat ->
                var i = 0
                for (server in chat.monitors) {
                    if (server.value) {
                        i++
                        val states = monitors[server.key]!!.getState().values
                        send("$i. ${server.key}:\n" +
                                states.sortedBy { s -> s.color + s.name }
                                        .map { s -> statusOf(s) }
                                        .joinToString("\n") +
                                if (states.size >= maxProjects) "\n...and more" else ""
                        )
                    }
                }
            }
        }

        fun stop(chat: Chat?) {
            withChat(chat) { chat ->
                chat.running = false
                stateMgr.save()
                send("Bot stopped.")
            }
        }

        fun go(chat: Chat?) {
            withChat(chat) { chat ->
                chat.running = true
                stateMgr.save()
                send("Bot started.")
            }
        }

        fun start() {
            val url = parts.getOrNull(1)
            val username = parts.getOrNull(2)
            val token = parts.getOrNull(3)
            if (url == null) {
                send("No server given. Use /start <url>?<filter> <username> <token>")
            } else {
                val qpos = url.indexOf('?')
                val server = if (qpos < 0) url else url.substring(0, qpos)
                val filter = if (qpos < 0) null else url.substring(qpos + 1)
                val connect = JenkinsConnect(server, username = username, apiToken = token, verifyCertificate = false)
                val config = MonitorConfig(connect, filter)
                try {
                    startMonitor(config, false)
                    stateMgr.state.monitors.put(config.connect.server, config)
                    val newChat = stateMgr.state.chats.getOrPut(msg.chatId, { Chat(LinkedHashMap(), true) })
                    newChat.monitors.put(config.connect.server, true)
                    newChat.running = true
                    stateMgr.save()
                    status(newChat)
                } catch(e: Exception) {
                    send("Sorry, there was a problem trying to get project status.")
                    log.error("Problem during start", e)
                }
            }
        }

        fun end(chat: Chat?) {
            val serverParam = parts.getOrNull(1)
            if (chat == null) {
                return send("No servers configured.")
            }
            if (serverParam == null) {
                var i = 0
                var s = ""
                for (server in chat.monitors) {
                    if (server.value) {
                        i++
                        s += "/end_$i to end ${server.key}\n"
                    }
                }
                return send(s)
            }
            val serverKey: String
            try {
                val serverI = serverParam.toInt()
                if (serverI < 1 || serverI > chat.monitors.size) {
                    return send("No server $serverI")
                }
                val iter = chat.monitors.iterator()
                for (i in 0..serverI - 2) iter.next()
                serverKey = iter.next().key
            } catch(e: NumberFormatException) {
                serverKey = serverParam
            }

            if (!chat.monitors.contains(serverKey)) {
                return send("Not monitoring $serverKey")
            }
            chat.monitors.put(serverKey, false)
            stateMgr.save()
            send("Monitoring for $serverKey ended.")
        }

        fun info(prefix: String) {
            send("""
$prefix
/info - Show this info
/status - Show that status of all projects
/start - Start monitoring a server
/end - End monitoring a server
/stop - Stop the bot
/go - Start the bot
""")
        }

        val chat = stateMgr.state.chats[msg.chatId]
        if (msg.text.startsWith("/")) {
            val atPos = parts[0].indexOf('@')
            val command = if (atPos < 0) parts[0].substring(1) else parts[0].substring(1, atPos)
            when (command) {
                "status" -> status(chat)
                "start" -> start()
                "end" -> end(chat)
                "stop" -> stop(chat)
                "go" -> go(chat)
                "info" -> info("")
                else -> info("Unknown command ${msg.text}")
            }
        }
    }

    override fun getBotUsername(): String {
        return username
    }

    override fun getBotToken(): String {
        return token
    }
}

fun main(args: Array<String>) {
    val api = TelegramBotsApi()
    api.registerBot(JenkinsBot(System.getenv("JENKINS_BOT_NAME"), System.getenv("JENKINS_BOT_TOKEN")))
}