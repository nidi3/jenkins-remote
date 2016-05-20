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

import org.apache.http.util.EntityUtils
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.net.ssl.SSLHandshakeException

class JenkinsConnectTest {
    @Test
    fun simpleConnect() {
        val connect = JenkinsConnect(Server.APACHE)
        connect.get("") { res ->
            assertTrue(EntityUtils.toString(res.entity).length > 1000)
        }
    }

//    @Test(expected = JenkinsException::class)
    fun unknownCertNok() {
        val connect = JenkinsConnect(Server.JENKINS)
        connect.get("") {}
    }

    @Test
    fun unknownCertOk() {
        val connect = JenkinsConnect(Server.JENKINS, verifyCertificate = false)
        connect.get("") {}
    }
}