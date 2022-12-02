package com.eactpro.th2.httpws.client

import com.exactpro.th2.common.message.toJson
import com.exactpro.th2.common.schema.message.MessageListener
import com.exactpro.th2.common.schema.message.MessageRouter
import com.exactpro.th2.common.schema.message.MessageRouterContext
import com.exactpro.th2.common.schema.message.SubscriberMonitor
import com.exactpro.th2.conn.dirty.tcp.core.Microservice
import com.exactpro.th2.conn.dirty.tcp.core.SessionSettings
import com.exactpro.th2.conn.dirty.tcp.core.Settings
import com.exactpro.th2.conn.dirty.tcp.core.api.impl.DummyManglerFactory
import com.exactpro.th2.conn.dirty.tcp.core.api.impl.DummyManglerFactory.DummyManglerSettings
import com.exactpro.th2.http.client.dirty.handler.HttpHandlerFactory
import com.exactpro.th2.http.client.dirty.handler.HttpHandlerSettings
import com.google.protobuf.Message
import java.io.InputStream
import kotlin.test.Ignore
import kotlin.test.Test

class Test {
    @Test
    @Ignore
    fun simple() {
        val settings = Settings(
            batchByGroup = false,
            sessions = listOf(
                SessionSettings(
                    sessionAlias = "test-alias",
                    sessionGroup = "test-group",
                    handler = HttpHandlerSettings(host = "127.0.0.1", port = 8888),
                    mangler = DummyManglerSettings()
                )
            )
        )

        val service = Microservice(
            "root-event",
            settings,
            { InputStream.nullInputStream() },
            TestRouter("event-router"),
            TestRouter("message-router"),
            HttpHandlerFactory(),
            DummyManglerFactory,
        ) { resource, _ ->
            println("Registered resource: $resource")
        }

        service.run()
        while (true) Thread.sleep(1000)
    }

    private class TestRouter<T : Message>(private val name: String) : MessageRouter<T> {
        override fun init(context: MessageRouterContext) = println("$name - init")
        override fun sendAll(message: T, vararg queueAttr: String?) = println("$name - sendAll(message: ${message.toJson()}, queueAttr: ${queueAttr.contentToString()})")
        override fun send(message: T, vararg queueAttr: String?) = println("$name - send(message: ${message.toJson()}, queueAttr: ${queueAttr.contentToString()})")

        override fun subscribeAll(callback: MessageListener<T>?, vararg queueAttr: String?): SubscriberMonitor {
            println("$name - subscribeAll(callback: ${callback}, vararg: ${queueAttr.contentToString()})")
            return SubscriberMonitor {}
        }

        override fun subscribe(callback: MessageListener<T>?, vararg queueAttr: String?): SubscriberMonitor {
            println("$name - subscribe(callback: ${callback}, vararg: ${queueAttr.contentToString()})")
            return SubscriberMonitor {}
        }

        override fun close() = println("$name - close()")
    }
}