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


package io.netty.handler.codec

import com.exactpro.th2.http.client.dirty.handler.data.DirtyHttpRequest
import com.exactpro.th2.http.client.dirty.handler.data.pointers.HeadersPointer
import com.exactpro.th2.http.client.dirty.handler.data.pointers.MethodPointer
import com.exactpro.th2.http.client.dirty.handler.data.pointers.StringPointer
import com.exactpro.th2.http.client.dirty.handler.data.pointers.VersionPointer
import com.exactpro.th2.http.client.dirty.handler.parsers.HeaderParser
import com.exactpro.th2.http.client.dirty.handler.parsers.StartLineParser
import com.exactpro.th2.http.client.dirty.handler.skipReaderIndex
import io.netty.buffer.ByteBuf
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
            setMethod(startLine[0].let { MethodPointer(it.second, HttpMethod.valueOf(it.first)) })
            setUrl(startLine[1].let { StringPointer(it.second, it.first) })
            setVersion(startLine[2].let { VersionPointer(it.second, HttpVersion.valueOf(it.first)) })
        }
        return true
    }

    override fun parseHeaders(position: Int, buffer: ByteBuf): Boolean {
        if (!headerParser.parseHeaders(buffer)) return false
        val headers = headerParser.getHeaders()
        currentMessageBuilder.setHeaders(HeadersPointer(position, buffer.readerIndex() - position, buffer, headers))
        return true
    }

    override fun parseBody(position: Int, buffer: ByteBuf): Boolean {
        currentMessageBuilder.setBodyLength(buffer.writerIndex() - position)
        currentMessageBuilder.setBodyPosition(position)
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