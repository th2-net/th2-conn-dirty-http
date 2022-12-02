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
import rawhttp.core.RawHttpRequest
import rawhttp.core.RawHttpResponse

interface ISessionManager : AutoCloseable {
    val isReady: Boolean

    fun onOpen(channel: IChannel) = Unit
    fun onRequest(channel: IChannel, request: RawHttpRequest): RawHttpRequest = request
    fun onResponse(channel: IChannel, response: RawHttpResponse<*>, request: RawHttpRequest) = Unit
    fun onError(channel: IChannel, cause: Throwable) = Unit
    fun onClose(channel: IChannel) = Unit

    override fun close() = Unit
}

interface ISessionManagerSettings

interface ISessionManagerFactory {
    /**
     * Returns factory name
     */
    val name: String

    /**
     * Returns settings class of entities produced by this factory
     */
    val settings: Class<out ISessionManagerSettings>

    /**
     * Creates an entity with provided [settings]
     *
     * @param settings entity settings
     * @return entity instance
     */
    fun create(settings: ISessionManagerSettings?): ISessionManager
}
