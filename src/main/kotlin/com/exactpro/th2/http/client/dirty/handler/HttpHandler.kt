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
import com.exactpro.th2.conn.dirty.tcp.core.api.IChannel.SendMode.HANDLE_AND_MANGLE
import com.exactpro.th2.conn.dirty.tcp.core.api.IHandler
import com.exactpro.th2.conn.dirty.tcp.core.api.IHandlerContext
import com.exactpro.th2.conn.dirty.tcp.core.util.eventId
import com.exactpro.th2.conn.dirty.tcp.core.util.toByteBuf
import com.exactpro.th2.http.client.dirty.handler.api.ISessionManager
import com.exactpro.th2.netty.bytebuf.util.contains
import com.google.auto.service.AutoService
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufInputStream
import io.netty.buffer.ByteBufOutputStream
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.DuplicatedByteBuf
import mu.KotlinLogging
import rawhttp.core.RawHttpRequest
import rawhttp.core.RawHttpResponse
import rawhttp.core.RawHttpResponse.shouldCloseConnectionAfter
import java.net.InetSocketAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.failedFuture
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.text.Charsets.UTF_8

@AutoService(IHandler::class)
class HttpHandler(
    private val context: IHandlerContext,
    private val manager: ISessionManager,
    private val settings: HttpHandlerSettings,
) : IHandler {
    @Volatile private lateinit var channel: IChannel

    private val requests = ArrayBlockingQueue<RequestInfo>(settings.requestQueueSize)

    override fun onStart() {
        channel = context.createChannel(
            InetSocketAddress(settings.host, settings.port),
            settings.security,
            mapOf(),
            false,
            0L,
            Integer.MAX_VALUE
        ).also(IChannel::open)
    }

    override fun onOpen(channel: IChannel) = manager.onOpen(channel)

    override fun onReceive(channel: IChannel, buffer: ByteBuf): ByteBuf? {
        if (!buffer.contains(BODY_SEPARATOR)) return null // to prevent RawHttp from parsing incomplete responses

        val startIndex = buffer.readerIndex()

        return try {
            // copy instead of slice to prevent mangling of the last response before immediate channel close
            ResponseByteBuf(buffer.readResponse(), buffer.copy(startIndex, buffer.readerIndex() - startIndex))
        } catch (e: Exception) {
            buffer.readerIndex(startIndex)
            LOGGER.trace(e) { "Failed to read (probably partial) response from buffer: ${buffer.hexDump}" }
            null
        }
    }

    override fun onIncoming(channel: IChannel, message: ByteBuf): Map<String, String> {
        val holder = requireNotNull(message as? ResponseByteBuf) { "Message is not a response byte buffer: $message" }
        val response = holder.response

        LOGGER.debug { "Received response in session '${channel.sessionAlias}':\n${message.toString(UTF_8)}" }

        val info = checkNotNull(requests.poll()) { "No requests in queue for response in session '${channel.sessionAlias}':\n${message.toString(UTF_8)}" }
        val request = info.request

        manager.onResponse(channel, response, request)

        if (shouldCloseConnectionAfter(response)) {
            LOGGER.debug { "Server requested to close channel for session: ${channel.sessionAlias}" }
            context.destroyChannel(channel)
        }

        return info.metadata + mapOf(METHOD_PROPERTY to request.method, URI_PROPERTY to request.uri.toString())
    }

    override fun onOutgoing(channel: IChannel, message: ByteBuf, metadata: MutableMap<String, String>) {
        if (requests.remainingCapacity() == 0) {
            LOGGER.warn { "Request queue is full for session: ${channel.sessionAlias}" }
            var timeout = READINESS_TIMEOUT
            while (requests.remainingCapacity() == 0 && --timeout != 0L) Thread.sleep(1)
            if (requests.remainingCapacity() == 0) throw IllegalStateException("Request queue has been full for $READINESS_TIMEOUT ms for session: ${channel.sessionAlias}")
            LOGGER.info { "Request queue is free for session: ${channel.sessionAlias}" }
        }

        val request = message.markReaderIndex().readRequest().also { message.resetReaderIndex() }

        val preparedRequest = manager.onRequest(channel, request).run {
            when {
                uri.host != settings.host || uri.port != settings.port -> withRequestLine(startLine.withHost("${settings.host}:${settings.port}"))
                else -> this
            }
        }

        if (request !== preparedRequest) message.clear().writeRequest(preparedRequest).resetReaderIndex()

        LOGGER.debug { "Sending request in session '${channel.sessionAlias}':\n${message.toString(UTF_8)}" }
        requests.offer(RequestInfo(preparedRequest, metadata))
    }

    override fun send(message: RawMessage): CompletableFuture<MessageID> {
        if (!channel.isOpen) channel.open().get(CONNECT_TIMEOUT, MILLISECONDS)

        if (!manager.isReady) {
            LOGGER.info { "Waiting for session to become ready: ${channel.sessionAlias}" }
            var timeout = READINESS_TIMEOUT
            while (!manager.isReady && --timeout != 0L) Thread.sleep(1)
            if (!manager.isReady) return failedFuture(IllegalStateException("Session did not become ready in $READINESS_TIMEOUT ms"))
            LOGGER.info { "Session is ready: ${channel.sessionAlias}" }
        }

        return channel.send(message.body.toByteBuf(), message.metadata.propertiesMap, message.eventId, HANDLE_AND_MANGLE).exceptionally {
            manager.onError(channel, it)
            throw it
        }
    }

    override fun onClose(channel: IChannel) {
        if (requests.isNotEmpty()) LOGGER.warn { "Channel closed with ${requests.size} requests pending responses in session: ${channel.sessionAlias}" }
        requests.clear()
        manager.onClose(channel)
    }

    override fun close() = manager.close()

    private data class RequestInfo(val request: RawHttpRequest, val metadata: Map<String, String>)

    private class ResponseByteBuf(val response: RawHttpResponse<*>, buffer: ByteBuf) : DuplicatedByteBuf(buffer) {
        override fun asReadOnly() = ResponseByteBuf(response, super.asReadOnly())
    }

    companion object {
        private val LOGGER = KotlinLogging.logger {}
        private val BODY_SEPARATOR = "\r\n\r\n".toByteArray()

        private const val CONNECT_TIMEOUT = 5000L
        private const val READINESS_TIMEOUT = 5000L

        private const val METHOD_PROPERTY = "method"
        private const val URI_PROPERTY = "uri"

        val ByteBuf.hexDump: String
            get() = ByteBufUtil.hexDump(this)

        private fun ByteBuf.readRequest(): RawHttpRequest = ByteBufInputStream(this).use(RawHttpParser::parseRequest)
        private fun ByteBuf.readResponse(): RawHttpResponse<*> = ByteBufInputStream(this).use(RawHttpParser::parseResponse)
        fun ByteBuf.writeRequest(request: RawHttpRequest): ByteBuf = apply { ByteBufOutputStream(this).use(request::writeTo) }
    }
}