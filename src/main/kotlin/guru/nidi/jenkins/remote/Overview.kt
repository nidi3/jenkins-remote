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

