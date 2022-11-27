package com.exactpro.th2.http.client.codec

import com.exactpro.th2.http.client.dirty.handler.data.DirtyHttpRequest
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import com.exactpro.th2.http.client.dirty.handler.codec.DirtyRequestDecoder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.charset.Charset

class RequestCodecTests {

    @Test
    fun `Request decode`() {
        val requestString = """
            POST /test/demo_form.php HTTP/1.1
            Host: w3schools.com
            Content-Length: 25

            name1=value1&name2=value2
            """.trimIndent().replace("\n", "\r\n")
        createCodec().decodeAsChannel(requestString, 0, 1) {
            Assertions.assertEquals("POST", it.method.name())
            Assertions.assertEquals("/test/demo_form.php", it.url)
            Assertions.assertEquals("HTTP/1.1", it.version.text())
            Assertions.assertEquals("w3schools.com", it.headers["Host"])
            Assertions.assertEquals("name1=value1&name2=value2", it.body.toString(Charset.defaultCharset()))
            Assertions.assertEquals(requestString, it.reference.readerIndex(0).toString(Charset.defaultCharset()))
        }
    }

    @Test
    fun `Request decode without body`() {
        val requestString = """
            POST /test/demo_form.php HTTP/1.1
            Host: w3schools.com
            
            
            """.trimIndent().replace("\n", "\r\n")
        createCodec().decodeAsChannel(requestString, 0, 1) {
            Assertions.assertEquals("POST", it.method.name())
            Assertions.assertEquals("/test/demo_form.php", it.url)
            Assertions.assertEquals("HTTP/1.1", it.version.text())
            Assertions.assertEquals("w3schools.com", it.headers["Host"])
            Assertions.assertTrue(it.body.toString(Charset.defaultCharset()).isEmpty())
            Assertions.assertEquals(requestString, it.reference.readerIndex(0).toString(Charset.defaultCharset()))
        }
    }

    @Test
    fun `Request only with start line`() {
        val requestString = """
            POST /test/demo_form.php HTTP/1.1
            
            
            """.trimIndent().replace("\n", "\r\n")
        createCodec().decodeAsChannel(requestString, 0, 1) {
            Assertions.assertEquals("POST", it.method.name())
            Assertions.assertEquals("/test/demo_form.php", it.url)
            Assertions.assertEquals("HTTP/1.1", it.version.text())
            Assertions.assertTrue(it.body.toString(Charset.defaultCharset()).isEmpty())
            Assertions.assertEquals(requestString, it.reference.readerIndex(0).toString(Charset.defaultCharset()))
        }
    }


    private fun DirtyRequestDecoder.decodeAsChannel(data: String, chunkSize: Int, expectCount: Int, messageAssertion: (DirtyHttpRequest) -> Unit) {
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

    private fun createCodec() = DirtyRequestDecoder()

    private fun DirtyRequestDecoder.decodeAsMainHandler(buffer: ByteBuf): List<DirtyHttpRequest> = mutableListOf<DirtyHttpRequest>().apply {
        while (buffer.isReadable) {
            add(decode(buffer) ?: break)
        }
    }

}