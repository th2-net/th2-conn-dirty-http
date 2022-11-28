package com.exactpro.th2.http.client.dirty.handler.codec

import com.exactpro.th2.http.client.dirty.handler.data.DirtyHttpRequest
import com.exactpro.th2.http.client.dirty.handler.data.pointers.HTTPVersionFragment
import com.exactpro.th2.http.client.dirty.handler.data.pointers.HeaderFragments
import com.exactpro.th2.http.client.dirty.handler.data.pointers.MethodFragment
import com.exactpro.th2.http.client.dirty.handler.data.pointers.TextFragment
import com.exactpro.th2.http.client.dirty.handler.parsers.HeaderParser
import com.exactpro.th2.http.client.dirty.handler.parsers.StartLineParser
import com.exactpro.th2.http.client.dirty.handler.skipReaderIndex
import io.netty.buffer.ByteBuf
import io.netty.handler.codec.DecoderResult
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import mu.KotlinLogging

/**
 * This codec required to use with full single message only
 * If used as multiple decode processor, all messages after first one will fall into body data
 * There no check of body length due current core realization
 * Core always processes only one request message for each RawMessage
 */
class DirtyRequestDecoder: DirtyHttpDecoder<DirtyHttpRequest>() {

    private val startLineParser: StartLineParser = StartLineParser()
    private val headerParser: HeaderParser = HeaderParser()

    private var currentMessageBuilder: DirtyHttpRequest.Builder = DirtyHttpRequest.Builder()

    override fun buildCurrentMessage(startPos: Int, endPos: Int, originalBuf: ByteBuf): DirtyHttpRequest {
        return currentMessageBuilder.build(originalBuf)
    }

    override fun parseStartLine(position: Int, buffer: ByteBuf): Boolean {
        if (!startLineParser.parseLine(buffer)) return false
        val startLine = startLineParser.lineParts
        if (startLine.size < 3) {
            startLineParser.reset()
            return false
        }

        currentMessageBuilder.apply {
            setMethod(startLine[0].let { MethodFragment(it.second, it.first.length, buffer) })
            setUrl(startLine[1].let { TextFragment(it.second, it.first.length, buffer) }.also {
                it.previous = method
            })
            setVersion(startLine[2].let { HTTPVersionFragment(it.second, it.first.length, buffer) }.also {
                it.previous = url
            })
        }
        return true
    }

    override fun parseHeaders(position: Int, buffer: ByteBuf): Boolean {
        if (!headerParser.parseHeaders(buffer, )) return false
        val headers = headerParser.getHeaders()
        currentMessageBuilder.setHeaders(HeaderFragments(headers))
        return true
    }

    override fun parseBody(position: Int, buffer: ByteBuf): Boolean {
        currentMessageBuilder.setBody(TextFragment(position, buffer.writerIndex() - position, buffer))
        buffer.skipReaderIndex()
        return true
    }

    override fun onDecodeFailure(buffer: ByteBuf, e: Exception) {
        currentMessageBuilder.setDecodeResult(DecoderResult.failure(e))
        LOGGER.error(e) { "Error during response message parsing" }
    }

    override fun reset() {
        startLineParser.reset()
        headerParser.reset()
        currentMessageBuilder = DirtyHttpRequest.Builder()
    }

    companion object {
        private val LOGGER = KotlinLogging.logger { this::class.java.simpleName }
    }
}