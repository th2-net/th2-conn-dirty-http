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


package com.exactpro.th2.http.client.dirty.handler.data

import com.exactpro.th2.http.client.dirty.handler.data.pointers.BodyPointer
import com.exactpro.th2.http.client.dirty.handler.data.pointers.HeadersPointer
import com.exactpro.th2.http.client.dirty.handler.data.pointers.MethodPointer
import com.exactpro.th2.http.client.dirty.handler.data.pointers.StringPointer
import com.exactpro.th2.http.client.dirty.handler.data.pointers.VersionPointer
import com.exactpro.th2.http.client.dirty.handler.skipReaderIndex
import com.exactpro.th2.netty.bytebuf.util.replace
import io.netty.buffer.ByteBuf
import io.netty.handler.codec.DecoderResult
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion
import java.nio.charset.Charset

class DirtyHttpRequest(private val httpMethod: MethodPointer, private val httpUrl: StringPointer, httpVersion: VersionPointer, headers: HeadersPointer, httpBody: BodyPointer, reference: ByteBuf, decoderResult: DecoderResult = DecoderResult.SUCCESS): DirtyHttpMessage(httpVersion, headers, httpBody, reference, decoderResult) {

    var method: NettyHttpMethod
        get() = httpMethod.value
        set(value) = this.httpMethod.let {
            reference.replace(it.position, reference.writerIndex(), value.name())
            it.value = value
            settle()
        }

    var url: String
        get() = httpUrl.value
        set(value) = this.httpUrl.let {
            reference.readerIndex(it.position)
            reference.replace(it.position, it.position + it.value.length, value)
            reference.skipReaderIndex()
            it.value = value
            settle()
        }

    override fun settle(startSum: Int): Int {
        var sum = startSum
        if (httpMethod.isModified()) {
            sum += httpMethod.expansion
            httpMethod.settle()
        }
        if (httpUrl.isModified() || sum > 0) {
            sum = httpUrl.settleSingle(sum)
        }
        if (httpVersion.isModified() || sum > 0) {
            sum = httpVersion.settleSingle(sum)
        }

        return super.settle(sum)
    }

    override fun toString(): String = buildString {
        appendLine("=================")
        appendLine("${method.name()} $url ${version.text()}")
        headers.forEach {
            appendLine("${it.key}: ${it.value}")
        }
        appendLine()
        appendLine(body.toString(Charset.defaultCharset()))
        appendLine("================= FROM BUFFER:")
        appendLine(reference.readerIndex(0).toString(Charset.defaultCharset()))
        appendLine("=================")
    }

    class Builder: HttpBuilder() {
        var decodeResult: DecoderResult = DecoderResult.SUCCESS
            private set
        var version: VersionPointer? = null
            private set
        var method: MethodPointer? = null
            private set
        var url: StringPointer? = null
            private set
        var headers: HeadersPointer? = null
            private set
        var bodyPosition: Int? = null
            private set
        var bodyLength: Int? = null
            private set

        override fun setDecodeResult(result: DecoderResult) {
            this.decodeResult = result
        }

        fun setMethod(method: MethodPointer) {
            this.method = method
        }

        fun setUrl(url: StringPointer) {
            this.url = url
        }

        fun setVersion(version: VersionPointer) {
            this.version = version
        }

        fun setHeaders(headers: HeadersPointer) {
            this.headers = headers
        }

        fun setBodyLength(length: Int) {
            this.bodyLength = length
        }

        fun setBodyPosition(pos: Int) {
            this.bodyPosition = pos
        }

        private fun createError(reference: ByteBuf, decoderResult: DecoderResult) = DirtyHttpRequest(MethodPointer(0, HttpMethod.GET), StringPointer(0,"/"), VersionPointer(0, HttpVersion.HTTP_1_1), HeadersPointer(0, 0, reference, mutableMapOf()), BodyPointer(reference, 0, 0), reference, decoderResult)

        override fun build(reference: ByteBuf): DirtyHttpRequest = if (decodeResult.isSuccess) {
            checkNotNull(bodyPosition) {"Body is required"}
            checkNotNull(bodyLength) {"Body is required"}
            val bodyPointer = if (bodyLength == 0) BodyPointer.Empty(reference, bodyPosition!!) else BodyPointer(reference, bodyPosition!!, bodyLength!!)
            DirtyHttpRequest(
                checkNotNull(method) {"Reason is required"},
                checkNotNull(url) {"Url is required"},
                checkNotNull(version) {"Version is required"},
                checkNotNull(headers) {"Header is required"},
                bodyPointer,
                reference
            )
        } else {
            createError(reference, decodeResult)
        }

    }

}