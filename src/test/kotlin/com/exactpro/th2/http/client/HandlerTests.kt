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