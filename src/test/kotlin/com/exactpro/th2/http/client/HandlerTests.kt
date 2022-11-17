/*
 * Copyright 2022-2022 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.http.client

import com.exactpro.th2.common.event.Event
import com.exactpro.th2.common.schema.dictionary.DictionaryType
import com.exactpro.th2.conn.dirty.tcp.core.api.IChannel
import com.exactpro.th2.conn.dirty.tcp.core.api.IHandlerContext
import com.exactpro.th2.conn.dirty.tcp.core.api.IHandlerSettings
import com.exactpro.th2.http.client.dirty.handler.HttpHandler
import com.exactpro.th2.http.client.dirty.handler.HttpHandlerSettings
import com.exactpro.th2.http.client.dirty.handler.stateapi.IState
import io.netty.buffer.Unpooled
import mu.KotlinLogging
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.io.InputStream
import java.net.InetSocketAddress
import java.nio.charset.Charset

class HandlerTests {

    @Test
    fun `single response`() {
        val channel = mock<IChannel>()

        createHandler().let { handler ->
            handler.onOutgoing(channel, Unpooled.buffer().writeBytes("GET /test HTTP/1.1\r\nHost: localhost\r\nContent-Length: 12\r\n\r\nTest Body\r\n".toByteArray()), mutableMapOf())
            val response = handler.onReceive(channel, Unpooled.buffer().writeBytes(httpResponse.toByteArray()))
            Assertions.assertNotNull(response) { "Response must be parsed" }
            handler.onIncoming(channel, response!!)
        }

        createHandler().let { handler ->
            handler.onOutgoing(channel, Unpooled.buffer().writeBytes("GET /test HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\n\r\n6\r\nTest \r\n4\r\nBody\r\n0\r\n\r\n".toByteArray()), mutableMapOf())
            val response = handler.onReceive(channel, Unpooled.buffer().writeBytes(buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: plain/text\r\n")
                append("Transfer-Encoding: chunked\r\n")
                append("\r\n")
                append("6\r\n")
                append("Test \r\n")
                append("4\r\n")
                append("Body\r\n")
                append("0\r\n")
                append("\r\n")
            }.toByteArray()))
            Assertions.assertNotNull(response) { "Response must be parsed" }
            handler.onIncoming(channel, response!!)
        }

        createHandler().let { handler ->
            handler.onOutgoing(channel, Unpooled.buffer().writeBytes("GET /test HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\n\r\n6\r\nTest \r\n4\r\nBody\r\n0\r\n\r\n".toByteArray()), mutableMapOf())
            val response = handler.onReceive(channel, Unpooled.buffer().writeBytes(buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: plain/text\r\n")
                append("Transfer-Encoding: chunked\r\n")
                append("\r\n")
                append("0\r\n")
                append("\r\n")
            }.toByteArray()))
            Assertions.assertNotNull(response) { "Response must be parsed" }
            handler.onIncoming(channel, response!!)
        }
    }

    @Test
    fun `chunked response`() {
        createHandler().testResponse(httpResponse, 5, 1)
    }

    @Test
    fun `chunked response with part of new one`() {
        createHandler().testResponse(httpResponse + "HTTP/1.1", 5, 1)
    }

    @Test
    fun `few chunked responses with part of new one`() {
        createHandler().testResponse(httpResponse.repeat(3) + "HTTP/1.1", 15, 3)
    }

    @Test
    fun `ten chunked responses`() {
        createHandler().testResponse(httpResponse.repeat(10), 5, 10)
    }

    @Test
    fun `ten responses`() {
        createHandler().testResponse(httpResponse.repeat(10), 0, 10)
    }

    @Test
    fun `ten responses with part of new one`() {
        createHandler().testResponse(httpResponse.repeat(10) + "HTTP/1.1", 0, 10)
    }

    @Test
    fun `single response with part of new one`() {
        createHandler().testResponse(httpResponse + "HTTP/1.1", 0, 1)
    }

    @Test
    fun `body chunked response`() {
        createHandler().testChunkedBodyResponse("Please pass the test, body is chunked", 5)
    }

    private fun HttpHandler.testChunkedBodyResponse(body: String, chunkLength: Int) {
        val channel = mock<IChannel>()
        onOutgoing(channel, Unpooled.buffer().writeBytes("GET /test HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray()), mutableMapOf())
        var resultCount = 0

        if (chunkLength == 0) {
            val byteBuf = Unpooled.buffer().writeBytes("POST /test HTTP/1.1\r\nContent-Length: ${body.length}\r\nContent-Type: plain/text\r\n\r\n$body".toByteArray())
            while (byteBuf.isReadable) {
                val resultMessage = onReceive(channel, byteBuf) ?: break
                Assertions.assertEquals(httpResponse, resultMessage)
                resultCount++
                onIncoming(channel, resultMessage)
            }
        } else {
            val firstPartOfResponse = buildString {
                append("HTTP/1.1 200 ok\r\n")
                append("Transfer-Encoding: chunked\r\n")
                append("\r\n")
            }

            val byteBufFirstPart = Unpooled.buffer().writeBytes(firstPartOfResponse.toByteArray())
            while (byteBufFirstPart.isReadable) {
                val resultMessage = onReceive(channel, byteBufFirstPart) ?: break
                Assertions.assertEquals(httpResponse, resultMessage)
                resultCount++
                onIncoming(channel, resultMessage)
            }

            val buffer = StringBuilder().also {
                if (byteBufFirstPart.readableBytes() > 0) {
                    it.append(byteBufFirstPart.readCharSequence(byteBufFirstPart.readableBytes(), Charset.defaultCharset()))
                }
            }

            byteBufFirstPart.release()

            val chunks = body.chunked(body.length/chunkLength)

            for (i in 0..chunks.size) {
                val byteBuf = Unpooled.buffer().writeBytes(buffer.append(buildString {
                    if (i == chunks.size) {
                        append(0)
                        append("\r\n")
                        append("\r\n")
                    } else {
                        chunks[i].let { chunk ->
                            append(chunk.length)
                            append("\r\n")
                            append(chunk)
                            append("\r\n")
                        }
                    }
                }).toString().toByteArray())

                while (byteBuf.isReadable) {
                    val resultMessage = onReceive(channel, byteBuf) ?: break
                    Assertions.assertEquals(httpResponse, resultMessage)
                    resultCount++
                    onIncoming(channel, resultMessage)
                }

                buffer.clear()
                if (byteBuf.readableBytes() > 0) {
                    buffer.append(byteBuf.readCharSequence(byteBuf.readableBytes(), Charset.defaultCharset()))
                }
                byteBuf.release()
            }

            Assertions.assertEquals(1, resultCount)
            LOGGER.info { "Test with chunked body: [$body] was passed" }
        }
    }

    private fun HttpHandler.testResponse(data: String, chunkSize: Int, expectCount: Int) {
        val channel = mock<IChannel>()
        for (i in 0 until expectCount) {
            onOutgoing(channel, Unpooled.buffer().writeBytes("GET /test$i HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray()), mutableMapOf())
        }
        var resultCount = 0

        if (chunkSize == 0) {
            val byteBuf = Unpooled.buffer().writeBytes(data.toByteArray())
            while (byteBuf.isReadable) {
                val resultMessage = onReceive(channel, byteBuf) ?: break
                Assertions.assertEquals(httpResponse, resultMessage)
                resultCount++
            }
        } else {
            val chunks = data.chunked(chunkSize)
            val buffer = StringBuilder()
            chunks.forEach { chunk ->
                val byteBuf = Unpooled.buffer().writeBytes(buffer.append(chunk).toString().toByteArray())
                while (byteBuf.isReadable) {
                    val resultMessage = onReceive(channel, byteBuf) ?: break
                    Assertions.assertEquals(httpResponse, resultMessage)
                    resultCount++
                    onIncoming(channel, resultMessage)
                }

                buffer.clear()
                if (byteBuf.readableBytes() > 0) {
                    buffer.append(byteBuf.readCharSequence(byteBuf.readableBytes(), Charset.defaultCharset()))
                }
                byteBuf.release()
            }
        }

        Assertions.assertEquals(expectCount, resultCount) {"Test with:\n$data\nmust have been recognized as $expectCount messages"}
        LOGGER.info { "Test with $expectCount messages was passed" }
    }

    private fun createHandler(): HttpHandler {
        val context = object : IHandlerContext {

            override val settings: IHandlerSettings
                get() = error("Not yet implemented")

            override fun createChannel(address: InetSocketAddress, security: IChannel.Security, attributes: Map<String, Any>, autoReconnect: Boolean, reconnectDelay: Long, maxMessageRate: Int, vararg sessionSuffixes: String): IChannel {
                error("Not yet implemented")
            }

            override fun destroyChannel(channel: IChannel) = Unit

            override fun get(dictionary: DictionaryType): InputStream {
                error("Not yet implemented")
            }

            override fun send(event: Event) {
                error("Not yet implemented")
            }

        }
        val state = object : IState {}
        return HttpHandler(context, state, HttpHandlerSettings())
    }

    companion object {
        private val LOGGER = KotlinLogging.logger { this::class.java.simpleName }
        val httpResponse = """
            HTTP/1.1 200 OK 
            Content-Type: plain/text
            Content-Length: 205
            
            { "id" : 901, "name" : { "first":"Tom", "middle":"and", "last":"Jerry" }, "phones" : [ {"type" : "home", "number" : "1233333" }, {"type" : "work", "number" : "264444" }], "lazy" : false, "married" : null }
        """.trimIndent().replace("\n", "\r\n")
    }

}