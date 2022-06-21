import com.exactpro.th2.conn.dirty.tcp.core.api.IContext
import com.exactpro.th2.conn.dirty.tcp.core.api.IProtocolHandlerSettings
import com.exactpro.th2.handler.HttpHandler
import com.exactpro.th2.util.CONTENT_TYPE_PROPERTY
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull


class TestHttpHandler {
    private val client: TestClient = TestClient()
    private val buffer: ByteBuf? = null
    private val oneMessageBuffer: ByteBuf? = null
    private val brokenBuffer: ByteBuf? = null
    private val httpHandler: HttpHandler = client.httpHandler

    @Test
    fun testMessageWithContentLengthMessage() {
        val body = """{ "id" : 901, "name" : { "first":"Tom", "middle":"and", "last":"Jerry" }, "phones" : [ {"type" : "home", "number" : "1233333" }, {"type" : "work", "number" : "264444" }], "lazy" : false, "married" : null }"""

        val response: ByteArray = """
          HTTP/1.1 200 OK
          Content-Type: plain/text
          Content-Length: ${body.length}
          
          $body
          HTTP/1.1 200 OK
          Content-Type: plain/text
          Content-Length: ${body.length}
          
          $body
          """.trimIndent().toByteArray()

        val buf: ByteBuf = Unpooled.buffer()
        buf.writeBytes(response.sliceArray(0..10))
        assertNull(httpHandler.onReceive(buf))
        buf.writeBytes(response.sliceArray(11 until response.size-body.length - 10))
        val firstMessage = httpHandler.onReceive(buf)
        assertNotNull(firstMessage)
        assertEquals(buf.readerIndex(), 267)
        assertNull(httpHandler.onReceive(buf))
        buf.writeBytes(response.sliceArray(response.size-body.length - 10 until response.size))
        assertNotNull(httpHandler.onReceive(buf))
        val metadata: Map<String, String> = httpHandler.onIncoming(firstMessage)
        assertEquals(metadata["status"], "200")
        assertEquals(metadata[CONTENT_TYPE_PROPERTY], "plain/text")
    }

    @Test
    fun testChunkedResponse() {
        val response = ("HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/plain\r\n" +
                "Transfer-Encoding: chunked\r\n" +
                "\r\n" +
                "7\r\n" +
                "aaaaaaa\r\n" +
                "9\r\n" +
                "bbbbbbbbb\r\n" +
                "7\r\n" +
                "ccccccc\r\n" +
                "0\r\n" +
                "\r\n").toByteArray()
        val buf: ByteBuf = Unpooled.buffer()
        buf.writeBytes(response)
        val firstMessage = httpHandler.onReceive(buf)
        assertNotNull(firstMessage)
        buf.writeBytes(response.sliceArray(0..20))
        assertNull(httpHandler.onReceive(buf))
        buf.writeBytes(response.sliceArray(21 until response.size))
        assertNotNull(httpHandler.onReceive(buf))
        val metadata = httpHandler.onIncoming(firstMessage)
        assertEquals(metadata["status"], "200")
        assertEquals(metadata["contentType"], "text/plain")
    }

    @Test
    fun testResponseOnClosedConnection() {
        val body = """{ "id" : 901, "name" : { "first":"Tom", "middle":"and", "last":"Jerry" }, "phones" : [ {"type" : "home", "number" : "1233333" }, {"type" : "work", "number" : "264444" }], "lazy" : false, "married" : null }"""
        val response: ByteArray = """
          HTTP/1.1 200 OK
          Content-Type: plain/text
          
          $body
          """.trimIndent().toByteArray()

        val buf: ByteBuf = Unpooled.buffer()
        buf.writeBytes(response.sliceArray(0..35))
        assertNull(httpHandler.onReceive(buf))
        client.isOpen = false
        buf.writeBytes(response.sliceArray(36 until response.size))
        assertNotNull(httpHandler.onReceive(buf))
    }

    @Test
    fun testHeadResponse() {
        val response = """
            HTTP/1.1 200 OK
            Date: Wed, 01 Jul 2020 08:41:08 GMT
            Content-Type: application/json
            Content-Length: 19
            Connection: keep-alive
            Set-Cookie: __cfduid=d4fedc50db34d50c9b389d6ebb494b1b31593592868; expires=Fri, 31-Jul-20 08:41:08 GMT; path=/; domain=.domain.com; HttpOnly; SameSite=Lax; Secure
            CF-Cache-Status: DYNAMIC
            cf-request-id: 03ab2286b70000f015b49f6200000001
            Expect-CT: max-age=604800, report-uri="https://name.com/name/name"
            Server: server
            CF-RAY: 5abed3845b60f015-EWR
            
            HTTP/1.1 200 OK
            Date: Wed, 01 Jul 2020 08:41:08 GMT
            Content-Type: application/json
            Content-Length: 19
            Connection: keep-alive
            Set-Cookie: __cfduid=d4fedc50db34d50c9b389d6ebb494b1b31593592868; expires=Fri, 31-Jul-20 08:41:08 GMT; path=/; domain=.domain.com; HttpOnly; SameSite=Lax; Secure
            CF-Cache-Status: DYNAMIC
            cf-request-id: 03ab2286b70000f015b49f6200000001
            Expect-CT: max-age=604800, report-uri="https://name.com/name/name"
            Server: server
            CF-RAY: 5abed3845b60f015-EWR
        """.trimIndent().toByteArray()

        val buf = Unpooled.buffer()
        buf.writeBytes(response)
        httpHandler.onReceive(buf)
        httpHandler.onReceive(buf)
    }
}
