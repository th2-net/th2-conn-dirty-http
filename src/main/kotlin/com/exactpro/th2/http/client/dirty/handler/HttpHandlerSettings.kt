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

import com.exactpro.th2.conn.dirty.tcp.core.api.IChannel.Security
import com.exactpro.th2.conn.dirty.tcp.core.api.IHandlerSettings
import com.exactpro.th2.http.client.dirty.handler.api.DefaultSessionManagerFactory
import com.exactpro.th2.http.client.dirty.handler.api.ISessionManagerFactory
import com.exactpro.th2.http.client.dirty.handler.api.ISessionManagerSettings
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.google.auto.service.AutoService

@AutoService(IHandlerSettings::class)
class HttpHandlerSettings(
    val security: Security = Security(),
    val host: String,
    val port: Int = if (security.ssl) 443 else 80,
    @Deprecated("set requestQueueSize to 0 instead") val sync: Boolean = false,
    val requestQueueSize: Int = if (sync) 1 else 65536,
    @JsonDeserialize(using = SessionManagerDeserializer::class) val session: ISessionManagerSettings? = null,
) : IHandlerSettings {
    init {
        check(requestQueueSize > 0) { "${::requestQueueSize.name} must be positive" }
    }
}

class SessionManagerDeserializer<T : ISessionManagerSettings>() : JsonDeserializer<T>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): T {
        return p.readValueAs(load<ISessionManagerFactory>(DefaultSessionManagerFactory::class.java).settings) as T
    }
}
