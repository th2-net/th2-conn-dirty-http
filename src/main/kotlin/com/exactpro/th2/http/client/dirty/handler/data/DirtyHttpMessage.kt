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

typealias NettyHttpMethod = io.netty.handler.codec.http.HttpMethod
typealias NettyHttpVersion = io.netty.handler.codec.http.HttpVersion

abstract class DirtyHttpMessage(val httpVersion: HTTPVersionFragment, val headers: HeaderFragments, val httpBody: TextFragment, val decoderResult: DecoderResult = DecoderResult.SUCCESS) {
    abstract class HttpBuilder {
        abstract fun build(reference: ByteBuf): DirtyHttpMessage
        abstract fun setDecodeResult(result: DecoderResult)
    }
}