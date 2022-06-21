package com.exactpro.th2.handler

import com.exactpro.th2.conn.dirty.tcp.core.api.IProtocolHandlerSettings

class HttpHandlerSettings : IProtocolHandlerSettings {
    var username: String = ""

    var password: String = ""

    var defaultHeaders: Map<String, List<String>> = mapOf()
}