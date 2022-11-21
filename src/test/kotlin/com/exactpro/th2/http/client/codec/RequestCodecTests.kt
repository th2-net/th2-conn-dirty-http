package com.exactpro.th2.http.client.codec

import com.exactpro.th2.http.client.dirty.handler.data.DirtyHttpRequest
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.DirtyRequestDecoder
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
        val channel = createChannel()
        try {
            channel.decode(requestString) {
                Assertions.assertEquals("POST", it.method.name())
                Assertions.assertEquals("/test/demo_form.php", it.url)
                Assertions.assertEquals("HTTP/1.1", it.version.text())
                Assertions.assertEquals("w3schools.com", it.headers["Host"])
                Assertions.assertEquals("name1=value1&name2=value2", it.body.toString(Charset.defaultCharset()))
                Assertions.assertEquals(requestString, it.reference.readerIndex(0).toString(Charset.defaultCharset()))
            }
        } finally {
            channel.close().sync()
        }
    }

    @Test
    fun `Request multiply decode`() {
        val requestString = """
            POST /test/demo_form.php HTTP/1.1
            Host: w3schools.com
            Content-Length: 25

            name1=value1&name2=value2
            """.trimIndent().replace("\n", "\r\n")
        val channel = createChannel()
        try {
            repeat(3) {
                channel.decode(requestString) {
                    Assertions.assertEquals("POST", it.method.name())
                    Assertions.assertEquals("/test/demo_form.php", it.url)
                    Assertions.assertEquals("HTTP/1.1", it.version.text())
                    Assertions.assertEquals("w3schools.com", it.headers["Host"])
                    Assertions.assertEquals("name1=value1&name2=value2", it.body.toString(Charset.defaultCharset()))
                    Assertions.assertEquals(requestString, it.reference.readerIndex(0).toString(Charset.defaultCharset()))
                }
            }

        } finally {
            channel.close().sync()
        }
    }

    @Test
    fun `Request decode without body`() {
        val requestString = """
            POST /test/demo_form.php HTTP/1.1
            Host: w3schools.com
            
            
            """.trimIndent().replace("\n", "\r\n")
        val channel = createChannel()
        try {
            channel.decode(requestString) {
                Assertions.assertEquals("POST", it.method.name())
                Assertions.assertEquals("/test/demo_form.php", it.url)
                Assertions.assertEquals("HTTP/1.1", it.version.text())
                Assertions.assertEquals("w3schools.com", it.headers["Host"])
                Assertions.assertTrue(it.body.toString(Charset.defaultCharset()).isEmpty())
                Assertions.assertEquals(requestString, it.reference.readerIndex(0).toString(Charset.defaultCharset()))
            }
        } finally {
            channel.close().sync()
        }
    }

    private fun EmbeddedChannel.decode(data: String, messageAssertion: (DirtyHttpRequest) -> Unit) {
        val currentBuffer = Unpooled.buffer().writeBytes(data.toByteArray())
        writeInbound(currentBuffer)
        Assertions.assertEquals(inboundMessages().size, 1)
        while(inboundMessages().size != 0) messageAssertion(readInbound())
    }


    private fun createChannel() = EmbeddedChannel(DirtyRequestDecoder())

}