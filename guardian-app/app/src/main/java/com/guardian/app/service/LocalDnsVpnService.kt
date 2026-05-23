package com.guardian.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.guardian.app.R
import com.guardian.app.engine.KeywordEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import javax.inject.Inject

/**
 * Local VPN service that intercepts all UDP port-53 DNS packets.
 *
 * Architecture:
 *   App traffic → tun0 (our VPN interface) → we read raw IP packets →
 *   parse DNS query → if domain is blocked → respond with NXDOMAIN →
 *   else forward to upstream DNS (1.1.1.1) → write response back.
 *
 * This gives us DNS-level blocking without root and without a remote server.
 */
@AndroidEntryPoint
class LocalDnsVpnService : VpnService() {

    @Inject lateinit var keywordEngine: KeywordEngine

    private var vpnInterface: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var packetJob: Job? = null

    companion object {
        private const val CHANNEL_ID = "guardian_vpn_channel"
        private const val NOTIF_ID   = 1002
        private const val UPSTREAM_DNS = "1.1.1.1"
        private const val DNS_PORT     = 53
        private const val VPN_ADDRESS  = "10.0.0.2"
        private const val VPN_ROUTE    = "0.0.0.0"

        fun start(context: Context) {
            val intent = prepare(context) ?: return  // null = already prepared
            // If not null, caller must startActivityForResult with VPN_REQUEST_CODE
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        setupVpnInterface()
        startPacketProcessing()
        return START_STICKY
    }

    // ─────────────────────────────────────────────────────────────────
    // VPN interface setup
    // ─────────────────────────────────────────────────────────────────

    private fun setupVpnInterface() {
        vpnInterface = Builder()
            .addAddress(VPN_ADDRESS, 32)
            .addRoute(VPN_ROUTE, 0)
            .addDnsServer(UPSTREAM_DNS)
            .setSession("الحارس VPN")
            .setBlocking(false)
            .establish()
    }

    // ─────────────────────────────────────────────────────────────────
    // Packet processing loop
    // ─────────────────────────────────────────────────────────────────

    private fun startPacketProcessing() {
        val pfd = vpnInterface ?: return
        val inStream  = FileInputStream(pfd.fileDescriptor)
        val outStream = FileOutputStream(pfd.fileDescriptor)

        packetJob = scope.launch {
            val packet = ByteBuffer.allocate(32767)

            while (isActive) {
                packet.clear()
                val length = inStream.channel.read(packet)
                if (length <= 0) continue

                packet.flip()
                val raw = ByteArray(packet.limit())
                packet.get(raw)

                val processed = processPacket(raw)
                if (processed != null) {
                    withContext(Dispatchers.IO) {
                        outStream.write(processed)
                    }
                }
            }
        }
    }

    /**
     * Inspect a raw IP packet.
     * - If it is a DNS query and the queried domain is blocked → inject NXDOMAIN.
     * - Otherwise → forward to upstream DNS and relay the reply.
     *
     * Returns the byte array that should be written back to the tun interface,
     * or null to drop the packet silently.
     */
    private suspend fun processPacket(raw: ByteArray): ByteArray? = withContext(Dispatchers.IO) {
        try {
            // ── Minimal IP header parsing ─────────────────────────────
            if (raw.size < 20) return@withContext null
            val ipVersion = (raw[0].toInt() shr 4) and 0xF
            if (ipVersion != 4) return@withContext forwardRaw(raw)   // only handle IPv4

            val protocol = raw[9].toInt() and 0xFF
            if (protocol != 17) return@withContext forwardRaw(raw)   // only handle UDP

            val ipHeaderLen = (raw[0].toInt() and 0xF) * 4
            if (raw.size < ipHeaderLen + 8) return@withContext null

            val destPort = ((raw[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or
                            (raw[ipHeaderLen + 3].toInt() and 0xFF)

            if (destPort != DNS_PORT) return@withContext forwardRaw(raw)

            // ── Extract DNS payload ───────────────────────────────────
            val udpHeaderLen = 8
            val dnsOffset    = ipHeaderLen + udpHeaderLen
            if (raw.size <= dnsOffset) return@withContext null
            val dnsPayload   = raw.copyOfRange(dnsOffset, raw.size)

            // ── Parse queried domain name ─────────────────────────────
            val domain = parseDnsQueryName(dnsPayload) ?: return@withContext forwardRaw(raw)

            // ── Check if domain is blocked ────────────────────────────
            if (keywordEngine.isBlockedUrl(domain)) {
                return@withContext buildNxdomainResponse(raw, dnsPayload, ipHeaderLen)
            }

            // ── Forward to upstream DNS ───────────────────────────────
            return@withContext forwardDns(raw, dnsPayload, ipHeaderLen)

        } catch (e: Exception) {
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // DNS helpers
    // ─────────────────────────────────────────────────────────────────

    private fun parseDnsQueryName(dns: ByteArray): String? {
        return try {
            val sb = StringBuilder()
            var i = 12  // skip 12-byte DNS header
            while (i < dns.size) {
                val len = dns[i].toInt() and 0xFF
                if (len == 0) break
                if (sb.isNotEmpty()) sb.append('.')
                sb.append(String(dns, i + 1, len, Charsets.US_ASCII))
                i += len + 1
            }
            sb.toString().lowercase()
        } catch (e: Exception) { null }
    }

    private fun buildNxdomainResponse(
        originalPacket: ByteArray,
        dnsQuery: ByteArray,
        ipHeaderLen: Int
    ): ByteArray {
        // Build minimal NXDOMAIN DNS response
        val response = dnsQuery.copyOf()
        if (response.size < 4) return originalPacket
        // Set QR=1 (response), AA=1, RCODE=3 (NXDOMAIN)
        response[2] = 0x84.toByte()
        response[3] = 0x03.toByte()

        // Rebuild the UDP+IP packet with the DNS response
        return rebuildPacket(originalPacket, response, ipHeaderLen, swapSrcDst = true)
    }

    private suspend fun forwardDns(
        originalPacket: ByteArray,
        dnsQuery: ByteArray,
        ipHeaderLen: Int
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val socket = DatagramSocket()
            protect(socket)
            val request = DatagramPacket(
                dnsQuery, dnsQuery.size,
                InetAddress.getByName(UPSTREAM_DNS), DNS_PORT
            )
            socket.send(request)

            val buf = ByteArray(512)
            val reply = DatagramPacket(buf, buf.size)
            socket.soTimeout = 3000
            socket.receive(reply)
            socket.close()

            val dnsResponse = buf.copyOf(reply.length)
            rebuildPacket(originalPacket, dnsResponse, ipHeaderLen, swapSrcDst = true)
        } catch (e: Exception) { null }
    }

    private fun forwardRaw(raw: ByteArray): ByteArray = raw

    private fun rebuildPacket(
        original: ByteArray,
        dnsPayload: ByteArray,
        ipHeaderLen: Int,
        swapSrcDst: Boolean
    ): ByteArray {
        val udpLen = 8 + dnsPayload.size
        val totalLen = ipHeaderLen + udpLen
        val packet = ByteArray(totalLen)

        // Copy IP header
        System.arraycopy(original, 0, packet, 0, ipHeaderLen)
        // Fix total length
        packet[2] = (totalLen shr 8).toByte()
        packet[3] = (totalLen and 0xFF).toByte()

        if (swapSrcDst) {
            // Swap source ↔ destination IP
            System.arraycopy(original, 12, packet, 16, 4)
            System.arraycopy(original, 16, packet, 12, 4)
        }

        // UDP header: swap ports
        packet[ipHeaderLen]     = original[ipHeaderLen + 2]
        packet[ipHeaderLen + 1] = original[ipHeaderLen + 3]
        packet[ipHeaderLen + 2] = original[ipHeaderLen]
        packet[ipHeaderLen + 3] = original[ipHeaderLen + 1]
        packet[ipHeaderLen + 4] = (udpLen shr 8).toByte()
        packet[ipHeaderLen + 5] = (udpLen and 0xFF).toByte()
        packet[ipHeaderLen + 6] = 0  // checksum (optional for IPv4 UDP)
        packet[ipHeaderLen + 7] = 0

        // DNS payload
        System.arraycopy(dnsPayload, 0, packet, ipHeaderLen + 8, dnsPayload.size)

        return packet
    }

    // ─────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────

    override fun onDestroy() {
        packetJob?.cancel()
        vpnInterface?.close()
        super.onDestroy()
    }

    override fun onRevoke() {
        vpnInterface?.close()
        super.onRevoke()
    }

    // ─────────────────────────────────────────────────────────────────
    // Notification
    // ─────────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "الحارس — VPN نشط",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_guardian_shield)
            .setContentTitle("الحارس — الحماية نشطة")
            .setContentText("يتم تصفية المحتوى غير اللائق")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
}
