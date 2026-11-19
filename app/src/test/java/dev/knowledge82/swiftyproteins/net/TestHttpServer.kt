package dev.knowledge82.swiftyproteins.net

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A tiny HTTP server for the tests, written with plain sockets so it works in any JVM and gives TOTAL control:
 * a test decides byte by byte what to send, how slowly, and when to hang up. Each connection serves ONE request.
 *
 * It listens only on the loopback address (127.0.0.1) and on a port chosen by the system.
 */
class TestHttpServer(private val handler: (request: String, socket: Socket) -> Unit) : AutoCloseable {

    private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    val port: Int get() = server.localPort

    /** The text of every request received (request line and headers), in order. */
    val requests = CopyOnWriteArrayList<String>()

    @Volatile
    private var running = true

    init {
        Thread {
            while (running) {
                val socket = try {
                    server.accept()
                } catch (e: IOException) {
                    break   // the server was closed
                }
                Thread { serve(socket) }.apply { isDaemon = true }.start()
            }
        }.apply { isDaemon = true }.start()
    }

    /** `http://127.0.0.1:PORT/ligands/view/`, like the real base URL but local. */
    val baseUrl: String get() = "http://127.0.0.1:$port/ligands/view/"

    private fun serve(socket: Socket) {
        try {
            socket.use {
                val input = it.getInputStream()
                val head = StringBuilder()
                while (!head.endsWith("\r\n\r\n")) {
                    val b = input.read()
                    if (b == -1) return
                    head.append(b.toChar())
                }
                requests += head.toString()
                handler(head.toString(), it)
            }
        } catch (e: IOException) {
            // the client hung up while we were writing: normal in the tests that cut or time out
        }
    }

    override fun close() {
        running = false
        server.close()
    }
}

/** The path of a request: from `GET /ligands/view/001.cif HTTP/1.1` it returns `/ligands/view/001.cif`. */
fun requestPath(request: String): String = request.lineSequence().first().split(" ")[1]

/** Writes a whole response and flushes it. [lengthHeader] lets a test lie about the length. */
fun Socket.respond(
    status: String = "200 OK",
    body: ByteArray = ByteArray(0),
    lengthHeader: Long? = body.size.toLong(),
    extraHeaders: List<String> = emptyList(),
) {
    val out = getOutputStream()
    val head = buildString {
        append("HTTP/1.1 $status\r\n")
        if (lengthHeader != null) append("Content-Length: $lengthHeader\r\n")
        extraHeaders.forEach { append("$it\r\n") }
        append("Connection: close\r\n\r\n")
    }
    out.write(head.toByteArray())
    out.write(body)
    out.flush()
}

/** A chunked response (`Transfer-Encoding: chunked`): each piece is announced with its size in hexadecimal. */
fun Socket.respondChunked(pieces: List<ByteArray>, finish: Boolean = true) {
    val out = getOutputStream()
    out.write("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n".toByteArray())
    for (piece in pieces) {
        out.write("${piece.size.toString(16)}\r\n".toByteArray())
        out.write(piece)
        out.write("\r\n".toByteArray())
    }
    if (finish) out.write("0\r\n\r\n".toByteArray())
    out.flush()
}
