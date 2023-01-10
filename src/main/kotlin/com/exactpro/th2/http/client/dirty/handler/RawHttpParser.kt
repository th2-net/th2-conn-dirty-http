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

import rawhttp.core.HttpMetadataParser
import rawhttp.core.RawHttp
import rawhttp.core.RawHttpHeaders
import rawhttp.core.RawHttpOptions
import rawhttp.core.RawHttpRequest
import rawhttp.core.RawHttpResponse
import rawhttp.core.StartLine
import rawhttp.core.StatusLine
import rawhttp.core.body.BodyDecoder
import rawhttp.core.body.BodyReader
import rawhttp.core.body.EagerBodyReader
import rawhttp.core.body.FramedBody
import rawhttp.core.body.FramedBody.Chunked
import rawhttp.core.body.FramedBody.CloseTerminated
import rawhttp.core.body.FramedBody.ContentLength
import rawhttp.core.errors.InvalidHttpRequest
import rawhttp.core.errors.InvalidHttpResponse
import rawhttp.core.errors.InvalidMessageFrame
import java.io.InputStream

/**
 * Fork of RawHttp which allows to parse requests without 'Host' header
 */
object RawHttpParser {
    private val options = RawHttpOptions.defaultInstance()
    private val metadataParser = HttpMetadataParser(options)

    fun parseRequest(inputStream: InputStream): RawHttpRequest {
        val requestLine = metadataParser.parseRequestLine(inputStream)
        val originalHeaders = metadataParser.parseHeaders(inputStream) { message, lineNumber -> InvalidHttpRequest(message, lineNumber + 1) }
        val modifiableHeaders = RawHttpHeaders.newBuilder(originalHeaders)
        val headers = modifiableHeaders.build()
        val bodyReader = if (RawHttp.requestHasBody(headers)) createBodyReader(inputStream, requestLine, headers) else null
        return RawHttpRequest(requestLine, headers, bodyReader, null)
    }

    fun parseResponse(inputStream: InputStream): RawHttpResponse<*> {
        val statusLine = metadataParser.parseStatusLine(inputStream)
        val headers = metadataParser.parseHeaders(inputStream) { message, lineNumber -> InvalidHttpResponse(message, lineNumber + 1) }
        val bodyReader = if (RawHttp.responseHasBody(statusLine, null)) createBodyReader(inputStream, statusLine, headers) else null
        return RawHttpResponse(null, null, statusLine, headers, bodyReader)
    }

    private fun createBodyReader(inputStream: InputStream, startLine: StartLine, headers: RawHttpHeaders): BodyReader {
        return EagerBodyReader(getFramedBody(startLine, headers), inputStream)
    }

    private fun getFramedBody(startLine: StartLine, headers: RawHttpHeaders): FramedBody {
        val transferEncodings = headers["Transfer-Encoding", ",\\s*"]
        val contentEncodings = headers["Content-Encoding", ",\\s*"]
        val bodyDecoder = BodyDecoder(options.encodingRegistry, contentEncodings + transferEncodings)
        val isChunked = transferEncodings.lastOrNull().equals("chunked", ignoreCase = true)

        if (isChunked) return Chunked(bodyDecoder, metadataParser)

        val lengthValues = headers["Content-Length"]

        if (lengthValues.isEmpty()) {
            if (startLine is StatusLine) return CloseTerminated(bodyDecoder)
            throw InvalidMessageFrame("The length of the request body cannot be determined. The Content-Length header is missing and the Transfer-Encoding header does not indicate the message is chunked")
        }

        if (lengthValues.size > 1) throw InvalidMessageFrame("More than one Content-Length header value is present")

        val bodyLength = try {
            lengthValues[0].toLong()
        } catch (e: NumberFormatException) {
            throw InvalidMessageFrame("Content-Length header value is not a valid number")
        }

        return ContentLength(bodyDecoder, bodyLength)
    }
}