package guru.nidi.jenkins.remote

data class Build(val actions: List<Map<String, Any>>?, val artifacts: List<Artifact>?, val building: Boolean?,
                 val description: String?, val displayName: String?, val duration: Int?,
                 val estimatedDuration: Int?, val executor: String?, val fullDisplayName: String?,
                 val id: String?, val keepLog: Boolean?, val number: Int?, val queueId: Int?,
                 val result: String?, val timestamp: Long?, val url: String?, val builtOn: String?,
                 val changeSet: ChangeSet?, val culprits: List<Person>?)

data class Artifact(val displayPath: String?, val fileName: String?, val relativePath: String?)

data class ChangeSet(val items: List<ChangeItem>?, val kind: String?)

data class ChangeItem(val affectedPaths: List<String>?, val commitId: String?, val timestamp: Long?,
                      val author: Person?, val comment: String?, val date: String?, val id: String?,
                      val msg: String?, val paths: List<ChangePath>?)

data class ChangePath(val editType: String?, val file: String?)

data class Person(val absoluteUrl: String?, val fullName: String?)

