package com.exactpro.th2.util

import com.exactpro.th2.conn.dirty.tcp.core.util.indexOf
import com.exactpro.th2.conn.dirty.tcp.core.util.insert
import com.exactpro.th2.conn.dirty.tcp.core.util.substring
import io.netty.buffer.ByteBuf

fun ByteBuf.getContentStartIdx(): Int {
    var startIdx = 0
    var endIdx: Int
    while(true) {
        endIdx = indexOf(HTTP_LINE_END_INDICATOR, startIdx)
        if(endIdx < 0) return -1
        if(endIdx - startIdx <= 2) return endIdx // /n/r or /n
        startIdx = endIdx
    }
}

fun ByteBuf.applyHeaders(
    headers: Map<String, List<String>>
) {
    val contentStartIdx = getContentStartIdx()
    var accLength = contentStartIdx
    headers.forEach { (header, value) ->
        val headerBytes: ByteArray = getHeaderValue(header)?.run {
            value.foldRight("$header:") {_, headerValue ->
                if(value.contains(headerValue)) "" else ", $headerValue" }.toByteArray()
        } ?: "$header: ${value.joinToString(HTTP_HEADER_VALUE_SEPARATOR)}".toByteArray()
        insert(headerBytes, accLength)
        accLength += headerBytes.size
    }
}

fun ByteBuf.getHeaderValue(headerName: String): String? {
    val contentStartIdx = getContentStartIdx()
    val startIdx = indexOf(headerName)
    if(contentStartIdx < 0) return null
    if(startIdx < 0) return null
    val endIdx = indexOf(HTTP_LINE_END_INDICATOR, startIdx, contentStartIdx)
    if(endIdx < 0) return null
    substring(startIdx, endIdx).split(HTTP_HEADER_SEPARATOR).let {
        if(it.size != 2) {
            throw Exception("Malformed http message. Header must contain one $HTTP_HEADER_SEPARATOR.")
        }
        return it[1].trim()
    }
}

fun ByteBuf.getRequestLineParts(): Triple<String, String, String> {
    val requestLineEndIdx = indexOf(HTTP_LINE_END_INDICATOR)
    if(requestLineEndIdx < 0) throw Exception("Malformed buffer. Request line does not contain end of line character.")
    val requestLine = substring(0, requestLineEndIdx)
    requestLine.split(" ").let {
        if(it.size != 3) {
            throw Exception("Malformed http request line: request line must contain 3 parts, actually there is ${it.size} parts.\n" +
                    "Request line: $requestLine")
        }
        return Triple(it[0], it[1], it[2])
    }
}