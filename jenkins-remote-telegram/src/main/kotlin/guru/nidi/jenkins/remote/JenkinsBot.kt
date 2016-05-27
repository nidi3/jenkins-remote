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
import org.telegram.telegrambots.TelegramBotsApi
import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.api.objects.Update
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter

data class BotState(val monitors: MutableMap<String, JenkinsConnect>, val chats: MutableMap<Long, Chat>)
data class Chat(val monitors: MutableMap<String, Boolean>, var running: Boolean)

class JenkinsBot(val username: String, val token: String) : TelegramLongPollingBot() {
    private val mapper: ObjectMapper
    val dataDir = File(".")
    val state: BotState
    val monitors = mutableMapOf<String, JenkinsMonitor>()

    init {
        mapper = ObjectMapper().registerModule(KotlinModule())
        mapper.setConfig(mapper.deserializationConfig.without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
        state = loadState()
        for (monitor in state.monitors) {
            startMonitor(monitor.value)
        }
    }

    private fun startMonitor(connect: JenkinsConnect) {
        val instance = JenkinsMonitor(JenkinsClient(connect), 15 * 60, dataDir) { before, after ->
            for (chat in state.chats) {
                if (chat.value.running && chat.value.monitors.getOrElse(connect.server, { false })) {
                    sendMsg(chat.key, statusOf(after))
                }
            }
        }
        instance.start()
        monitors.put(connect.server, instance)
    }

    private fun dataFile(): File {
        val file = File(dataDir, "jenkins-bot.json")
        if (!file.exists()) {
            OutputStreamWriter(FileOutputStream(file)).use() { out -> out.write("""{"monitors":{},"chats":{}}""") }
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
        val parts = msg.text.split(" ")

        fun send(text: String) = sendMsg(msg.chatId, text)

        fun status(chat: Chat?) {
            if (chat == null) {
                send("No servers configured. Use /start <url> <username> <token>")
            } else {
                for (server in chat.monitors) {
                    if (server.value) {
                        send("${server.key}:\n" +
                                monitors[server.key]!!.getState().values
                                        .sortedBy { s -> s.color + s.name }
                                        .map { s -> statusOf(s) }
                                        .joinToString("\n"))
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
                    val newChat = state.chats.getOrPut(msg.chatId, { Chat(mutableMapOf(), true) })
                    newChat.monitors.put(connect.server, true)
                    newChat.running = true
                    saveState(state)
                    status(newChat)
                } catch(e: Exception) {
                    send("Could not connect to server: " + e.message)
                }
            }
        }

        fun end(chat: Chat?) {
            val server = parts.getOrNull(1)
            if (server == null) {
                send("No server given. Use /end <url>")
            } else if (chat == null) {
                send("No servers configured.")
            } else if (!chat.monitors.contains(server)) {
                send("Not monitoring $server")
            } else {
                chat.monitors.put(server, false)
                saveState(state)
                send("Monitoring for $server ended.")
            }
        }

        fun unknown() {
            send("Unknown command ${msg.text}\n" +
                    "/status - show status of all projects\n" +
                    "/start - start the monitoring of a server\n" +
                    "/end - end the monitoring of a server\n" +
                    "/stop - stop the bot\n" +
                    "/go - start the bot")
        }

        val chat = state.chats[msg.chatId]
        if (msg.text.startsWith("/")) {
            when (parts[0].substring(1)) {
                "status" -> status(chat)
                "start" -> start()
                "end" -> end(chat)
                "stop" -> stop(chat)
                "go" -> go(chat)
                else -> unknown()
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