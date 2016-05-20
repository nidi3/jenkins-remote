import guru.nidi.jenkins.remote.JenkinsConnect
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.impl.client.BasicAuthCache
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.HttpClients
import org.apache.http.ssl.SSLContexts
import org.junit.Test

class Test {
    @Test
    fun connect() {
        val connect = JenkinsConnect("https://ci.schaltstelle.ch")
        val target = HttpHost("ci.schaltstelle.ch", 443,"https")

        val credsProvider = BasicCredentialsProvider()
        credsProvider.setCredentials(
                AuthScope(target),
                UsernamePasswordCredentials("read-only", "95bd3c5d7c37d557ce41c0b387254ac6"))

        val sslcontext = SSLContexts.custom()
//                .useTLS()
                .loadTrustMaterial(null, {
                    chain, authType ->
                    true
                })
                .build();
//        val sslSessionStrategy = SSLIOSessionStrategy(sslcontext, new AllowAll());

        val authCache = BasicAuthCache()
        // Generate BASIC scheme object and add it to the local
        // auth cache
        val basicAuth = BasicScheme()
        authCache.put(target, basicAuth)

        // Add AuthCache to the execution context
        val localContext = HttpClientContext.create()
        localContext.authCache = authCache


        val client = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                .setSSLHostnameVerifier { url, sslSession -> true }
                .setSSLContext(sslcontext)
                .build()

//        client.params.authenticationPreemptive = true

        val get = HttpGet("https://ci.schaltstelle.ch/api/json")
        try {
            val result = client.execute(get,localContext)
            println("Return code: ${result}")
        } finally {
            get.releaseConnection()
        }
    }
}
