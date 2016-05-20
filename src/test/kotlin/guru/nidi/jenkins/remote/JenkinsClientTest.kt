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

import org.junit.Assert.*
import org.junit.Assume
import org.junit.Test

class JenkinsClientTest {
    val apache = JenkinsClient(JenkinsConnect(Server.APACHE))
    val jenkins = JenkinsClient(JenkinsConnect(Server.JENKINS, verifyCertificate = false))

    val overview: Overview by lazy {
        apache.overview()
    }

    @Test
    fun overview() {
        assertNotNull(overview.version)
        assertNotNull(overview.mode)
        assertNotNull(overview.nodeDescription)
        assertNotNull(overview.description)
        assertNotNull(overview.primaryView)
        assertFalse(overview.jobs.isEmpty())
        assertFalse(overview.views.isEmpty())
    }

    @Test
    fun navigateJob() {
        val job = overview.jobs[0].load(apache)
        assertNotNull(job.name)
    }

    @Test
    fun job() {
        val job = jenkins.job("Core")
        assertEquals("Core", job.name)
        assertNotNull(job.displayName)
        assertNotNull(job.primaryView)
        assertFalse(job.healthReport.isEmpty())
        assertFalse(job.jobs!!.isEmpty())
        assertFalse(job.views!!.isEmpty())
    }

    @Test
    fun subjob() {
        val job = jenkins.job("Core", "pom")
        assertEquals("pom", job.name)
        assertNotNull(job.displayName)
        assertFalse(job.healthReport.isEmpty())
        assertNull(job.jobs)
        assertNull(job.views)
    }

    @Test
    fun build() {
        val build = jenkins.build(4, "Core", "jenkins_2.0")
        assertEquals("4", build.id)
    }

    @Test
    fun navigateBuild() {
        val job = jenkins.job("Core", "pom")
        val build = job.builds!![0].load(jenkins)
        assertNotNull(build.id)
    }

    @Test
    fun privateTest() {
        Assume.assumeNotNull(System.getenv("JENKINS_URL"))
        val client = JenkinsClient(JenkinsConnect(System.getenv("JENKINS_URL"), verifyCertificate = false,
                username = System.getenv("JENKINS_USER"), apiToken = System.getenv("JENKINS_TOKEN")))
        val o = client.overview()
        assertNotNull(o.version)
        val job = o.jobs[0].load(client)
        assertNotNull(job.name)
        val build = job.builds!![0].load(client)
        assertNotNull(build.id)
    }
}