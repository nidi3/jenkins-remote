package guru.nidi.jenkins.remote

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate

class JenkinsMonitor(val client: JenkinsClient, val refreshSeconds: Int, val dataDir: File) {
    private val mapper = ObjectMapper()
    private val state: MutableMap<String, BuildState>

    init {
        dataDir.mkdirs()
        state = loadState()
        askJenkins()
        val interval = 1000L * refreshSeconds
        Timer(true).scheduleAtFixedRate(interval, interval) {
            askJenkins()
        }
    }

    fun getState(): Map<String, BuildState> = state

    private fun askJenkins() {
        fun askJobs(parent: String, jobContainer: JobContainer) {
            if (jobContainer.jobs != null) {
                for (jobOverview in jobContainer.jobs!!) {
                    val job = jobOverview.load(client)
                    askJobs(parent + "/" + jobOverview.name, job)
                    val lastBuild = job.lastBuild
                    val key = parent + "/" + job.name
                    if (lastBuild != null) {
//                    val lastState = state.get(job.name)
                        val build = lastBuild.load(client)
                        state.put(key, BuildState(build.number, build.result ?: "unknown",
                                (build.culprits ?: emptyList<Person>()).map { it.fullName ?: "unknown" }))
                    } else {
                        state.remove(key)
                    }
                }
            }
        }

        val overview = client.overview()
        askJobs("", overview)
        saveState(state)
    }

    private fun dataFile(): File {
        val file = File(dataDir, "jenkins-" + client.connect.getName() + ".json")
        if (!file.exists()) {
            OutputStreamWriter(FileOutputStream(file)).use() { out -> out.write("{}") }
        }
        return file
    }

    private fun loadState(): MutableMap<String, BuildState> {
        return FileInputStream(dataFile()).use { inp ->
            mapper.readValue(inp, HashMap<String, BuildState>().javaClass)
        }
    }

    private fun saveState(state: Map<String, BuildState>) {
        return FileOutputStream(dataFile()).use { out ->
            mapper.writeValue(out, state)
        }
    }


}

data class BuildState(val id: Int, val color: String, val culprits: List<String>) {}