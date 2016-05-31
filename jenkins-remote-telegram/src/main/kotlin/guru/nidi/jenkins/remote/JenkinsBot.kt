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

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.TelegramBotsApi
import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.api.objects.Update
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.*
import java.util.regex.Pattern

data class BotState(val monitors: MutableMap<String, JenkinsConnect>, val chats: MutableMap<Long, Chat>)
data class Chat(val monitors: LinkedHashMap<String, Boolean>, var running: Boolean)

class JenkinsBot(val username: String, val token: String) : TelegramLongPollingBot() {
    private val log = LoggerFactory.getLogger(JenkinsBot::class.java)
    private val mapper: ObjectMapper
    val dataDir = File(System.getenv("DATA_DIR") ?: ".")
    val state: BotState
    val monitors = mutableMapOf<String, JenkinsMonitor>()
    val readInterval = 15 * 60
    val maxProjects = 50

    init {
        dataDir.mkdirs()
        mapper = ObjectMapper().registerModule(KotlinModule())
        mapper.setConfig(mapper.deserializationConfig.without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
        state = loadState()
        for (monitor in state.monitors) {
            startMonitor(monitor.value)
        }
    }

    private fun startMonitor(connect: JenkinsConnect) {
        val instance = JenkinsMonitor(JenkinsClient(connect), readInterval, dataDir, maxProjects) { changes ->
            if (!changes.isEmpty()) {
                for (chat in state.chats) {
                    if (chat.value.running && chat.value.monitors.getOrElse(connect.server, { false })) {
                        sendMsg(chat.key, "${connect.server}:\n" +
                                changes.map { c -> statusOf(c.second) }
                                        .joinToString("\n"))
                    }
                }
            }
        }
        instance.start()
        monitors.put(connect.server, instance)
    }

    private fun dataFile(): File {
        val file = File(dataDir, "jenkins-bot.json")
        if (!file.exists()) {
            OutputStreamWriter(FileOutputStream(file)).use() { out ->
                out.write("""{"monitors":{},"chats":{}}""")
            }
        }
        return file
    }

    private fun loadState(): BotState {
        return FileInputStream(dataFile()).use { inp ->
            mapper.readValue(inp, BotState::class.java)
        }
    }

    private fun saveState(state: BotState) {
        return FileOutputStream(dataFile()).use { out ->
            mapper.writeValue(out, state)
        }
    }

    fun statusOf(s: BuildState): String {
        fun symbolOf(color: String): String = when (color) {
            "SUCCESS" -> "\u2705"
            "UNSTABLE" -> "\u26a0"
            "FAILURE" -> "\u26d4"
            else -> "?"
        }

        val name = s.name.substring(1)
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

        fun status(chat: Chat?) {
            if (chat == null) {
                send("No servers configured. Use /start <url> <username> <token>")
            } else {
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
            if (chat == null) {
                send("No servers configured. Use /start <url> <username> <token>")
            } else {
                chat.running = false
                saveState(state)
                send("Bot stopped.")
            }
        }

        fun go(chat: Chat?) {
            if (chat == null) {
                send("No servers configured. Use /start <url> <username> <token>")
            } else {
                chat.running = true
                saveState(state)
                send("Bot started.")
            }
        }

        fun start() {
            val server = parts.getOrNull(1)
            val username = parts.getOrNull(2)
            val token = parts.getOrNull(3)
            if (server == null) {
                send("No server given. Use /start <url> <username> <token>")
            } else {
                val connect = JenkinsConnect(server, username = username, apiToken = token, verifyCertificate = false)
                try {
                    startMonitor(connect)
                    state.monitors.put(connect.server, connect)
                    val newChat = state.chats.getOrPut(msg.chatId, { Chat(LinkedHashMap(), true) })
                    newChat.monitors.put(connect.server, true)
                    newChat.running = true
                    saveState(state)
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
            saveState(state)
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

        val chat = state.chats[msg.chatId]
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