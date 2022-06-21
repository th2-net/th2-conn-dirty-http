package com.exactpro.th2.netty

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import org.apache.commons.lang3.mutable.Mutable
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

class HttpFramingHelper(): HttpResponseDecoder() {

    private val headQueue = ConcurrentLinkedQueue<Int>()
    private val isHeadResponse = AtomicBoolean(false)

    fun decode0(buffer: ByteBuf, out: MutableList<Any>): Int {

        var startReaderIndex = buffer.readerIndex()
        var prevReaderIndex = buffer.readerIndex()
        while (buffer.readableBytes() > 0) {
            decode(null, buffer, out)
            prevReaderIndex = if(prevReaderIndex == buffer.readerIndex()) break else buffer.readerIndex()
            if(out.isNotEmpty() && out.last() is LastHttpContent) {
                return startReaderIndex
            }
        }
        return -1
    }

    override fun isContentAlwaysEmpty(msg: HttpMessage): Boolean {
        return isHeadResponse.get() || super.isContentAlwaysEmpty(msg)
    }
}