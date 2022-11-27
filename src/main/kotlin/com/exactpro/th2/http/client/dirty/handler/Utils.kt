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

import com.exactpro.th2.http.client.dirty.handler.data.pointers.HeadersPointer
import io.netty.buffer.ByteBuf
import java.util.ServiceLoader

inline fun <reified T> load(defaultImpl: Class<out T>): T {
    val instances = ServiceLoader.load(T::class.java).toList()

    return when (instances.size) {
        0 -> error("No instances of ${T::class.simpleName}")
        1 -> instances.first()
        2 -> instances.first { !defaultImpl.isInstance(it) }
        else -> error("More than 1 non-default instance of ${T::class.simpleName} has been found: $instances")
    }
}

fun ByteBuf.forEachByteIndexed(byteProcessor: (index: Int, byte: Byte) -> Boolean): Int {
    var index = 0
    return this.forEachByte {
        byteProcessor(index++, it)
    }
}

fun HeadersPointer.HttpHeaderDetails.move(step: Int) {
    this.start += step
    this.end += step
}

fun ByteBuf.resetMarkReaderIndex(): ByteBuf {
    val lastIndex = readerIndex()
    readerIndex(0)
    markReaderIndex()
    return readerIndex(lastIndex)
}

fun ByteBuf.skipReaderIndex(): ByteBuf = readerIndex(writerIndex())