package com.ncviewer.idea.bridge

import com.google.gson.Gson
import com.ncviewer.idea.log.NcViewerLogConfig
import com.ncviewer.idea.log.NcViewerLogService
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class NcViewerHttpBridge(private val logService: NcViewerLogService) {

    private val gson = Gson()
    private val executor = Executors.newCachedThreadPool()
    private val messageVersion = AtomicLong(0L)
    private val messageBuffer = ArrayDeque<MessageEnvelope>()
    private val maxBufferedMessages = 128
    private val bufferLock = Any()
    private val incomingListeners = CopyOnWriteArrayList<(String) -> Unit>()

    private val server: HttpServer = HttpServer.create(
        InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
        0,
    )

    val port: Int = server.address.port
    private val token: String = UUID.randomUUID().toString()

    init {
        server.createContext("/ncbridge/poll", HttpHandler { exchange ->
            handlePoll(exchange)
        })
        server.createContext("/ncbridge/event", HttpHandler { exchange ->
            handleEvent(exchange)
        })
        server.createContext("/ncbridge/health", HttpHandler { exchange ->
            if (!isAuthorized(exchange)) {
                respond(exchange, 401, "unauthorized")
                return@HttpHandler
            }
            respond(exchange, 200, "ok")
        })
        server.executor = executor
        server.start()
        logService.log("Http bridge started on port $port")
    }

    fun stop() {
        try {
            server.stop(0)
            executor.shutdownNow()
            logService.log("Http bridge stopped")
        } catch (t: Throwable) {
            logService.log("Http bridge stop error: ${t.message}")
        }
    }

    fun publish(messageJson: String) {
        val version = messageVersion.incrementAndGet()
        val envelope = MessageEnvelope(version, messageJson)
        synchronized(bufferLock) {
            messageBuffer.addLast(envelope)
            while (messageBuffer.size > maxBufferedMessages) {
                messageBuffer.removeFirst()
            }
        }
        if (NcViewerLogConfig.verbose) {
            logService.log("Http bridge publish version=$version len=${messageJson.length}")
        }
    }

    fun addIncomingListener(listener: (String) -> Unit) {
        incomingListeners += listener
    }

    fun removeIncomingListener(listener: (String) -> Unit) {
        incomingListeners.remove(listener)
    }

    fun endpointScript(): String {
        val endpoint = "http://127.0.0.1:$port/ncbridge"
        return "<script>window.__NC_HTTP_ENDPOINT='$endpoint';window.__NC_HTTP_TOKEN='$token';</script>"
    }

    private fun handlePoll(exchange: HttpExchange) {
        if (handlePreflight(exchange)) return
        if (!isAuthorized(exchange)) {
            respond(exchange, 401, "unauthorized")
            return
        }
        if (!exchange.requestMethod.equals("GET", ignoreCase = true)) {
            respond(exchange, 405, "method not allowed")
            return
        }
        val after = parseQuery(exchange.requestURI.rawQuery)["after"]?.toLongOrNull() ?: 0L
        val nextEnvelope = synchronized(bufferLock) {
            messageBuffer.firstOrNull { it.version > after }
        }
        if (nextEnvelope != null) {
            respondJson(exchange, 200, nextEnvelope)
        } else {
            respond(exchange, 204, "")
        }
    }

    private fun handleEvent(exchange: HttpExchange) {
        if (handlePreflight(exchange)) return
        if (!isAuthorized(exchange)) {
            respond(exchange, 401, "unauthorized")
            return
        }
        if (!exchange.requestMethod.equals("POST", ignoreCase = true)) {
            respond(exchange, 405, "method not allowed")
            return
        }
        val body = exchange.requestBody.use { input ->
            BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).readText()
        }
        if (NcViewerLogConfig.verbose) {
            logService.log("Http bridge event body len=${body.length}")
        }
        incomingListeners.forEach { listener ->
            try {
                listener(body)
            } catch (t: Throwable) {
                logService.log("Http bridge listener error: ${t.message}")
            }
        }
        respond(exchange, 202, "accepted")
    }

    private fun respond(exchange: HttpExchange, status: Int, text: String) {
        applyCorsHeaders(exchange)
        exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        val bodyAllowed = status !in setOf(204, 304)
        val length = if (bodyAllowed) bytes.size.toLong() else -1L
        exchange.sendResponseHeaders(status, length)
        if (bodyAllowed) {
            exchange.responseBody.use { it.write(bytes) }
        } else {
            exchange.responseBody.close()
        }
    }

    private fun respondJson(exchange: HttpExchange, status: Int, payload: Any) {
        val json = gson.toJson(payload)
        applyCorsHeaders(exchange)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        val bodyAllowed = status !in setOf(204, 304)
        val length = if (bodyAllowed) bytes.size.toLong() else -1L
        exchange.sendResponseHeaders(status, length)
        if (bodyAllowed) {
            exchange.responseBody.use { it.write(bytes) }
        } else {
            exchange.responseBody.close()
        }
    }

    private fun handlePreflight(exchange: HttpExchange): Boolean {
        if (!exchange.requestMethod.equals("OPTIONS", ignoreCase = true)) {
            return false
        }
        applyCorsHeaders(exchange)
        exchange.responseHeaders.add("Access-Control-Max-Age", "600")
        exchange.sendResponseHeaders(204, -1)
        return true
    }

    private fun applyCorsHeaders(exchange: HttpExchange) {
        exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
        exchange.responseHeaders.add("Access-Control-Allow-Headers", "X-Ncviewer-Token, Content-Type")
        exchange.responseHeaders.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
    }

    private fun isAuthorized(exchange: HttpExchange): Boolean {
        val header = exchange.requestHeaders.getFirst("X-Ncviewer-Token") ?: return false
        return header == token
    }

    private fun parseQuery(query: String?): Map<String, String> {
        if (query.isNullOrBlank()) return emptyMap()
        return query.split("&").mapNotNull { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.isEmpty()) return@mapNotNull null
            val key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8)
            val value = if (parts.size > 1) URLDecoder.decode(parts[1], StandardCharsets.UTF_8) else ""
            key to value
        }.toMap()
    }

    data class MessageEnvelope(
        val version: Long,
        val message: String,
    )
}
