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

import org.apache.http.HttpHost
import org.apache.http.HttpResponse
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.CredentialsProvider
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.impl.client.BasicAuthCache
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.ssl.SSLContexts
import java.io.Closeable
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException

class JenkinsConnect(server: String, port: Int = 0,
                     val username: String? = null, val apiToken: String? = null,
                     val verifyHostname: Boolean = true, val verifyCertificate: Boolean = true) : Closeable {

    val server: String
    private val client: CloseableHttpClient
    private val context: HttpClientContext?

    init {
        if (!server.startsWith("http://") && !server.startsWith("https://")) {
            throw IllegalAccessException("server must start with http:// or https://")
        }
        this.server = if (server.endsWith("/")) server.substring(0, server.length - 1) else server
        val colonPos = this.server.indexOf("://")
        val protocol = this.server.substring(0, colonPos)
        val defaultPort = if (protocol == "http") 80 else 443
        val target = HttpHost(this.server.substring(colonPos + 3), if (port == 0) defaultPort else port, protocol)
        val isAuth = username != null && apiToken != null

        val builder = HttpClients.custom()
        if (isAuth) {
            builder.setDefaultCredentialsProvider(creds(target, username!!, apiToken!!))
        }
        if (!verifyHostname) {
            builder.setSSLHostnameVerifier { url, sslSession -> true }
        }
        if (!verifyCertificate) {
            builder.setSSLContext(trustSslContext())
        }

        client = builder.build()
        context = if (isAuth) clientContext(target) else null
    }

    override fun close() {
        client.close()
    }

    fun getName(): String {
        return server.substring(server.indexOf("://") + 3).replace('/', '.')
    }

    fun <T> get(path: String, consumer: (HttpResponse) -> T): T {
        val get = HttpGet(uri(path))
        try {
            client.execute(get, context).use { response ->
                val status = response.statusLine
                if (status.statusCode >= 400) {
                    throw JenkinsException("Error response: ${status.statusCode}: ${status.reasonPhrase}")
                }
                return consumer(response)
            }
        } catch(e: SSLHandshakeException) {
            if (e.cause?.javaClass.toString().endsWith("ValidatorException")) {
                throw JenkinsException("Java does not recognize the HTTPS certificate. Either\n" +
                        "- Import it from the server into java's key store or\n" +
                        "- Set 'verifyCertificate = false' in the constructor.", e)
            }
            throw e
        }
    }

    private fun uri(path: String): String {
        val start = if (path.startsWith("http://") || path.startsWith("https://")) path
        else server + (if (path.startsWith("/")) "" else "/") + path
        return start + (if (start.endsWith("/")) "" else "/") + "api/json"
    }

    private fun trustSslContext(): SSLContext {
        val trustSslContext = SSLContexts.custom()
                .loadTrustMaterial(null, { chain, authType -> true })
                .build();
        return trustSslContext
    }

    private fun creds(target: HttpHost, username: String, apiToken: String): CredentialsProvider {
        val credsProvider = BasicCredentialsProvider()
        credsProvider.setCredentials(
                AuthScope(target),
                UsernamePasswordCredentials(username, apiToken))
        return credsProvider
    }

    private fun clientContext(target: HttpHost): HttpClientContext {
        val authCache = BasicAuthCache()
        authCache.put(target, BasicScheme())

        val context = HttpClientContext.create()
        context.authCache = authCache
        return context
    }
}

