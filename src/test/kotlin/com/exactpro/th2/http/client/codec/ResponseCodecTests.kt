package com.exactpro.th2.http.client.codec

import com.exactpro.th2.http.client.dirty.handler.data.DirtyHttpResponse
import com.exactpro.th2.http.client.dirty.handler.data.NettyHttpVersion
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.handler.codec.DecoderResult
import io.netty.handler.codec.DirtyResponseDecoder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.charset.Charset

class ResponseCodecTests {

    @Test
    fun `fully response decode`() {
        val httpResponse = """
            HTTP/1.1 200 OK
            Host: w3schools.com
            Content-Length: 25

            name1=value1&name2=value2
            """.trimIndent()

        createCodec().decodeAsChannel(httpResponse, 0, 1) {
            Assertions.assertEquals(DecoderResult.SUCCESS, it.decoderResult)
            Assertions.assertEquals(NettyHttpVersion.HTTP_1_1, it.version)
            Assertions.assertEquals(200, it.code)
            Assertions.assertEquals("OK", it.reason)
            Assertions.assertEquals(httpResponse, it.reference.readerIndex(0).toString(Charset.defaultCharset()))
        }
    }

    @Test
    fun `partly response decode`() {
        val httpResponse = """
            HTTP/1.1 200 OK
            Host: w3schools.com
            Content-Length: 25

            name1=value1&name2=value2
            """.trimIndent()
        createCodec().decodeAsChannel(httpResponse,  35, 1) {
            Assertions.assertEquals(DecoderResult.SUCCESS, it.decoderResult)
            Assertions.assertEquals(NettyHttpVersion.HTTP_1_1, it.version)
            Assertions.assertEquals(200, it.code)
            Assertions.assertEquals("OK", it.reason)
            Assertions.assertEquals(httpResponse, it.reference.readerIndex(0).toString(Charset.defaultCharset()))
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
        createCodec().decodeAsChannel(httpResponse, 20, 1) {
            Assertions.assertEquals(DecoderResult.SUCCESS, it.decoderResult)
            Assertions.assertEquals(NettyHttpVersion.HTTP_1_1, it.version)
            Assertions.assertEquals(200, it.code)
            Assertions.assertEquals("OK", it.reason)
            Assertions.assertEquals(httpResponse, it.reference.readerIndex(0).toString(Charset.defaultCharset()))
        }
    }

    @Test
    fun `multiple responses`() {
        val httpResponse = """
            HTTP/1.1 200 OK
            Content-Type: plain/text
            Content-Length: 205
            
            { "id" : 901, "name" : { "first":"Tom", "middle":"and", "last":"Jerry" }, "phones" : [ {"type" : "home", "number" : "1233333" }, {"type" : "work", "number" : "264444" }], "lazy" : false, "married" : null }
        """.trimIndent().replace("\n", "\r\n")
        createCodec().decodeAsChannel(httpResponse.repeat(3), 0, 3) {
            Assertions.assertEquals(DecoderResult.SUCCESS, it.decoderResult)
            Assertions.assertEquals(NettyHttpVersion.HTTP_1_1, it.version)
            Assertions.assertEquals(200, it.code)
            Assertions.assertEquals("OK", it.reason)
            Assertions.assertEquals(httpResponse, it.reference.readerIndex(0).toString(Charset.defaultCharset()))
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
        createCodec().decodeAsChannel(httpResponse.repeat(3), 20, 3) {
            Assertions.assertEquals(DecoderResult.SUCCESS, it.decoderResult)
            Assertions.assertEquals(NettyHttpVersion.HTTP_1_1, it.version)
            Assertions.assertEquals(200, it.code)
            Assertions.assertEquals("OK", it.reason)
            Assertions.assertEquals(httpResponse, it.reference.readerIndex(0).toString(Charset.defaultCharset()))
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
        createCodec().decodeAsChannel(httpResponse.repeat(3) + "HTTP/1.1", 20, 3) {
            Assertions.assertEquals(DecoderResult.SUCCESS, it.decoderResult)
            Assertions.assertEquals(NettyHttpVersion.HTTP_1_1, it.version)
            Assertions.assertEquals(200, it.code)
            Assertions.assertEquals("OK", it.reason)
            Assertions.assertEquals(httpResponse, it.reference.readerIndex(0).toString(Charset.defaultCharset()))
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
        createCodec().decodeAsChannel(httpResponse, 0, 1) {
            Assertions.assertEquals(DecoderResult.SUCCESS, it.decoderResult)
            Assertions.assertEquals(NettyHttpVersion.HTTP_1_1, it.version)
            Assertions.assertEquals(200, it.code)
            Assertions.assertEquals("OK", it.reason)
            Assertions.assertEquals(httpResponse, it.reference.readerIndex(0).toString(Charset.defaultCharset()))
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
        createCodec().decodeAsChannel(httpResponse, 0, 1) {
            Assertions.assertEquals(DecoderResult.SUCCESS, it.decoderResult)
            Assertions.assertEquals(NettyHttpVersion.HTTP_1_1, it.version)
            Assertions.assertEquals(200, it.code)
            Assertions.assertEquals("OK", it.reason)
            Assertions.assertEquals(httpResponse, it.reference.readerIndex(0).toString(Charset.defaultCharset()))
        }
    }

    private fun DirtyResponseDecoder.decodeAsChannel(data: String, chunkSize: Int, expectCount: Int, messageAssertion: (DirtyHttpResponse) -> Unit) {
        val currentBuffer = Unpooled.buffer()
        var count = 0
        if (chunkSize == 0) {
            count += decodeAsMainHandler(currentBuffer.writeBytes(data.toByteArray())).onEach(messageAssertion).size
        } else {
            val chunks = data.chunked(chunkSize)
            chunks.forEach { chunk ->
                currentBuffer.writeBytes(chunk.toByteArray())
                count += decodeAsMainHandler(currentBuffer).onEach(messageAssertion).size
                currentBuffer.discardReadBytes()
            }
        }
        currentBuffer.release()

        Assertions.assertEquals(expectCount, count) {"Test with response data:\n==============\n$data\n==============\nData must have been recognized as $expectCount messages"}
    }

    private fun createCodec() = DirtyResponseDecoder()

    private fun DirtyResponseDecoder.decodeAsMainHandler(buffer: ByteBuf): List<DirtyHttpResponse> = mutableListOf<DirtyHttpResponse>().apply {
        while (buffer.isReadable) {
            add(decode(buffer) ?: break)
        }
    }

}