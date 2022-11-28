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
import com.exactpro.th2.http.client.dirty.handler.data.pointers.TextFragment
import io.netty.buffer.ByteBuf
import io.netty.handler.codec.DecoderResult

class DirtyHttpResponse(httpVersion: HTTPVersionFragment, val httpCode: TextFragment, val httpReason: TextFragment, headers: HeaderFragments, httpBody: TextFragment, decoderResult: DecoderResult = DecoderResult.SUCCESS): DirtyHttpMessage(httpVersion, headers, httpBody, decoderResult) {

    override fun toString(): String = buildString {
        appendLine("$httpVersion $httpCode $httpReason")
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
        var code: TextFragment? = null
            private set
        var reason: TextFragment? = null
            private set
        var headers: HeaderFragments? = null
            private set
        var body: TextFragment? = null
            private set


        override fun setDecodeResult(result: DecoderResult) {
            this.decodeResult = result
        }

        fun setVersion(version: HTTPVersionFragment) {
            this.version = version
        }

        fun setCode(code: TextFragment) {
            this.code = code
        }

        fun setReason(reason: TextFragment) {
            this.reason = reason
        }

        fun setHeaders(headers: HeaderFragments) {
            this.headers = headers
        }

        fun setBody(body: TextFragment) {
            this.body = body
        }

        private fun createError(reference: ByteBuf, decoderResult: DecoderResult) = DirtyHttpResponse(HTTPVersionFragment(0, 0, reference), TextFragment(0,0, reference), TextFragment(0,0, reference), HeaderFragments(mutableMapOf()), TextFragment(0, 0, reference), decoderResult)

        override fun build(reference: ByteBuf): DirtyHttpResponse = if (decodeResult.isSuccess) {
            DirtyHttpResponse(
                checkNotNull(version) {"Version is required"},
                checkNotNull(code) {"Code is required"},
                checkNotNull(reason) {"Reason is required"},
                checkNotNull(headers) {"Header is required"},
                checkNotNull(body) {"Body is required"},
                decodeResult
            )
        } else {
            createError(reference, decodeResult)
        }
    }

}

