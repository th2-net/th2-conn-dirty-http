package io.netty.handler.codec

import com.exactpro.th2.http.client.dirty.handler.data.DirtyHttpMessage
import io.netty.buffer.ByteBuf

abstract class DirtyHttpDecoder<T: DirtyHttpMessage> {
    private var currentState = State.READ_INITIAL
    private var currentPosition = 0
    private var startOfMessage = 0

    fun decode(input: ByteBuf): T? {
        if (currentState != State.READ_INITIAL) shrinkIfRequired(input) // Need to check if data was discarded
        return if (decodeSingle(input)) {
            buildCurrentMessage(startOfMessage, currentPosition, input).also {
                currentState = State.READ_INITIAL
                reset()
            }
        } else {
            input.readerIndex(startOfMessage)
            null
        }
    }

    private fun shrinkIfRequired(buffer: ByteBuf) {
        if (buffer.writerIndex() < startOfMessage) {
            currentPosition -= startOfMessage
            startOfMessage = 0
        }
    }

    private fun decodeSingle(buffer: ByteBuf): Boolean {
        try {
            when(currentState) {
                State.READ_INITIAL -> {
                    currentPosition = buffer.readerIndex()
                    startOfMessage = currentPosition
                    if (!parseStartLine(currentPosition, buffer)) {
                        return false
                    }
                    currentState = State.READ_HEADER
                }
                State.READ_HEADER -> {
                    if (!parseHeaders(currentPosition, buffer.readerIndex(currentPosition))) {
                        return false
                    }
                    currentState = State.READ_BODY
                }
                State.READ_BODY -> {
                    if (!parseBody(currentPosition, buffer.readerIndex(currentPosition))) {
                        return false
                    }
                    currentState = State.FINALIZE
                }
                State.FINALIZE -> {
                    finalize(currentPosition, buffer.readerIndex(currentPosition))
                    return true
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

    protected abstract fun buildCurrentMessage(startPos: Int, endPos: Int, originalBuf: ByteBuf): T
    protected abstract fun parseStartLine(position: Int, buffer: ByteBuf): Boolean
    protected abstract fun parseHeaders(position: Int, buffer: ByteBuf): Boolean
    protected abstract fun parseBody(position: Int, buffer: ByteBuf): Boolean
    protected open fun finalize(position: Int, buffer: ByteBuf) = Unit
    protected abstract fun onDecodeFailure(buffer: ByteBuf, e: Exception)
    protected abstract fun reset()

    private enum class State {
        READ_INITIAL, READ_HEADER, READ_BODY, FINALIZE
    }
}