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

import com.exactpro.th2.http.client.dirty.handler.data.pointers.HTTPVersionFragment
import com.exactpro.th2.http.client.dirty.handler.data.pointers.HeaderFragments
import com.exactpro.th2.http.client.dirty.handler.data.pointers.MethodFragment
import com.exactpro.th2.http.client.dirty.handler.data.pointers.TextFragment
import io.netty.buffer.ByteBuf
import io.netty.handler.codec.DecoderResult
import java.nio.charset.Charset

class DirtyHttpRequest(val httpMethod: MethodFragment, val httpUrl: TextFragment, httpVersion: HTTPVersionFragment, headers: HeaderFragments, httpBody: TextFragment, decoderResult: DecoderResult = DecoderResult.SUCCESS): DirtyHttpMessage(httpVersion, headers, httpBody, decoderResult) {

    override fun toString(): String = buildString {
        appendLine("$httpMethod $httpUrl $httpVersion")
        headers.forEach {
            appendLine("${it.key}: ${it.value}")
        }
        appendLine()
        appendLine(httpBody)
    }

    class Builder: HttpBuilder() {
        var decodeResult: DecoderResult = DecoderResult.SUCCESS
            private set
        var version: HTTPVersionFragment? = null
            private set
        var method: MethodFragment? = null
            private set
        var url: TextFragment? = null
            private set
        var headers: HeaderFragments? = null
            private set
        var body: TextFragment? = null
            private set

        override fun setDecodeResult(result: DecoderResult) {
            this.decodeResult = result
        }

        fun setMethod(method: MethodFragment) {
            this.method = method
        }

        fun setUrl(url: TextFragment) {
            this.url = url
        }

        fun setVersion(version: HTTPVersionFragment) {
            this.version = version
        }

        fun setHeaders(headers: HeaderFragments) {
            this.headers = headers
        }

        fun setBody(body: TextFragment) {
            this.body = body
        }

        private fun createError(reference: ByteBuf, decoderResult: DecoderResult) = DirtyHttpRequest(MethodFragment(0, 0, reference), TextFragment(0, 0, reference), HTTPVersionFragment(0, 0, reference), HeaderFragments(mutableMapOf()), TextFragment(0, 0, reference), decoderResult)

        override fun build(reference: ByteBuf): DirtyHttpRequest = if (decodeResult.isSuccess) {
            DirtyHttpRequest(
                checkNotNull(method) {"Reason is required"},
                checkNotNull(url) {"Url is required"},
                checkNotNull(version) {"Version is required"},
                checkNotNull(headers) {"Header is required"},
                checkNotNull(body) {"Body is required"},
            )
        } else {
            createError(reference, decodeResult)
        }

    }

}