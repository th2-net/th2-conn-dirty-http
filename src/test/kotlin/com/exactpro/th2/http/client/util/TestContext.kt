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

package com.exactpro.th2.http.client.util

import com.exactpro.th2.common.event.Event
import com.exactpro.th2.common.schema.dictionary.DictionaryType
import com.exactpro.th2.conn.dirty.tcp.core.api.IChannel
import com.exactpro.th2.conn.dirty.tcp.core.api.IHandlerContext
import com.exactpro.th2.conn.dirty.tcp.core.api.IHandlerSettings
import java.io.InputStream
import java.net.InetSocketAddress

class TestContext(override val settings: IHandlerSettings): IHandlerContext {
    override fun createChannel(address: InetSocketAddress, security: IChannel.Security, attributes: Map<String, Any>, autoReconnect: Boolean, reconnectDelay: Long, maxMessageRate: Int, vararg sessionSuffixes: String): IChannel {
        error("Not yet implemented")
    }

    override fun destroyChannel(channel: IChannel) = Unit

    override fun get(dictionary: DictionaryType): InputStream {
        error("Not yet implemented")
    }

    override fun send(event: Event) {
        error("Not yet implemented")
    }

}