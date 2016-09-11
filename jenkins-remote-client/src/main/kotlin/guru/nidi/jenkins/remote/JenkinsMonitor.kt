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

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate

class JenkinsMonitor(val client: JenkinsClient, val refreshSeconds: Int, val dataDir: File,
                     loadState: Boolean, val maxProjects: Int,
                     val filterPrefix: String? = null,
                     val changeListener: ((List<Pair<BuildState, BuildState>>) -> Unit) = {}) {
    private val log = LoggerFactory.getLogger(JenkinsMonitor::class.java)
    private val mapper = ObjectMapper().registerModule(KotlinModule())
    private val state: MutableMap<String, BuildState>
    private var timer: Timer = Timer(true)

    init {
        dataDir.mkdirs()
        state = if (loadState) loadState() else HashMap()
    }

    fun start() {
        askJenkins()
        stop()
        timer = Timer(true)
        val interval = 1000L * refreshSeconds
        timer.scheduleAtFixedRate(interval, interval) {
            try {
                askJenkins()
            } catch(e: Exception) {
                log.error("error asking jenkins", e)
            }
        }
    }

    fun stop() {
        timer.cancel()
    }

    fun getState(): Map<String, BuildState> = state

    private fun askJenkins() {
        val changes = mutableListOf<Pair<BuildState, BuildState>>()

        fun askJobs(parent: String, jobContainer: JobContainer): Int {
            log.info("Asking ${jobContainer.jobs?.size ?: 0} of ${client.connect.server} $parent")
            var projs = 0
            if (jobContainer.jobs != null) {
                for (jobOverview in jobContainer.jobs!!) {
                    val key = (if (parent.length == 0) "" else "/") + jobOverview.name
                    if (filterPrefix == null || key.startsWith(filterPrefix)) {
                        val job = jobOverview.load(client)
                        projs += askJobs(key, job)
                        val lastBuild = job.lastBuild
                        if (lastBuild != null) {
                            val build = lastBuild.load(client)
                            if (!build.building) {
                                val newState = BuildState(key, build.number, build.result ?: "unknown",
                                        (build.culprits ?: emptyList<Person>()).map { it.fullName ?: "unknown" })
                                val lastState = state.get(key)
                                if (lastState != null) {
                                    if (lastState.color != newState.color) {
                                        changes.add(Pair(lastState, newState))
                                    }
                                }
                                state.put(key, newState)
                                projs++
                            }
                        } else {
                            state.remove(key)
                            projs--
                        }

                        if (maxProjects > 0 && projs > maxProjects) {
                            log.info("Too many projects")
                            return projs
                        }
                    }
                }
            }
            return projs
        }

        val overview = client.overview()
        askJobs("", overview)
        saveState(state)
        changeListener.invoke(changes)
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
            mapper.readValue(inp, object : TypeReference<Map<String, BuildState>>() {})
        }
    }

    private fun saveState(state: Map<String, BuildState>) {
        return FileOutputStream(dataFile()).use { out ->
            mapper.writeValue(out, state)
        }
    }
}

data class BuildState(val name: String, val id: Int, val color: String, val culprits: List<String>)