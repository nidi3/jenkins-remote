/*
 * Copyright © 2014 Stefan Niederhauser (nidin@gmx.ch)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package guru.nidi.jenkins.remote

interface JobContainer {
    val jobs: List<JobOverview>?
}

data class Job(val actions: List<Any>, val description: String?, val displayName: String?,
               val displayNameOrNull: String?, val name: String, val url: String?,
               val buildable: Boolean?, val builds: List<BuildOverview>?, val firstBuild: BuildOverview?,
               val lastBuild: BuildOverview?, val lastCompletedBuild: BuildOverview?, val lastFailedBuild: BuildOverview?,
               val lastStableBuild: BuildOverview?, val lastSuccessfulBuild: BuildOverview?,
               val lastUnstableBuild: BuildOverview?, val lastUnsuccessfulBuild: BuildOverview?,
               val nextBuildNumber: Int?, val property: List<Any>?, val queueItem: Any?,
               val concurrentBuild: Boolean?, val scm: Any?, val modules: List<Module>?,
               val upstreamProjects: List<Any>?, val downstreamProjects: List<Any>?,
               val color: String?, val inQueue: Boolean?, val keepDependencies: Boolean?,
               val healthReport: List<HealthReport>, override val jobs: List<JobOverview>?,
               val primaryView: ViewOverview?, val views: List<ViewOverview>?,
               val activeConfigurations: List<JobOverview>?) : JobContainer

data class HealthReport(val description: String?, val iconClassName: String?,
                        val iconUrl: String?, val score: Int?)

data class BuildOverview(val number: Int, val url: String) {
    fun load(client: JenkinsClient): Build {
        return client.byUrl(url, Build::class.java)
    }
}

data class Module(val name: String?, val url: String, val color: String?, val displayName: String?) {
    fun load(client: JenkinsClient): Job {
        return client.byUrl(url, Job::class.java)
    }
}