package com.zerointerrupt.piano.rpc

import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

data class RpcTapCmd(
    val x: Float,
    val y: Float,
    val durationMs: Long
)

data class RpcConnectionProbe(
    val ok: Boolean,
    val shizukuConnected: Boolean = false,
    val engineMode: Int = 0,
    val overlayVisible: Boolean = false,
    val latencyMs: Long = 0L,
    val error: String = ""
)

/**
 * Persistent TCP_NODELAY JSON-line RPC client talking to ZeroInterruptClicker (127.0.0.1:27183).
 *
 * Pipelines multi-key chord commands in a single flushed write so M0 chord spread stays ~7-11ms.
 */
class RpcClient {
    private val lock = Any()
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private var connectedHost: String = ""
    private var connectedPort: Int = 0

    private fun ensureConnectedLocked(host: String, port: Int, timeoutMs: Int = 1800) {
        val alive = socket?.let { !it.isClosed && it.isConnected } == true &&
            connectedHost == host && connectedPort == port
        if (alive) return
        closeLocked()
        val s = Socket()
        s.tcpNoDelay = true
        s.soTimeout = timeoutMs
        s.connect(InetSocketAddress(host, port), timeoutMs)
        socket = s
        reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
        writer = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
        connectedHost = host
        connectedPort = port
    }

    private fun closeLocked() {
        try {
            socket?.close()
        } catch (_: Throwable) {
        }
        socket = null
        reader = null
        writer = null
    }

    fun close() {
        synchronized(lock) {
            closeLocked()
        }
    }

    fun probe(host: String, port: Int, token: String): RpcConnectionProbe {
        if (token.isBlank()) {
            return RpcConnectionProbe(ok = false, error = "请先填写连点器 RPC Access Token")
        }
        synchronized(lock) {
            val t0 = System.nanoTime()
            return try {
                ensureConnectedLocked(host, port, 1800)
                val w = writer ?: throw IllegalStateException("Writer unavailable")
                val r = reader ?: throw IllegalStateException("Reader unavailable")
                val req = JSONObject().put("cmd", "status").put("token", token.trim()).toString()
                w.write(req)
                w.write("\n")
                w.flush()
                val line = r.readLine() ?: throw IllegalStateException("连点器断开连接")
                val dt = (System.nanoTime() - t0) / 1_000_000L
                val resp = JSONObject(line)
                if (!resp.optBoolean("ok", false)) {
                    val err = resp.optString("error", "unknown_error")
                    RpcConnectionProbe(ok = false, latencyMs = dt, error = mapRpcError(err))
                } else {
                    RpcConnectionProbe(
                        ok = true,
                        shizukuConnected = resp.optBoolean("shizukuConnected", false),
                        engineMode = resp.optInt("engineMode", 0),
                        overlayVisible = resp.optBoolean("overlayVisible", false),
                        latencyMs = dt
                    )
                }
            } catch (e: SocketTimeoutException) {
                closeLocked()
                RpcConnectionProbe(
                    ok = false,
                    error = "连接超时：连点器可能被系统冻结，请开启连点器悬浮窗或将省电策略设为「无限制」"
                )
            } catch (t: Throwable) {
                closeLocked()
                RpcConnectionProbe(
                    ok = false,
                    error = "连接失败 (${t.message ?: "请确认连点器已开启极客模式与 RPC 桥"})"
                )
            }
        }
    }

    /**
     * Dispatches all taps of a single chord in one pipelined write+flush.
     */
    fun sendTapBatch(host: String, port: Int, token: String, taps: List<RpcTapCmd>): Result<Unit> {
        if (taps.isEmpty()) return Result.success(Unit)
        if (token.isBlank()) return Result.failure(IllegalStateException("未配置 RPC Token"))
        val cleanToken = token.trim()
        synchronized(lock) {
            return try {
                ensureConnectedLocked(host, port, 1500)
                val w = writer ?: throw IllegalStateException("Writer unavailable")
                val r = reader ?: throw IllegalStateException("Reader unavailable")

                val sb = StringBuilder(taps.size * 84)
                for (tap in taps) {
                    sb.append("{\"cmd\":\"tap\",\"token\":\"")
                        .append(escapeJson(cleanToken))
                        .append("\",\"x\":")
                        .append(tap.x)
                        .append(",\"y\":")
                        .append(tap.y)
                        .append(",\"durationMs\":")
                        .append(tap.durationMs.coerceIn(8L, 10_000L))
                        .append("}\n")
                }
                w.write(sb.toString())
                w.flush()

                var firstError = ""
                for (i in taps.indices) {
                    val line = r.readLine() ?: throw IllegalStateException("Socket EOF")
                    val resp = JSONObject(line)
                    if (!resp.optBoolean("ok", false) && firstError.isEmpty()) {
                        firstError = resp.optString("error", "rpc_failed")
                    }
                }
                if (firstError.isNotEmpty()) {
                    Result.failure(IllegalStateException(mapRpcError(firstError)))
                } else {
                    Result.success(Unit)
                }
            } catch (e: SocketTimeoutException) {
                closeLocked()
                Result.failure(IllegalStateException("RPC 响应超时（请保持连点器悬浮窗或后台无限制运行）"))
            } catch (t: Throwable) {
                closeLocked()
                Result.failure(t)
            }
        }
    }

    private fun mapRpcError(code: String): String = when (code) {
        "unauthorized" -> "Access Token 校验失败，请重新复制连点器实验室中的 Token"
        "rpc_bridge_disabled" -> "连点器「极客模式」或「RPC 接口桥」未开启"
        "shizuku_not_connected" -> "连点器 Shizuku 触控引擎未连接"
        else -> "RPC 错误: $code"
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")
}
