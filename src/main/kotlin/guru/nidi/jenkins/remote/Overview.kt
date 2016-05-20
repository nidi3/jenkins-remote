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

data class Overview(val version: String?, val mode: String?,
                    val nodeDescription: String?, val nodeName: String?,
                    val numExecutors: Int?, val description: String?, val jobs: List<JobOverview>,
                    val primaryView: View?, val quietingDown: Boolean?, val slaveAgentPort: Int?,
                    val useCrumbs: Boolean?, val useSecurity: Boolean?, val views: List<View>,
        //unknown...
                    val assignedLabels: List<Any>, val overallLoad: Any?, val unlabeledLoad: Any?)

data class View(val name: String, val url: String)

data class JobOverview(val name: String, val url: String, val color: String?) {
    fun load(client: JenkinsClient): Job {
        return client.job(name)
    }
}

