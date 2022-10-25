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

package com.exactpro.th2.http.client.dirty.handler

import com.exactpro.th2.common.grpc.MessageID
import com.exactpro.th2.common.grpc.RawMessage
import com.exactpro.th2.conn.dirty.tcp.core.api.IChannel
import com.exactpro.th2.conn.dirty.tcp.core.api.IHandler
import com.exactpro.th2.conn.dirty.tcp.core.api.IHandlerContext
import com.exactpro.th2.conn.dirty.tcp.core.util.eventId
import com.exactpro.th2.conn.dirty.tcp.core.util.toByteBuf
import com.exactpro.th2.http.client.dirty.handler.codec.DirtyHttpClientCodec
import com.exactpro.th2.http.client.dirty.handler.data.DirtyHttpMessage
import com.exactpro.th2.http.client.dirty.handler.data.DirtyHttpResponse
import com.exactpro.th2.http.client.dirty.handler.stateapi.IState
import com.google.auto.service.AutoService
import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http.HttpMethod
import mu.KotlinLogging
import org.apache.commons.lang3.exception.ExceptionUtils
import java.net.InetSocketAddress
import java.util.Queue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference


@AutoService(IHandler::class)
open class HttpHandler(private val context: IHandlerContext, private val state: IState, private val settings: HttpHandlerSettings): IHandler {
    private lateinit var hostValue: String

    private val responseOutputQueue = ConcurrentLinkedQueue<DirtyHttpResponse>()

    private val httpClientCodec = DirtyHttpClientCodec()

    private val httpMode = AtomicReference(HttpMode.DEFAULT)
    private val lastMethod = AtomicReference<HttpMethod?>(null)

    private var isLastResponse = AtomicBoolean(false)
    private val dialogueQueue: Queue<Pair<Map<String, String>, (DirtyHttpResponse) -> Unit>> = ConcurrentLinkedQueue()

    private var address: InetSocketAddress = InetSocketAddress(settings.host, settings.port)

    @Volatile
    private var channel: IChannel? = null

    override fun onStart() {
        isLastResponse.set(false)
        httpMode.set(HttpMode.DEFAULT)
        lastMethod.set(null)
        channel = context.createChannel(address, settings.security, mapOf(), false, 0L, Integer.MAX_VALUE)
    }

    override fun onOutgoing(channel: IChannel, message: ByteBuf, metadata: MutableMap<String, String>) {
        try {
            when (val mode = httpMode.get()) {
                HttpMode.DEFAULT -> httpClientCodec.onRequest(message).let { request ->
                    isLastResponse.set(!request.isKeepAlive())
                    settings.defaultHeaders.forEach {
                        if (!request.headers.contains(it.key)){
                            request.headers[it.key] = it.value.joinToString(",")
                        }
                    }
                    if (!request.headers.contains(HOST)){
                        request.headers[HOST] = hostValue
                    }
                    lastMethod.set(request.method)
                    state.onRequest(channel, request)
                    LOGGER.debug { "Sending request: $request" }
                    dialogueQueue.offer (metadata to { response: DirtyHttpResponse ->
                        state.onResponse(channel, response, request)
                    })
                }
                HttpMode.CONNECT -> LOGGER.trace { "$mode: Sending data passing as tcp package" }
                else -> error("Unsupported http mode: $mode")
            }
        } catch (e: Exception) {
            LOGGER.error(e) { "Cannot handle request" }
        } finally {
            message.readerIndex(0)
        }
    }

    override fun onIncoming(channel: IChannel, message: ByteBuf): Map<String, String> {
        when (val mode = httpMode.get()) {
            HttpMode.DEFAULT -> responseOutputQueue.poll().let { response ->
                if (response.decoderResult.isFailure) {
                    throw response.decoderResult.cause()
                }
                LOGGER.debug { "Received response: $response" }
                when {
                    isLastResponse.get() || response.code >= 400 -> context.destroyChannel(channel)
                    response.isKeepAlive() -> Unit
                    else -> context.destroyChannel(channel) // all else are closing cases
                }

                when(lastMethod.get()) {
                    HttpMethod.CONNECT -> if (response.code == 200) httpMode.set(HttpMode.CONNECT)
                }
                val (metadata, dialogue) = dialogueQueue.poll()
                dialogue.invoke(response)
                return metadata
            }
            HttpMode.CONNECT -> LOGGER.trace { "$mode: Received data passing as tcp package" }
            else -> error("Unsupported http mode: $mode")
        }

        return emptyMap()
    }

    override fun onReceive(channel: IChannel, buffer: ByteBuf): ByteBuf? {
        if (httpMode.get() == HttpMode.CONNECT) return buffer
        return httpClientCodec.onResponse(buffer)?.let {
            LOGGER.debug { "Response message was decoded" }
            responseOutputQueue.offer(it)
            it.reference
        }
    }

    override fun send(message: RawMessage): CompletableFuture<MessageID> = checkNotNull(channel) { "To send message channel must be initialized" }.let { channel ->
        if (!channel.isOpen) {
            channel.runCatching { open().get() }.getOrElse { throw ExceptionUtils.rethrow(it) }
        }

        channel.send(message.body.toByteBuf(), message.metadata.propertiesMap, message.eventId, IChannel.SendMode.HANDLE_AND_MANGLE)
    }

    private fun DirtyHttpMessage.isKeepAlive(): Boolean {
        return version.minorVersion() == 1 && !(this.headers[CONNECTION]?.equals("close", true) ?: false) || version.minorVersion() == 0 && this.headers[CONNECTION]?.equals("keep-alive", true) ?: false
    }

    override fun onClose(channel: IChannel) {
        dialogueQueue.clear()
        responseOutputQueue.clear()
        state.onClose()
        if (isLastResponse.get() || lastMethod.get() == HttpMethod.CONNECT) {
            LOGGER.debug { "Closing channel due last request/response" }
        }
    }

    override fun close() {
        state.close()
        httpClientCodec.close()
    }

    override fun onOpen(channel: IChannel) {
        this.channel = channel
        hostValue = channel.address.let { "${it.hostString}:${it.port}" }
        state.onOpen()
    }

    companion object {
        private val LOGGER = KotlinLogging.logger { this::class.java.simpleName }
        const val CONNECTION = "Connection"
        const val HOST = "Host"
    }

    private enum class HttpMode {
        CONNECT,
        DEFAULT
    }
}