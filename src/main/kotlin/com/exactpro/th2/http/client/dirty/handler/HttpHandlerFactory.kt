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

import com.exactpro.th2.conn.dirty.tcp.core.api.IHandlerContext
import com.exactpro.th2.conn.dirty.tcp.core.api.IHandlerFactory
import com.exactpro.th2.conn.dirty.tcp.core.api.IHandlerSettings
import com.exactpro.th2.http.client.dirty.handler.stateapi.DefaultStateFactory
import com.exactpro.th2.http.client.dirty.handler.stateapi.IStateFactory
import com.google.auto.service.AutoService
import mu.KotlinLogging

@AutoService(IHandlerFactory::class)
class HttpHandlerFactory: IHandlerFactory {

    private val stateFactory = load<IStateFactory>(DefaultStateFactory::class.java).also {
        LOGGER.info { "Loaded state factory: ${it.name}" }
    }

    override val name: String
        get() = HttpHandlerFactory::class.java.name
    override val settings: Class<out IHandlerSettings> = HttpHandlerSettings::class.java

    override fun create(context: IHandlerContext): HttpHandler = (context.settings as HttpHandlerSettings).let { settings ->
        HttpHandler(context, stateFactory.create(settings.stateSettings), settings)
    }

    companion object {
        private val LOGGER = KotlinLogging.logger { this::class.java.simpleName }
    }
}

