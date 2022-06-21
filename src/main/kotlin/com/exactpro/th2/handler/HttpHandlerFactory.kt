package com.exactpro.th2.handler

import com.exactpro.th2.conn.dirty.tcp.core.api.IContext
import com.exactpro.th2.conn.dirty.tcp.core.api.IProtocolHandler
import com.exactpro.th2.conn.dirty.tcp.core.api.IProtocolHandlerFactory
import com.exactpro.th2.conn.dirty.tcp.core.api.IProtocolHandlerSettings

class HttpHandlerFactory: IProtocolHandlerFactory {
    override val name: String
        get() = HttpHandlerFactory::class.java.name

    override val settings: Class<out IProtocolHandlerSettings> = HttpHandlerSettings::class.java

    override fun create(context: IContext<IProtocolHandlerSettings>): IProtocolHandler = HttpHandler(context)
}