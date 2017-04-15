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

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule

class JenkinsClient(val connect: JenkinsConnect) {
    private val mapper: ObjectMapper

    init {
        mapper = ObjectMapper().registerModule(KotlinModule())
        mapper.setConfig(mapper.deserializationConfig.without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
    }


    fun overview(): Overview {
        return connect.get("") { res ->
            val overview = mapper.readValue(res.entity.content, Overview::class.java)
            overview.copy(version = res.getFirstHeader("X-Jenkins")?.value)
        }
    }

    fun job(name: String, vararg subs: String): Job {
        val path = (listOf<String>(name) + subs.asList()).joinToString("/job/")
        return byUrl("job/$path", Job::class.java)
    }

    fun build(build: Int, name: String, vararg subs: String): Build {
        val path = (listOf<String>(name) + subs.asList()).joinToString("/job/")
        return byUrl("job/$path/$build", Build::class.java)
    }

    fun view(name: String): View {
        return byUrl("view/$name", View::class.java)
    }

    fun <T> byUrl(url: String, type: Class<T>): T {
        return connect.get(url) { res ->
            mapper.readValue(res.entity.content, type)
        }
    }
}

