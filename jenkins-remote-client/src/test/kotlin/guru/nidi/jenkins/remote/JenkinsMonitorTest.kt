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

import org.junit.Assert.assertFalse
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JenkinsMonitorTest {

    @Rule @JvmField
    val output = TemporaryFolder()

    @Test
    fun simple() {
        val jenkins = JenkinsClient(JenkinsConnect(Server.JENKINS, verifyCertificate = false))
        val monitor = JenkinsMonitor(jenkins, 100, output.newFolder(), false, 100)
        monitor.start()
        assertFalse(monitor.getState().isEmpty())
    }

    @Test
    fun privateTest() {
        Assume.assumeNotNull(System.getenv("MY_JENKINS_URL"))
        val client = JenkinsClient(JenkinsConnect(System.getenv("MY_JENKINS_URL"), verifyCertificate = false,
                username = System.getenv("MY_JENKINS_USER"), apiToken = System.getenv("MY_JENKINS_TOKEN")))
        val monitor = JenkinsMonitor(client, 100, output.newFolder(), false, 100, "schalt")
        monitor.start()
        assertFalse(monitor.getState().isEmpty())
    }
}