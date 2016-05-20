package guru.nidi.jenkins.remote

data class View(val description:String?,val jobs:List<JobOverview>?,val name:String?,
                val property:List<Any>?,val url:String)
