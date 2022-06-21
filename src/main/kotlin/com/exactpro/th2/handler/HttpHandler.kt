package com.exactpro.th2.handler

import com.exactpro.th2.conn.dirty.tcp.core.api.IContext
import com.exactpro.th2.conn.dirty.tcp.core.api.IProtocolHandler
import com.exactpro.th2.conn.dirty.tcp.core.api.IProtocolHandlerSettings
import io.netty.buffer.ByteBuf
import com.exactpro.th2.netty.HttpFramingHelper
import com.exactpro.th2.util.CONTENT_TYPE_PROPERTY
import com.exactpro.th2.util.HTTP_AUTH_HEADER
import com.exactpro.th2.util.HTTP_CONNECTION_HEADER
import com.exactpro.th2.util.HTTP_CONNECTION_KEEP_ALIVE
import com.exactpro.th2.util.HTTP_CONTENT_TYPE_HEADER
import com.exactpro.th2.util.METHOD_PROPERTY
import com.exactpro.th2.util.STATUS_PROPERTY
import com.exactpro.th2.util.URI_PROPERTY
import com.exactpro.th2.util.applyHeaders
import com.exactpro.th2.util.getHeaderValue
import com.exactpro.th2.util.getRequestLineParts
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpResponse
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64


open class HttpHandler(private val context: IContext<IProtocolHandlerSettings>)
    : IProtocolHandler, AutoCloseable {

    protected val framingHelper: HttpFramingHelper = HttpFramingHelper()
    private val framingOutput: MutableList<Any> = mutableListOf()

    private val settings: HttpHandlerSettings = context.settings as HttpHandlerSettings
    private val defaultHeaders = run {
        val newMap = settings.defaultHeaders.toMutableMap()
        val credentials = "${settings.username}:${settings.password}".toByteArray(UTF_8)

        newMap[HTTP_AUTH_HEADER] = newMap[HTTP_AUTH_HEADER]
            ?: listOf("Basic ${Base64.getEncoder().encodeToString(credentials)}")

        newMap[HTTP_CONNECTION_HEADER] = newMap[HTTP_CONNECTION_HEADER]
            ?.run { listOf(HTTP_CONNECTION_KEEP_ALIVE).union(this).toList() }
            ?: listOf(HTTP_CONNECTION_KEEP_ALIVE)

        newMap.toMap()
    }

    override fun onReceive(buffer: ByteBuf): ByteBuf? {
        val startIdx = framingHelper.decode0(buffer, framingOutput)
        if(startIdx >= 0) {
            framingOutput.removeAll { it is HttpContent }
            return buffer.retainedSlice(startIdx, buffer.readerIndex() - startIdx)
        }
        // Http response can have no content-length header in case connection closed by server.
        // In this case LastHttpContent will not be produced by framing helper as it can't detect closed connection.
        if(!context.channel.isOpen && framingOutput.isNotEmpty()) {
            return buffer.retainedSlice(buffer.readerIndex(), buffer.readableBytes())
        }
        return null
    }

    override fun onIncoming(message: ByteBuf): Map<String, String> {
        val metadata: MutableMap<String, String> = mutableMapOf()
        val response = framingOutput.removeFirst() as HttpResponse
        metadata[CONTENT_TYPE_PROPERTY] = response.headers()[HttpHeaderNames.CONTENT_TYPE]
        metadata[STATUS_PROPERTY] = response.status().code().toString()
        return metadata
    }

    override fun onOutgoing(message: ByteBuf, metadata: MutableMap<String, String>): Unit {
        val (method, uri, _) = message.getRequestLineParts()
        metadata[METHOD_PROPERTY] = method
        metadata[URI_PROPERTY] = uri
        message.getHeaderValue(HTTP_CONTENT_TYPE_HEADER)?.let {
            metadata[CONTENT_TYPE_PROPERTY] = it
        }
        message.applyHeaders(this.defaultHeaders)
    }
}