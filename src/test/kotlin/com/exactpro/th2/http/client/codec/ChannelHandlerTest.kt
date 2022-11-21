package com.exactpro.th2.http.client.codec

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler
import io.netty.channel.embedded.EmbeddedChannel

abstract class ChannelHandlerTest {
    protected abstract fun createHandler(): ChannelHandler
    protected fun createChannel() = EmbeddedChannel(createHandler())

    protected fun EmbeddedChannel.decode(data: String) = process(data) { byteBuf ->
        writeInbound(byteBuf.retain())
    }

    private fun EmbeddedChannel.process(data: String, processingFunction: EmbeddedChannel.(ByteBuf) -> Boolean) {
        val byteBuf = Unpooled.buffer().writeBytes(data.toByteArray())
        var lastIndex = byteBuf.readerIndex()
        while (byteBuf.isReadable) {
            processingFunction(byteBuf)
            if (byteBuf.readerIndex() == lastIndex) break else lastIndex = byteBuf.readerIndex()
        }
        byteBuf.release()
    }

}