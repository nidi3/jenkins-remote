package guru.nidi.jenkins.remote

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.*

data class BotState(val monitors: MutableMap<String, MonitorConfig>, val chats: MutableMap<Long, Chat>)

class MonitorConfig(val connect: JenkinsConnect, val filter: String?)

data class Chat(val monitors: LinkedHashMap<String, Boolean>, var running: Boolean)

class BotStateManager(val dataFile: File) {
    private val mapper: ObjectMapper
    val state: BotState

    init {
        dataFile.parentFile.mkdirs()
        mapper = ObjectMapper().registerModule(KotlinModule())
        mapper.setConfig(mapper.deserializationConfig.without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
        state = load()
    }

    private fun load(): BotState {
        return FileInputStream(dataFile()).use { inp ->
            mapper.readValue(inp, BotState::class.java)
        }
    }

    fun save() {
        return FileOutputStream(dataFile()).use { out ->
            mapper.writeValue(out, state)
        }
    }

    private fun dataFile(): File {
        if (!dataFile.exists()) {
            OutputStreamWriter(FileOutputStream(dataFile)).use() { out ->
                out.write("""{"monitors":{},"chats":{}}""")
            }
        }
        return dataFile
    }
}