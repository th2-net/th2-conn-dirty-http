package io.netty.handler.codec

import com.exactpro.th2.http.client.dirty.handler.data.DirtyHttpMessage
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import mu.KotlinLogging

abstract class DirtyHttpDecoder<T: DirtyHttpMessage> : ByteToMessageDecoder() {
    private var currentState = State.READ_INITIAL
    private var currentPosition = 0

    init {
        isSingleDecode = true
        this.setCumulator(COMPOSITE_CUMULATOR)
    }

    override fun decode(ctx: ChannelHandlerContext, `in`: ByteBuf, out: MutableList<Any>) {
        if (decodeSingle(`in`)) {
            out.add(buildCurrentMessage(`in`.copy(0, `in`.readerIndex())))
            cumulation.discardReadBytes()
            reset()
        } else {
            `in`.readerIndex(0)
        }
    }

    private fun decodeSingle(buffer: ByteBuf): Boolean {
        if (!buffer.isReadable && currentState == State.FINALIZE) return false
        buffer.readerIndex(currentPosition)
        try {
            when(currentState) {
                State.READ_INITIAL -> {
                    if (!parseStartLine(currentPosition, buffer)) {
                        return false
                    }
                    currentState = State.READ_HEADER
                }
                State.READ_HEADER -> {
                    if (!parseHeaders(currentPosition, buffer)) {
                        return false
                    }
                    currentState = State.READ_BODY
                }
                State.READ_BODY -> {
                    if (!parseBody(currentPosition, buffer)) {
                        return false
                    }
                    currentState = State.FINALIZE
                }
                State.FINALIZE -> {
                    return true
                }
                else -> {
                    throw java.lang.IllegalStateException("Unexpected state of decode: $currentState")
                }
            }
            currentPosition = buffer.readerIndex()
            return decodeSingle(buffer)
        } catch (e: Exception) {
            onDecodeFailure(buffer, e)
            buffer.readerIndex(buffer.writerIndex())
            return true
        }
    }

    protected abstract fun buildCurrentMessage(reference: ByteBuf): T
    protected abstract fun parseStartLine(position: Int, buffer: ByteBuf): Boolean
    protected abstract fun parseHeaders(position: Int, buffer: ByteBuf): Boolean
    protected abstract fun parseBody(position: Int, buffer: ByteBuf): Boolean
    protected abstract fun onDecodeFailure(buffer: ByteBuf, e: Exception)

    protected open fun reset() {
        currentState = State.READ_INITIAL
        currentPosition = 0
    }

    private enum class State {
        READ_INITIAL, READ_HEADER, READ_BODY, FINALIZE
    }

    companion object {
        private val LOGGER = KotlinLogging.logger { this::class.java.simpleName }
    }
}