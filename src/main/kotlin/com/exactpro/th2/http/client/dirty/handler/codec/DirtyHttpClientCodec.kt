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

package com.exactpro.th2.http.client.dirty.handler.codec

import com.exactpro.th2.http.client.dirty.handler.data.DirtyHttpRequest
import com.exactpro.th2.http.client.dirty.handler.data.DirtyHttpResponse
import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpMethod
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * @see HttpClientCodec
 */
class DirtyHttpClientCodec {
    private val requestDecoder = DirtyRequestDecoder()
    private val responseDecoder = DirtyResponseDecoder()

    private val methods : Queue<HttpMethod> = ConcurrentLinkedQueue()

    fun onRequest(msg: ByteBuf): DirtyHttpRequest? = requestDecoder.decode(msg)?.also {
        methods.offer(it.method)
    }

    fun onResponse(msg: ByteBuf): DirtyHttpResponse? = when(methods.poll()) {
        HttpMethod.HEAD -> responseDecoder.decodeHead(msg)
        else -> responseDecoder.decode(msg)
    }

    fun reset() {
        methods.clear()
    }
}
