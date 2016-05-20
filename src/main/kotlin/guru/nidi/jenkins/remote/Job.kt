package guru.nidi.jenkins.remote

data class Job(val actions: List<Any>, val description: String?, val displayName: String?,
               val displayNameOrNull: String?, val name: String?, val url: String?,
               val buildable: Boolean?, val builds: List<BuildOverview>?, val firstBuild: BuildOverview?,
               val lastBuild: BuildOverview?, val lastCompletedBuild: BuildOverview?, val lastFailedBuild: BuildOverview?,
               val lastStableBuild: BuildOverview?, val lastSuccessfulBuild: BuildOverview?,
               val lastUnstableBuild: BuildOverview?, val lastUnsuccessfulBuild: BuildOverview?,
               val nextBuildNumber: Int?, val property: List<Any>?, val queueItem: Any?,
               val concurrentBuild: Boolean?, val scm: Any?, val modules: List<Module>?,
               val upstreamProjects: List<Any>?, val downstreamProjects: List<Any>?,
               val color: String?, val inQueue: Boolean?, val keepDependencies: Boolean?,
               val healthReport: List<HealthReport>, val jobs: List<JobOverview>?,
               val primaryView: View?, val views: List<View>?)

data class HealthReport(val description: String?, val iconClassName: String?,
                        val iconUrl: String?, val score: Int?)

data class BuildOverview(val number: Int?, val url: String) {
    fun load(client: JenkinsClient): Build {
        return client.byUrl(url, Build::class.java)
    }
}

data class Module(val name: String?, val url: String?, val color: String?, val displayName: String?)