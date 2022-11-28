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


package com.exactpro.th2.http.client.dirty.handler.data.pointers

import io.netty.buffer.ByteBuf


class HeaderFragments(val headers: MutableMap<String, HeaderLine>) {

    class HeaderLine(offset: Int, length: Int, buffer: ByteBuf) : Fragment<String>(offset, length, buffer) {
        override var data: String?
            get() = TODO("Not yet implemented")
            set(value) {}
    }

    val keys: MutableSet<String>
        get() = headers.keys

    val size: Int
        get() = headers.size

    fun containsKey(key: String): Boolean = headers.containsKey(key)

    operator fun get(key: String): String? = if (containsKey(key)) headers[key]!!.text.split(":")[1] else null

    fun isEmpty(): Boolean = headers.isEmpty()


    fun clear() {
        TODO("Not yet implemented")
    }

    fun put(key: String, value: String): String? {
        TODO("Not yet implemented")
    }

    fun putAll(from: Map<out String, String>) {
        TODO("Not yet implemented")
    }

    fun remove(key: String): String? {
        TODO("Not yet implemented")
    }
}