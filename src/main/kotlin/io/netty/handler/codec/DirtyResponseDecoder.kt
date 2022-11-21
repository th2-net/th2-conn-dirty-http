package io.netty.handler.codec

import com.exactpro.th2.http.client.dirty.handler.data.DirtyHttpResponse
import com.exactpro.th2.http.client.dirty.handler.data.pointers.HeadersPointer
import com.exactpro.th2.http.client.dirty.handler.data.pointers.IntPointer
import com.exactpro.th2.http.client.dirty.handler.data.pointers.StringPointer
import com.exactpro.th2.http.client.dirty.handler.data.pointers.VersionPointer
import com.exactpro.th2.http.client.dirty.handler.parsers.HeaderParser
import com.exactpro.th2.http.client.dirty.handler.parsers.StartLineParser
import com.exactpro.th2.netty.bytebuf.util.indexOf
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpVersion
import mu.KotlinLogging

class DirtyResponseDecoder: ByteToMessageDecoder() {

    private val lineParser = StartLineParser()
    private val headerParser = HeaderParser()

    private var currentState = State.READ_INITIAL
    private var currentPosition = 0

    private var currentMessageBuilder: DirtyHttpResponse.Builder = DirtyHttpResponse.Builder()

    init {
        isSingleDecode = true
        //FIXME: need to understand how to work with default MERGE_CUMULATOR
        setCumulator { alloc, cumulation, incoming ->
            if (!cumulation.isWritable) {
                cumulation.release()
                alloc.buffer().writeBytes(incoming)
            } else {
                cumulation.writeBytes(incoming)
            }.also {
                incoming.readerIndex(incoming.writerIndex())
            }
        }
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is ByteBuf) {
            val out = CodecOutputList.newInstance()
            try {
                callDecode(ctx, msg, out)
            } catch (e: DecoderException) {
                throw e
            } catch (e: Exception) {
                throw DecoderException(e)
            } finally {
                try {
                    fireChannelRead(ctx, out, out.size)
                } finally {
                    out.recycle()
                }
            }
        } else {
            ctx.fireChannelRead(msg)
        }
    }

    override fun decode(ctx: ChannelHandlerContext, `in`: ByteBuf, out: MutableList<Any>) {
        if (decodeSingle(`in`)) {
            out.add(currentMessageBuilder.build(`in`.copy(0, `in`.readerIndex())))
            `in`.discardReadBytes()
            reset()
        } else {
            `in`.readerIndex(0)
        }
    }

    /**
     * @return true if decode was completed
     */
    private fun decodeSingle(buffer: ByteBuf): Boolean {
        if (!buffer.isReadable) return false
        try {
            when(currentState) {
                State.SKIP_CONTROL_CHARS -> {
                    currentState = State.READ_INITIAL
                    return decodeSingle(buffer)
                }
                State.READ_INITIAL -> {
                    buffer.readerIndex(currentPosition)
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

                    currentState = State.READ_HEADER
                    currentPosition = buffer.readerIndex()

                    return decodeSingle(buffer)
                }
                State.READ_HEADER -> {
                    buffer.readerIndex(currentPosition)

                    if (!headerParser.parseHeaders(buffer)) return false
                    val headers = headerParser.getHeaders()
                    headerParser.reset()
                    val endOfHeaders = buffer.readerIndex()

                    currentMessageBuilder.setHeaders(HeadersPointer(currentPosition, endOfHeaders-currentPosition, buffer, headers))
                    currentState = State.READ_FIXED_LENGTH_CONTENT
                    currentPosition = buffer.readerIndex()

                    return decodeSingle(buffer)
                }
                State.READ_FIXED_LENGTH_CONTENT -> checkNotNull(currentMessageBuilder.headers).let { headers ->
                    buffer.readerIndex(currentPosition)

                    // Reader index of buffer right now on free line position right before body
                    val startOfTheBody = currentPosition
                    var endOfTheBody = startOfTheBody

                    when {
                        headers.contains("Content-Length") -> headers["Content-Length"]!!.toInt().let { contentLengthInt ->
                            if (contentLengthInt != 0 ) {
                                if (buffer.writerIndex() < startOfTheBody + contentLengthInt) return false
                                endOfTheBody = startOfTheBody + contentLengthInt
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
                    buffer.readerIndex(endOfTheBody)

                    currentPosition = endOfTheBody
                    currentMessageBuilder.apply {
                        setBodyPosition(startOfTheBody)
                        setBodyLength(endOfTheBody-startOfTheBody)
                    }
                    currentState = State.SKIP_CONTROL_CHARS
                    return true
                }
                else -> {
                    throw java.lang.IllegalStateException("Unexpected state of decode: $currentState")
                }
            }
        } catch (e: Exception) {
            LOGGER.error(e) { "Error during response message parsing" }
            currentMessageBuilder.setDecodeResult(DecoderResult.failure(e))
            buffer.readerIndex(buffer.writerIndex())
            return true
        }
    }

    private fun reset() {
        lineParser.reset()
        headerParser.reset()
        currentPosition = 0
        currentMessageBuilder = DirtyHttpResponse.Builder()
        currentState = State.SKIP_CONTROL_CHARS
    }

    private enum class State {
        SKIP_CONTROL_CHARS, READ_INITIAL, READ_HEADER, READ_VARIABLE_LENGTH_CONTENT, READ_FIXED_LENGTH_CONTENT, READ_CHUNK_SIZE, READ_CHUNKED_CONTENT, READ_CHUNK_DELIMITER, READ_CHUNK_FOOTER, BAD_MESSAGE
    }

    companion object {
        private val LOGGER = KotlinLogging.logger { this::class.java.simpleName }
    }
}

// TODO: Use it instead of previous decoder but need to fix decode process, there delay between call and actual decode somehow
class NextDirtyResponseDecoder: DirtyHttpDecoder<DirtyHttpResponse>() {

    private val lineParser = StartLineParser()
    private val headerParser = HeaderParser()

    private var currentMessageBuilder: DirtyHttpResponse.Builder = DirtyHttpResponse.Builder()

    override fun buildCurrentMessage(reference: ByteBuf) = currentMessageBuilder.build(reference)

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
        currentMessageBuilder.setHeaders(HeadersPointer(position, buffer.readerIndex()-position, buffer, headers))
        return true
    }

    override fun parseBody(position: Int, buffer: ByteBuf): Boolean {
        val headers = checkNotNull(currentMessageBuilder.headers)
        var endOfTheBody = position

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
        super.reset()
        lineParser.reset()
        headerParser.reset()
        currentMessageBuilder = DirtyHttpResponse.Builder()
    }

    companion object {
        private val LOGGER = KotlinLogging.logger { this::class.java.simpleName }
    }
}