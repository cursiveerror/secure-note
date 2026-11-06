package org.cursiveerror.securenote

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.UUID
import java.util.concurrent.Executors
import javax.crypto.AEADBadTagException
import kotlin.concurrent.thread

class SyncServer(
    private val httpPort: Int = 8000,
    private val udpPort: Int = 8888,
    private val onSync: (List<Note>) -> List<Note>,
    private val getCurrentNotes: () -> List<Note>,
    private val onPairStatusChanged: ((Boolean) -> Unit)? = null
) {
    private var httpServer: HttpServer? = null
    private var udpSocket: DatagramSocket? = null
    @Volatile private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true

        CryptoManager.getOrGenerateKey()
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

            val syncHandler = object : HttpHandler {
                override fun handle(exchange: HttpExchange) {
                    try {
                        val path = exchange.requestURI.path.lowercase()
                        val method = exchange.requestMethod.uppercase()

                        if (path == "/pair") {
                            if (method == "POST") {
                                val bodyBytes = exchange.requestBody.readBytes()
                                val bodyText = String(bodyBytes, Charsets.UTF_8).trim()
                                if (bodyText.isNotEmpty() && bodyText.startsWith("{") && bodyText.contains("\"iv\"")) {
                                    val json = JSONObject(bodyText)
                                    val iv = json.getString("iv")
                                    val data = json.getString("data")
                                    CryptoManager.decrypt(data, iv)
                                }
                                NoteRepository.setPaired(true, "Phone")
                                onPairStatusChanged?.invoke(true)
                                println("[SyncServer] Phone successfully paired!")

                                val resp = """{"status":"ok","message":"Paired successfully"}""".toByteArray(Charsets.UTF_8)
                                exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
                                exchange.sendResponseHeaders(200, resp.size.toLong())
                                exchange.responseBody.use { it.write(resp) }
                                return
                            }
                        }

                        if (path == "/unpair") {
                            if (method == "POST") {
                                NoteRepository.setPaired(false)
                                onPairStatusChanged?.invoke(false)
                                println("[SyncServer] Device unpaired.")
                                val resp = """{"status":"ok","message":"Unpaired"}""".toByteArray(Charsets.UTF_8)
                                exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
                                exchange.sendResponseHeaders(200, resp.size.toLong())
                                exchange.responseBody.use { it.write(resp) }
                                return
                            }
                        }

                        if (method == "POST") {
                            val bodyBytes = exchange.requestBody.readBytes()
                            val bodyText = String(bodyBytes, Charsets.UTF_8).trim()

                            var wasEncrypted = false
                            val finalJson: JSONObject = if (bodyText.isNotEmpty()) {
                                val json = JSONObject(bodyText)
                                if (json.has("iv") && json.has("data")) {
                                    wasEncrypted = true
                                    val iv = json.getString("iv")
                                    val data = json.getString("data")
                                    val decryptedText = CryptoManager.decrypt(data, iv)
                                    println("[Sync] Decrypted incoming sync request via AES-256-GCM.")
                                    JSONObject(decryptedText)
                                } else {
                                    json
                                }
                            } else {
                                JSONObject()
                            }

                            // If sync succeeds with encryption, device is confirmed paired
                            NoteRepository.setPaired(true, "Phone")
                            onPairStatusChanged?.invoke(true)

                            val incomingList = mutableListOf<Note>()
                            if (finalJson.has("clientNotes")) {
                                val clientArr = finalJson.getJSONArray("clientNotes")
                                for (i in 0 until clientArr.length()) {
                                    val obj = clientArr.getJSONObject(i)
                                    incomingList.add(
                                        Note(
                                            id = obj.optString("id", UUID.randomUUID().toString()),
                                            title = obj.optString("title", ""),
                                            content = obj.optString("content", ""),
                                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                                            isDeleted = obj.optBoolean("isDeleted", false)
                                        )
                                    )
                                }
                            } else if (finalJson.has("title") || finalJson.has("content")) {
                                incomingList.add(
                                    Note(
                                        id = finalJson.optString("id", UUID.randomUUID().toString()),
                                        title = finalJson.optString("title", "Untitled"),
                                        content = finalJson.optString("content", ""),
                                        timestamp = finalJson.optLong("timestamp", System.currentTimeMillis()),
                                        isDeleted = finalJson.optBoolean("isDeleted", false)
                                    )
                                )
                            }

                            // Perform 2-Way synchronization merge
                            val mergedNotes = onSync(incomingList)

                            val array = JSONArray()
                            for (n in mergedNotes) {
                                array.put(JSONObject().apply {
                                    put("id", n.id)
                                    put("title", n.title)
                                    put("content", n.content)
                                    put("timestamp", n.timestamp)
                                    put("isDeleted", n.isDeleted)
                                })
                            }

                            val rawResp = JSONObject().apply {
                                put("status", "ok")
                                put("notes", array)
                            }.toString()

                            val responseText = if (wasEncrypted) {
                                try {
                                    val (iv, data) = CryptoManager.encrypt(rawResp)
                                    """{"iv":"$iv","data":"$data"}"""
                                } catch (_: Exception) {
                                    rawResp
                                }
                            } else {
                                rawResp
                            }

                            val response = responseText.toByteArray(Charsets.UTF_8)
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
                                    put("isDeleted", n.isDeleted)
                                })
                            }
                            val rawJson = JSONObject().apply {
                                put("status", "ok")
                                put("notes", array)
                            }.toString()

                            val isRawRequested = exchange.requestURI.path.equals("/raw", ignoreCase = true)
                            val responseText = if (isRawRequested) {
                                array.toString(2)
                            } else {
                                try {
                                    val (iv, data) = CryptoManager.encrypt(rawJson)
                                    """{"iv":"$iv","data":"$data"}"""
                                } catch (_: Exception) {
                                    rawJson
                                }
                            }
                            val response = responseText.toByteArray(Charsets.UTF_8)
                            exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
                            exchange.sendResponseHeaders(200, response.size.toLong())
                            exchange.responseBody.use { it.write(response) }
                        } else {
                            exchange.sendResponseHeaders(405, -1)
                        }
                    } catch (e: AEADBadTagException) {
                        println("[Crypto] Decryption failed: invalid key or tampered data.")
                        val errorResp = """{"error":"invalid_key","message":"Key mismatch. Re-pair via QR code."}""".toByteArray(Charsets.UTF_8)
                        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
                        exchange.sendResponseHeaders(401, errorResp.size.toLong())
                        exchange.responseBody.use { it.write(errorResp) }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        val errorResp = """{"error":"${e.message}"}""".toByteArray(Charsets.UTF_8)
                        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
                        exchange.sendResponseHeaders(500, errorResp.size.toLong())
                        exchange.responseBody.use { it.write(errorResp) }
                    }
                }
            }

            server.createContext("/", syncHandler)
            server.createContext("/sync", syncHandler)
            server.createContext("/notes", syncHandler)
            server.createContext("/pair", syncHandler)
            server.createContext("/unpair", syncHandler)

            server.start()
            httpServer = server
            println("[Server] HTTP Sync Server started on port $httpPort (AES-256-GCM 2-Way Sync enabled).")
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
                        println("[Discovery] Device detected at ${packet.address.hostAddress}. Replied with ACK.")
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
            // Method 1: Outbound socket route lookup (standard Java mechanism to resolve default LAN adapter)
            try {
                DatagramSocket().use { socket ->
                    socket.connect(InetAddress.getByName("8.8.8.8"), 10002)
                    val address = socket.localAddress
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        val ip = address.hostAddress
                        if (ip != null && ip != "0.0.0.0" && !ip.startsWith("127.") && !ip.startsWith("169.254.")) {
                            return ip
                        }
                    }
                }
            } catch (_: Exception) {}

            // Method 2: Network interfaces scan with virtual/VPN adapter filtering
            try {
                val virtualKeywords = listOf(
                    "virtual", "vbox", "vmware", "wintun", "tap", "tunnel",
                    "vpn", "vethernet", "hyper-v", "pseudo", "loopback", "teredo", "radmin"
                )
                val candidateIps = mutableListOf<String>()

                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val ni = interfaces.nextElement()
                    if (ni.isLoopback || !ni.isUp) continue

                    val name = (ni.name + " " + (ni.displayName ?: "")).lowercase()
                    val isVirtual = virtualKeywords.any { name.contains(it) }

                    val addresses = ni.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            val ip = addr.hostAddress
                            if (ip.startsWith("169.254.") || ip.startsWith("127.")) continue

                            if (!isVirtual) {
                                if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                                    return ip
                                }
                            } else {
                                candidateIps.add(ip)
                            }
                        }
                    }
                }

                if (candidateIps.isNotEmpty()) {
                    val nonVirtual = candidateIps.firstOrNull { it.startsWith("192.168.") }
                    if (nonVirtual != null) return nonVirtual
                    return candidateIps.first()
                }
            } catch (_: Exception) {}

            return "127.0.0.1"
        }
    }
}
