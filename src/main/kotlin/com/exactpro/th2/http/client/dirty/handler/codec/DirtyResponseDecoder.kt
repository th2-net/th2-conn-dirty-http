package com.exactpro.th2.http.client.dirty.handler.codec

import com.exactpro.th2.http.client.dirty.handler.data.DirtyHttpResponse
import com.exactpro.th2.http.client.dirty.handler.data.pointers.HeadersPointer
import com.exactpro.th2.http.client.dirty.handler.data.pointers.IntPointer
import com.exactpro.th2.http.client.dirty.handler.data.pointers.StringPointer
import com.exactpro.th2.http.client.dirty.handler.data.pointers.VersionPointer
import com.exactpro.th2.http.client.dirty.handler.parsers.HeaderParser
import com.exactpro.th2.http.client.dirty.handler.parsers.StartLineParser
import com.exactpro.th2.netty.bytebuf.util.indexOf
import io.netty.buffer.ByteBuf
import io.netty.handler.codec.DecoderResult
import io.netty.handler.codec.http.HttpVersion
import mu.KotlinLogging

class DirtyResponseDecoder: DirtyHttpDecoder<DirtyHttpResponse>() {

    private val lineParser = StartLineParser()
    private val headerParser = HeaderParser()

    private var currentMessageBuilder: DirtyHttpResponse.Builder = DirtyHttpResponse.Builder()

    private var headMode: Boolean = false

    override fun decode(input: ByteBuf): DirtyHttpResponse? {
        headMode = false
        return super.decode(input)
    }

    fun decodeHead(input: ByteBuf): DirtyHttpResponse? {
        headMode = true
        return super.decode(input)
    }

    override fun buildCurrentMessage(startPos: Int, endPos: Int, originalBuf: ByteBuf): DirtyHttpResponse {
        return currentMessageBuilder.build(originalBuf.copy(startPos, endPos-startPos))
    }

    override fun parseStartLine(position: Int, buffer: ByteBuf): Boolean {
        if (!lineParser.parseLine(buffer)) return false

        val parts = lineParser.lineParts
        if (parts.size < 3) {
            lineParser.reset()
            return false
        }
        currentMessageBuilder.apply {
            setVersion(parts[0].let { VersionPointer(it.second, HttpVersion.valueOf(it.first)) })
            setCode(parts[1].let { IntPointer(it.second, it.first.toInt()) })
            setReason( parts[2].let { StringPointer(it.second, it.first) })
        }
        return true
    }

    override fun parseHeaders(position: Int, buffer: ByteBuf): Boolean {
        if (!headerParser.parseHeaders(buffer)) {
            headerParser.reset()
            return false
        }
        val headers = headerParser.getHeaders()
        // FIXME: Need to change buffer param to something else, this buffer will be discarded due decode process
        currentMessageBuilder.setHeaders(HeadersPointer(position, buffer.readerIndex() - position, buffer, headers))
        return true
    }

    override fun parseBody(position: Int, buffer: ByteBuf): Boolean {
        var endOfTheBody = position

        if (!headMode) {
            val headers = checkNotNull(currentMessageBuilder.headers)
            when {
                headers.contains("Content-Length") -> headers["Content-Length"]!!.toInt().let { contentLengthInt ->
                    if (contentLengthInt != 0 ) {
                        if (buffer.writerIndex() < position + contentLengthInt) return false
                        endOfTheBody = position + contentLengthInt
                    }
                }
                headers["Transfer-Encoding"]?.contains("chunked") == true -> {
                    val indexOfEndPattern = buffer.indexOf("0\r\n\r\n")
                    when {
                        indexOfEndPattern < 0 -> return false
                        else -> endOfTheBody = indexOfEndPattern+5
                    }
                }
            }
        }

        currentMessageBuilder.apply {
            setBodyPosition(position)
            setBodyLength(endOfTheBody-position)
        }
        buffer.readerIndex(endOfTheBody)
        return true
    }

    override fun onDecodeFailure(buffer: ByteBuf, e: Exception) {
        currentMessageBuilder.setDecodeResult(DecoderResult.failure(e))
        LOGGER.error(e) { "Error during response message parsing" }
    }

    override fun reset() {
        lineParser.reset()
        headerParser.reset()
        currentMessageBuilder = DirtyHttpResponse.Builder()
    }

    companion object {
        private val LOGGER = KotlinLogging.logger { this::class.java.simpleName }
    }
}