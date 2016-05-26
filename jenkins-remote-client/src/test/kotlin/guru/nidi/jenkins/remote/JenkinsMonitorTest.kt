package guru.nidi.jenkins.remote

import org.junit.Assert.assertFalse
import org.junit.Assume
import org.junit.Test
import java.io.File

class JenkinsMonitorTest {

    @Test
    fun simple() {
        val jenkins = JenkinsClient(JenkinsConnect(Server.JENKINS, verifyCertificate = false))
        val monitor = JenkinsMonitor(jenkins, 100, File("target/test"))
        assertFalse(monitor.getState().isEmpty())
    }

    @Test
    fun privateTest() {
        Assume.assumeNotNull(System.getenv("JENKINS_URL"))
        val client = JenkinsClient(JenkinsConnect(System.getenv("JENKINS_URL"), verifyCertificate = false,
                username = System.getenv("JENKINS_USER"), apiToken = System.getenv("JENKINS_TOKEN")))
        val monitor = JenkinsMonitor(client, 100, File("target/test"))
        assertFalse(monitor.getState().isEmpty())
    }
}