package com.exactpro.th2.http.client.connection

import com.exactpro.th2.http.client.util.ServerIncluded
import com.exactpro.th2.http.client.util.simpleTest
import org.junit.jupiter.api.Test
import rawhttp.core.HttpVersion
import rawhttp.core.RawHttpHeaders
import rawhttp.core.RawHttpRequest
import rawhttp.core.RequestLine
import rawhttp.core.body.EagerBodyReader
import java.net.URI

class ClientServerTest: ServerIncluded() {

    companion object {
        const val `test request count`: Int = 4
    }

    @Test
    fun `GET connect-disconnect test`() = simpleTest(serverPort) { port ->
        mutableListOf<RawHttpRequest>().apply {
            repeat(`test request count`) {
                val body = """{ "key":"$it" }"""
                this += RawHttpRequest(RequestLine("GET", URI("/test"), HttpVersion.HTTP_1_1), RawHttpHeaders.newBuilder()
                    .apply {
                        with("Host", "localhost:$port")
                        if (it%2 == 0) {
                            with("Connection", "close")
                        }
                        with("Content-Length", body.length.toString())
                    }
                    .build(), EagerBodyReader(body.toByteArray()), null)
            }
        }
    }

    @Test
    fun `GET keep-alive response test`() = simpleTest(serverPort, keepAlive = true) { port ->
        mutableListOf<RawHttpRequest>().apply {
            repeat(`test request count`) {
                val body = """{ "key":"$it" }"""
                this += RawHttpRequest(RequestLine("GET", URI("/test"), HttpVersion.HTTP_1_1), RawHttpHeaders.newBuilder()
                    .apply {
                        with("Host", "localhost:$port")
                        with("Content-Length", body.length.toString())
                    }
                    .build(), EagerBodyReader(body.toByteArray()), null)
            }
            add(removeLast().withHeaders(RawHttpHeaders.newBuilder().with("Connection", "close").build(), true))
        }
    }

    @Test
    fun `POST keep-alive response test`() = simpleTest(serverPort, keepAlive = true) { port ->
        mutableListOf<RawHttpRequest>().apply {
            repeat(`test request count`) {
                val body = """{ "key":"$it" }"""
                this += RawHttpRequest(RequestLine("POST", URI("/test"), HttpVersion.HTTP_1_1), RawHttpHeaders.newBuilder()
                    .apply {
                        with("Host", "localhost:$port")
                        with("Content-Length", body.length.toString())
                    }
                    .build(), EagerBodyReader(body.toByteArray()), null)
            }
            add(removeLast().withHeaders(RawHttpHeaders.newBuilder().with("Connection", "close").build(), true))
        }
    }

    @Test
    fun `PUT keep-alive response test`() = simpleTest(serverPort, keepAlive = true) { port ->
        mutableListOf<RawHttpRequest>().apply {
            repeat(`test request count`) {
                val body = """{ "key":"$it" }"""
                this += RawHttpRequest(RequestLine("PUT", URI("/test"), HttpVersion.HTTP_1_1), RawHttpHeaders.newBuilder()
                    .apply {
                        with("Host", "localhost:$port")
                        with("Content-Length", body.length.toString())
                    }
                    .build(), EagerBodyReader(body.toByteArray()), null)
            }
            add(removeLast().withHeaders(RawHttpHeaders.newBuilder().with("Connection", "close").build(), true))
        }
    }

    @Test
    fun `DELETE keep-alive response test`() = simpleTest(serverPort, keepAlive = true) { port ->
        mutableListOf<RawHttpRequest>().apply {
            repeat(`test request count`) {
                val body = """{ "key":"$it" }"""
                this += RawHttpRequest(RequestLine("DELETE", URI("/test"), HttpVersion.HTTP_1_1), RawHttpHeaders.newBuilder()
                    .apply {
                        with("Host", "localhost:$port")
                        with("Content-Length", body.length.toString())
                    }
                    .build(), EagerBodyReader(body.toByteArray()), null)
            }
            add(removeLast().withHeaders(RawHttpHeaders.newBuilder().with("Connection", "close").build(), true))
        }
    }

    @Test
    fun `TRACE keep-alive response test`() = simpleTest(serverPort, keepAlive = true) { port ->
        mutableListOf<RawHttpRequest>().apply {
            repeat(`test request count`) {
                this += RawHttpRequest(RequestLine("TRACE", URI("/test"), HttpVersion.HTTP_1_1), RawHttpHeaders.newBuilder()
                    .apply {
                        with("Host", "localhost:$port")
                    }
                    .build(), null, null)
            }
            add(removeLast().withHeaders(RawHttpHeaders.newBuilder().with("Connection", "close").build(), true))
        }
    }

    @Test
    fun `PATCH keep-alive response test`() = simpleTest(serverPort, keepAlive = true) { port ->
        mutableListOf<RawHttpRequest>().apply {
            repeat(`test request count`) {
                val body = """{ "key":"$it" }"""
                this += RawHttpRequest(RequestLine("PATCH", URI("/test"), HttpVersion.HTTP_1_1), RawHttpHeaders.newBuilder()
                    .apply {
                        with("Host", "localhost:$port")
                        with("Content-Length", body.length.toString())
                    }
                    .build(), EagerBodyReader(body.toByteArray()), null)
            }
            add(removeLast().withHeaders(RawHttpHeaders.newBuilder().with("Connection", "close").build(), true))
        }
    }

    @Test
    fun `OPTIONS keep-alive response test`() = simpleTest(serverPort, keepAlive = true) { port ->
        mutableListOf<RawHttpRequest>().apply {
            repeat(`test request count`) {
                val body = """{ "key":"$it" }"""
                this += RawHttpRequest(RequestLine("OPTIONS", URI("/test"), HttpVersion.HTTP_1_1), RawHttpHeaders.newBuilder()
                    .apply {
                        with("Host", "localhost:$port")
                        with("Content-Length", body.length.toString())
                    }
                    .build(), EagerBodyReader(body.toByteArray()), null)
            }
            add(removeLast().withHeaders(RawHttpHeaders.newBuilder().with("Connection", "close").build(), true))
        }
    }

    //@Test
    //FIXME
    fun `HEAD keep-alive response test`() = simpleTest(serverPort, false, true, keepAlive = true) { port ->
        mutableListOf<RawHttpRequest>().apply {
            repeat(`test request count`) {
                val body = """{ "key":"$it" }"""
                this += RawHttpRequest(RequestLine("HEAD", URI("/test"), HttpVersion.HTTP_1_1), RawHttpHeaders.newBuilder()
                    .apply {
                        with("Host", "localhost:$port")
                        with("Content-Length", body.length.toString())
                    }
                    .build(), EagerBodyReader(body.toByteArray()), null)
            }
            add(removeLast().withHeaders(RawHttpHeaders.newBuilder().with("Connection", "close").build(), true))
        }
    }


    @Test
    fun `GET simple response test`() = simpleTest(serverPort) { port ->
        mutableListOf<RawHttpRequest>().apply {
            repeat(`test request count`) {
                val body = """{ "key":"$it" }"""
                this += RawHttpRequest(RequestLine("GET", URI("/test"), HttpVersion.HTTP_1_1), RawHttpHeaders.newBuilder()
                    .apply {
                        with("Host", "localhost:$port")
                        with("Connection", "close")
                        with("Content-Length", body.length.toString())
                    }
                    .build(), EagerBodyReader(body.toByteArray()), null)
            }
        }
    }

    @Test
    fun `POST simple response test`() = simpleTest(serverPort) { port ->
        mutableListOf<RawHttpRequest>().apply {
            repeat(`test request count`) {
                val body = """{ "key":"$it" }"""
                this += RawHttpRequest(RequestLine("POST", URI("/test"), HttpVersion.HTTP_1_1), RawHttpHeaders.newBuilder()
                    .apply {
                        with("Host", "localhost:$port")
                        with("Connection", "close")
                        with("Content-Length", body.length.toString())

                    }
                    .build(), EagerBodyReader(body.toByteArray()), null)
            }
        }
    }

    @Test
    fun `PUT simple response test`() = simpleTest(serverPort) { port ->
        mutableListOf<RawHttpRequest>().apply {
            repeat(`test request count`) {
                val body = """{ "key":"$it" }"""
                this += RawHttpRequest(RequestLine("PUT", URI("/test"), HttpVersion.HTTP_1_1), RawHttpHeaders.newBuilder()
                    .apply {
                        with("Host", "localhost:$port")
                        with("Connection", "close")
                        with("Content-Length", body.length.toString())
                    }
                    .build(), EagerBodyReader(body.toByteArray()), null)
            }
        }
    }

    @Test
    fun `DELETE simple response test`() = simpleTest(serverPort) { port ->
        mutableListOf<RawHttpRequest>().apply {
            repeat(`test request count`) {
                val body = """{ "key":"$it" }"""
                this += RawHttpRequest(RequestLine("DELETE", URI("/test"), HttpVersion.HTTP_1_1), RawHttpHeaders.newBuilder()
                    .apply {
                        with("Host", "localhost:$port")
                        with("Connection", "close")
                        with("Content-Length", body.length.toString())
                    }
                    .build(), EagerBodyReader(body.toByteArray()), null)
            }
        }
    }

    @Test
    fun `TRACE simple response test`() = simpleTest(serverPort) { port ->
        mutableListOf<RawHttpRequest>().apply {
            repeat(`test request count`) {
                this += RawHttpRequest(RequestLine("TRACE", URI("/test"), HttpVersion.HTTP_1_1), RawHttpHeaders.newBuilder()
                    .apply {
                        with("Host", "localhost:$port")
                        with("Connection", "close")
                        with("Content-Length", "0")
                    }
                    .build(), null, null)
            }
        }
    }

    @Test
    fun `PATCH simple response test`() = simpleTest(serverPort) { port ->
        mutableListOf<RawHttpRequest>().apply {
            repeat(`test request count`) {
                val body = """{ "key":"$it" }"""
                this += RawHttpRequest(RequestLine("PATCH", URI("/test"), HttpVersion.HTTP_1_1), RawHttpHeaders.newBuilder()
                    .apply {
                        with("Host", "localhost:$port")
                        with("Connection", "close")
                        with("Content-Length", body.length.toString())
                    }
                    .build(), EagerBodyReader(body.toByteArray()), null)
            }
        }
    }

    @Test
    fun `OPTIONS simple response test`() = simpleTest(serverPort) { port ->
        mutableListOf<RawHttpRequest>().apply {
            repeat(`test request count`) {
                val body = """{ "key":"$it" }"""
                this += RawHttpRequest(RequestLine("OPTIONS", URI("/test"), HttpVersion.HTTP_1_1), RawHttpHeaders.newBuilder()
                    .apply {
                        with("Host", "localhost:$port")
                        with("Connection", "close")
                        with("Content-Length", body.length.toString())
                    }
                    .build(), EagerBodyReader(body.toByteArray()), null)
            }
        }
    }

    //@Test
    //FIXME
    fun `CONNECT response test`() = simpleTest(serverPort, false, false) { port ->
        mutableListOf<RawHttpRequest>().apply {
            val body = """{ "key":"0" }"""
            this += RawHttpRequest(RequestLine("CONNECT", URI("/test"), HttpVersion.HTTP_1_1), RawHttpHeaders.newBuilder()
                .apply {
                    with("Host", "localhost:$port")
                    with("Content-Length", body.length.toString())
                }
                .build(), EagerBodyReader(body.toByteArray()), null)
        }
    }

    @Test
    fun `HEAD simple response test`() = simpleTest(serverPort, false, true) { port ->
        mutableListOf<RawHttpRequest>().apply {
            repeat(`test request count`) {
                val body = """{ "key":"$it" }"""
                this += RawHttpRequest(RequestLine("HEAD", URI("/test"), HttpVersion.HTTP_1_1), RawHttpHeaders.newBuilder()
                    .apply {
                        with("Host", "localhost:$port")
                        with("Connection", "close")
                        with("Content-Length", body.length.toString())
                    }
                    .build(), EagerBodyReader(body.toByteArray()), null)
            }
        }
    }
}