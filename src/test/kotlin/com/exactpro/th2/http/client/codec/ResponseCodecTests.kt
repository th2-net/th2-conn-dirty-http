package com.exactpro.th2.http.client.codec

import com.exactpro.th2.http.client.dirty.handler.data.DirtyHttpResponse
import com.exactpro.th2.http.client.dirty.handler.data.NettyHttpVersion
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.DecoderResult
import io.netty.handler.codec.DirtyResponseDecoder
import mu.KotlinLogging
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.charset.Charset

class ResponseCodecTests {

    @BeforeEach
    fun `after each`() {
        currentBuffer.release()
        currentBuffer = Unpooled.buffer()
    }

    @Test
    fun `fully response decode`() {
        val channel = createChannel()
        val httpResponse = """
            HTTP/1.1 200 OK
            Host: w3schools.com
            Content-Length: 25

            name1=value1&name2=value2
            """.trimIndent()

        try {
            channel.testResponse(httpResponse, 0, 1) {
                Assertions.assertEquals(DecoderResult.SUCCESS, it.decoderResult)
                Assertions.assertEquals(NettyHttpVersion.HTTP_1_1, it.version)
                Assertions.assertEquals(200, it.code)
                Assertions.assertEquals("OK", it.reason)
                Assertions.assertEquals(httpResponse, it.reference.readerIndex(0).toString(Charset.defaultCharset()))
            }
        } finally {
            channel.close().sync()
        }
    }

    @Test
    fun `partly response decode`() {
        val channel = createChannel()
        val httpResponse = """
            HTTP/1.1 200 OK
            Host: w3schools.com
            Content-Length: 25

            name1=value1&name2=value2
            """.trimIndent()
        try {
            channel.testResponse(httpResponse, 35, 1) {
                Assertions.assertEquals(DecoderResult.SUCCESS, it.decoderResult)
                Assertions.assertEquals(NettyHttpVersion.HTTP_1_1, it.version)
                Assertions.assertEquals(200, it.code)
                Assertions.assertEquals("OK", it.reason)
                Assertions.assertEquals(httpResponse, it.reference.readerIndex(0).toString(Charset.defaultCharset()))
            }
        } finally {
            channel.close().sync()
        }
    }

    @Test
    fun `chunked response`() {
        val httpResponse = """
            HTTP/1.1 200 OK
            Content-Type: plain/text
            Content-Length: 205
            
            { "id" : 901, "name" : { "first":"Tom", "middle":"and", "last":"Jerry" }, "phones" : [ {"type" : "home", "number" : "1233333" }, {"type" : "work", "number" : "264444" }], "lazy" : false, "married" : null }
        """.trimIndent().replace("\n", "\r\n")
        val channel = createChannel()
        try {
            channel.testResponse(httpResponse, 20, 1) {
                Assertions.assertEquals(httpResponse, it.reference.readerIndex(0).toString(Charset.defaultCharset()))
            }
        } finally {
            channel.close().sync()
        }
    }

    @Test
    fun `multiple chunked responses`() {
        val httpResponse = """
            HTTP/1.1 200 OK
            Content-Type: plain/text
            Content-Length: 205
            
            { "id" : 901, "name" : { "first":"Tom", "middle":"and", "last":"Jerry" }, "phones" : [ {"type" : "home", "number" : "1233333" }, {"type" : "work", "number" : "264444" }], "lazy" : false, "married" : null }
        """.trimIndent().replace("\n", "\r\n")
        val channel = createChannel()
        try {
            channel.testResponse(httpResponse.repeat(3), 20, 3) {
                Assertions.assertEquals(httpResponse, it.reference.readerIndex(0).toString(Charset.defaultCharset()))
            }
        } finally {
            channel.close().sync()
        }
    }

    @Test
    fun `multiple chunked responses with tail`() {
        val httpResponse = """
            HTTP/1.1 200 OK
            Content-Type: plain/text
            Content-Length: 205
            
            { "id" : 901, "name" : { "first":"Tom", "middle":"and", "last":"Jerry" }, "phones" : [ {"type" : "home", "number" : "1233333" }, {"type" : "work", "number" : "264444" }], "lazy" : false, "married" : null }
        """.trimIndent().replace("\n", "\r\n")
        val channel = createChannel()
        try {
            channel.testResponse(httpResponse.repeat(3) + "HTTP/1.1", 20, 3) {
                Assertions.assertEquals(httpResponse, it.reference.readerIndex(0).toString(Charset.defaultCharset()))
            }
        } finally {
            channel.close().sync()
        }
    }

    @Test
    fun `chunked body test`() {
        val httpResponse = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: plain/text\r\n")
            append("Transfer-Encoding: chunked\r\n")
            append("\r\n")
            append("6\r\n")
            append("Test \r\n")
            append("4\r\n")
            append("Body\r\n")
            append("0\r\n")
            append("\r\n")
        }
        val channel = createChannel()
        try {
            channel.testResponse(httpResponse, 0, 1) {
                Assertions.assertEquals(httpResponse, it.reference.readerIndex(0).toString(Charset.defaultCharset()))
            }
        } finally {
            channel.close().sync()
        }
    }

    @Test
    fun `chunked empty body test`() {
        val httpResponse = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: plain/text\r\n")
            append("Transfer-Encoding: chunked\r\n")
            append("\r\n")
            append("0\r\n")
            append("\r\n")
        }
        val channel = createChannel()
        try {
            channel.testResponse(httpResponse, 0, 1) {
                Assertions.assertEquals(httpResponse, it.reference.readerIndex(0).toString(Charset.defaultCharset()))
            }
        } finally {
            channel.close().sync()
        }
    }

    private fun EmbeddedChannel.testResponse(data: String, chunkSize: Int, expectCount: Int, messageAssertion: (DirtyHttpResponse) -> Unit) {
        if (chunkSize == 0) {
            decode(data)
        } else {
            val chunks = data.chunked(chunkSize)
            chunks.forEach { chunk ->
                decode(chunk)
            }
        }
        Assertions.assertEquals(expectCount, inboundMessages().size) {"Test with response data:\n==============\n$data\n==============\nmust have been recognized as $expectCount messages"}
        while(inboundMessages().size != 0) messageAssertion(readInbound())
    }

    private fun createChannel() = EmbeddedChannel(DirtyResponseDecoder())

    private fun EmbeddedChannel.decode(data: String) {
        currentBuffer.writeBytes(data.toByteArray())
        while (currentBuffer.isReadable) {
            if (writeAndCheckInbound(currentBuffer)) {
                currentBuffer.discardReadBytes()
            } else break
        }
    }

    private var currentBuffer: ByteBuf = Unpooled.buffer()


    private fun EmbeddedChannel.writeAndCheckInbound(msg: Any): Boolean {
        val lastResult = inboundMessages().size
        writeInbound(msg)
        return lastResult != inboundMessages().size
    }

    companion object {
        private val LOGGER = KotlinLogging.logger { this::class.java.simpleName }
    }

}