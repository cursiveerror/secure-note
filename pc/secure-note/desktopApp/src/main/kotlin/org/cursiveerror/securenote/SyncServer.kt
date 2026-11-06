package org.cursiveerror.securenote

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class SyncServer(
    private val httpPort: Int = 8000,
    private val udpPort: Int = 8888,
    private val onNoteReceived: (Note) -> Unit,
    private val getCurrentNotes: () -> List<Note>
) {
    private var httpServer: HttpServer? = null
    private var udpSocket: DatagramSocket? = null
    @Volatile private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true

        startHttpServer()
        startUdpDiscovery()
    }

    fun stop() {
        isRunning = false
        try {
            httpServer?.stop(0)
            httpServer = null
        } catch (_: Exception) {}

        try {
            udpSocket?.close()
            udpSocket = null
        } catch (_: Exception) {}
    }

    private fun startHttpServer() {
        try {
            val server = HttpServer.create(InetSocketAddress(httpPort), 0)
            server.executor = Executors.newCachedThreadPool()

            server.createContext("/", object : HttpHandler {
                override fun handle(exchange: HttpExchange) {
                    try {
                        val method = exchange.requestMethod.uppercase()
                        if (method == "POST") {
                            val bodyBytes = exchange.requestBody.readBytes()
                            val bodyText = String(bodyBytes, Charsets.UTF_8).trim()
                            if (bodyText.isNotEmpty()) {
                                val json = JSONObject(bodyText)
                                val title = json.optString("title", "Untitled")
                                val content = json.optString("content", "")
                                val id = json.optString("id", java.util.UUID.randomUUID().toString())
                                val note = Note(id = id, title = title, content = content)
                                onNoteReceived(note)
                            }
                            val response = """{"status":"ok"}""".toByteArray(Charsets.UTF_8)
                            exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
                            exchange.sendResponseHeaders(200, response.size.toLong())
                            exchange.responseBody.use { it.write(response) }
                        } else if (method == "GET") {
                            val notes = getCurrentNotes()
                            val array = JSONArray()
                            for (n in notes) {
                                array.put(JSONObject().apply {
                                    put("id", n.id)
                                    put("title", n.title)
                                    put("content", n.content)
                                    put("timestamp", n.timestamp)
                                })
                            }
                            val response = array.toString(2).toByteArray(Charsets.UTF_8)
                            exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
                            exchange.sendResponseHeaders(200, response.size.toLong())
                            exchange.responseBody.use { it.write(response) }
                        } else {
                            exchange.sendResponseHeaders(405, -1)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        val errorResp = """{"error":"${e.message}"}""".toByteArray(Charsets.UTF_8)
                        exchange.sendResponseHeaders(500, errorResp.size.toLong())
                        exchange.responseBody.use { it.write(errorResp) }
                    }
                }
            })

            server.start()
            httpServer = server
            println("HTTP Server started on port $httpPort")
        } catch (e: Exception) {
            println("Failed to start HTTP server: ${e.message}")
        }
    }

    private fun startUdpDiscovery() {
        thread(isDaemon = true, name = "UDP-Discovery") {
            try {
                val socket = DatagramSocket(null)
                socket.reuseAddress = true
                socket.bind(InetSocketAddress("0.0.0.0", udpPort))
                udpSocket = socket

                val buffer = ByteArray(1024)
                while (isRunning) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (e: Exception) {
                        if (!isRunning) break
                        continue
                    }

                    val message = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    if (message == "DISCOVER_NOTES_SERVER") {
                        val replyData = "NOTES_SERVER_ACK:$httpPort".toByteArray(Charsets.UTF_8)
                        val replyPacket = DatagramPacket(
                            replyData,
                            replyData.size,
                            packet.address,
                            packet.port
                        )
                        socket.send(replyPacket)
                        println("UDP Auto-discovery: phone detected at ${packet.address.hostAddress}! Replied with ACK.")
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    println("UDP Server error: ${e.message}")
                }
            }
        }
    }

    companion object {
        fun getLocalIpAddress(): String {
            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    if (networkInterface.isLoopback || !networkInterface.isUp) continue

                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (!address.isLoopbackAddress && address is Inet4Address) {
                            val ip = address.hostAddress
                            // Prefer standard private LAN IP subnets (192.168.x.x, 10.x.x.x, 172.16.x.x)
                            if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                                return ip
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
            return "127.0.0.1"
        }
    }
}
