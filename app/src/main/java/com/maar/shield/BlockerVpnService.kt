package com.maar.shield

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

class BlockerVpnService : VpnService() {
    companion object { const val ACTION_STOP = "com.maar.shield.STOP" }

    private var tun: ParcelFileDescriptor? = null
    @Volatile private var running = false
    private var worker: Thread? = null
    private val upstream = InetAddress.getByName("1.1.1.3") // Cloudflare family filter (adult content blocked)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn(); stopSelf(); return START_NOT_STICKY
        }
        if (!running) startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        val fd = Builder()
            .setSession("MAAR Shield")
            .addAddress("10.111.0.2", 32)
            .addDnsServer("10.111.0.1")
            .addRoute("10.111.0.1", 32)   // only DNS goes through us; everything else is normal
            .setBlocking(true)
            .establish() ?: return
        tun = fd
        running = true
        worker = Thread { loop(fd) }.also { it.start() }
    }

    private fun stopVpn() {
        running = false
        try { tun?.close() } catch (_: Exception) {}
        tun = null
    }

    override fun onRevoke() { stopVpn(); super.onRevoke() }
    override fun onDestroy() { stopVpn(); super.onDestroy() }

    private fun loop(fd: ParcelFileDescriptor) {
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buf = ByteArray(32767)
        val pool = Executors.newFixedThreadPool(8)
        try {
            while (running) {
                val n = input.read(buf)
                if (n < 0) break
                if (n < 28) continue
                if ((buf[0].toInt() shr 4) != 4) continue
                val ihl = (buf[0].toInt() and 0x0F) * 4
                if (buf[9].toInt() != 17 || n < ihl + 8) continue
                val dport = ((buf[ihl + 2].toInt() and 0xFF) shl 8) or (buf[ihl + 3].toInt() and 0xFF)
                if (dport != 53) continue
                val pkt = buf.copyOf(n)
                pool.execute { handle(pkt, ihl, output) }
            }
        } catch (_: Exception) {
        } finally { pool.shutdownNow() }
    }

    private fun handle(pkt: ByteArray, ihl: Int, out: FileOutputStream) {
        try {
            val udpLen = ((pkt[ihl + 4].toInt() and 0xFF) shl 8) or (pkt[ihl + 5].toInt() and 0xFF)
            val end = minOf(ihl + udpLen, pkt.size)
            val dns = pkt.copyOfRange(ihl + 8, end)
            val name = DnsFilter.qname(dns)
            val resp = if (name != null && DnsFilter.isBlocked(name)) DnsFilter.nxdomain(dns) else forward(dns)
            if (resp != null) {
                val reply = buildPacket(pkt, ihl, resp)
                synchronized(out) { out.write(reply) }
            }
        } catch (_: Exception) {}
    }

    private fun forward(dns: ByteArray): ByteArray? {
        val s = DatagramSocket()
        return try {
            protect(s) // keep our own upstream traffic out of the tunnel
            s.soTimeout = 4000
            s.send(DatagramPacket(dns, dns.size, upstream, 53))
            val b = ByteArray(4096)
            val p = DatagramPacket(b, b.size)
            s.receive(p)
            b.copyOf(p.length)
        } catch (_: Exception) { null } finally { s.close() }
    }

    private fun buildPacket(req: ByteArray, ihl: Int, dns: ByteArray): ByteArray {
        val total = ihl + 8 + dns.size
        val o = ByteArray(total)
        System.arraycopy(req, 0, o, 0, ihl + 8)
        System.arraycopy(req, 12, o, 16, 4); System.arraycopy(req, 16, o, 12, 4)
        System.arraycopy(req, ihl, o, ihl + 2, 2); System.arraycopy(req, ihl + 2, o, ihl, 2)
        o[2] = (total shr 8).toByte(); o[3] = total.toByte()
        o[8] = 64
        o[10] = 0; o[11] = 0
        val udp = 8 + dns.size
        o[ihl + 4] = (udp shr 8).toByte(); o[ihl + 5] = udp.toByte()
        o[ihl + 6] = 0; o[ihl + 7] = 0
        System.arraycopy(dns, 0, o, ihl + 8, dns.size)
        var sum = 0
        var i = 0
        while (i < ihl) { sum += ((o[i].toInt() and 0xFF) shl 8) or (o[i + 1].toInt() and 0xFF); i += 2 }
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        val ck = sum.inv() and 0xFFFF
        o[10] = (ck shr 8).toByte(); o[11] = ck.toByte()
        return o
    }
}
