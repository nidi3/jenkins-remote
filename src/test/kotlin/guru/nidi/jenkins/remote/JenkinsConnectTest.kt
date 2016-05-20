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

    @Test(expected = JenkinsException::class)
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