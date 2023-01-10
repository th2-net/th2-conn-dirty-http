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

package com.exactpro.th2.http.client.dirty.handler.api

import com.exactpro.th2.conn.dirty.tcp.core.api.IChannel
import com.google.auto.service.AutoService
import rawhttp.core.RawHttpHeaders
import rawhttp.core.RawHttpRequest
import java.util.Base64
import kotlin.text.Charsets.UTF_8

class DefaultSessionManager(private val settings: DefaultSessionManagerSettings?) : ISessionManager {
    override val isReady: Boolean = true

    private val auth = settings?.auth?.run { "Basic ${Base64.getEncoder().encodeToString("${username}:${password}".toByteArray(UTF_8))}" }

    override fun onRequest(channel: IChannel, request: RawHttpRequest): RawHttpRequest {
        if (settings == null || auth == null && settings.headers.isEmpty()) {
            return request
        }

        val headers = request.headers

        if (auth == null && settings.headers.keys.all(headers::contains)) {
            return request
        }

        return request.withHeaders(RawHttpHeaders.newBuilder(headers).run {
            if (auth != null) with("Authorization", auth)

            settings.headers.forEach { (name, value) ->
                if (name !in headers) with(name, value)
            }

            build()
        })
    }
}

data class AuthSettings(val username: String, val password: String)

data class DefaultSessionManagerSettings(val auth: AuthSettings? = null, val headers: Map<String, String> = mapOf()) : ISessionManagerSettings

@AutoService(ISessionManagerFactory::class)
class DefaultSessionManagerFactory : ISessionManagerFactory {
    override val name: String = DefaultSessionManagerFactory::class.java.simpleName
    override val settings: Class<DefaultSessionManagerSettings> = DefaultSessionManagerSettings::class.java
    override fun create(settings: ISessionManagerSettings?): ISessionManager = DefaultSessionManager(settings as? DefaultSessionManagerSettings)
}