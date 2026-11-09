package com.maar.shield

object DnsFilter {
    // DNS-over-HTTPS / private DNS hosts blocked so apps fall back to our filtered DNS
    private val bypass = setOf(
        "use-application-dns.net", "dns.google", "dns.google.com", "cloudflare-dns.com",
        "mozilla.cloudflare-dns.com", "one.one.one.one", "dns.quad9.net", "dns.nextdns.io",
        "doh.opendns.com", "dns.adguard.com", "doh.cleanbrowsing.org"
    )

    private val domains = listOf(
        "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "redtube.com", "youporn.com",
        "spankbang.com", "eporner.com", "tube8.com", "porn.com", "brazzers.com", "bangbros.com",
        "chaturbate.com", "stripchat.com", "livejasmin.com", "bongacams.com", "cam4.com",
        "onlyfans.com", "fansly.com", "rule34.xxx", "e-hentai.org", "nhentai.net", "hanime.tv",
        "motherless.com", "thumbzilla.com", "beeg.com", "tnaflix.com", "xtube.com", "fapello.com",
        "erome.com", "myfreecams.com", "camsoda.com", "naughtyamerica.com", "realitykings.com",
        "adultfriendfinder.com", "ashleymadison.com", "pornpics.com", "sex.com", "4chan.org/b"
    )

    private val keywords = listOf(
        "porn", "xxx", "hentai", "xvideos", "xnxx", "xhamster", "nsfw", "camgirl", "sexcam",
        "livesex", "sexchat", "sextube", "freesex", "erotic", "escort", "nudes", "pussy",
        "fuck", "milf", "blowjob", "hotwife", "camwhore", "adultvideo", "rule34"
    )

    fun isBlocked(name: String): Boolean {
        val d = name.lowercase().trimEnd('.')
        if (d in bypass) return true
        if (domains.any { d == it || d.endsWith(".$it") }) return true
        return keywords.any { d.contains(it) }
    }

    fun qname(dns: ByteArray): String? {
        if (dns.size < 13) return null
        var p = 12
        val sb = StringBuilder()
        while (p < dns.size) {
            val len = dns[p].toInt() and 0xFF
            if (len == 0) return sb.toString()
            if (len and 0xC0 != 0 || p + 1 + len > dns.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(dns, p + 1, len, Charsets.ISO_8859_1))
            p += len + 1
        }
        return null
    }

    /** NXDOMAIN reply for the query */
    fun nxdomain(dns: ByteArray): ByteArray {
        var p = 12
        while (p < dns.size && dns[p].toInt() != 0) p += (dns[p].toInt() and 0xFF) + 1
        val end = minOf(p + 5, dns.size)
        val r = dns.copyOf(end)
        r[2] = 0x81.toByte(); r[3] = 0x83.toByte()
        r[4] = 0; r[5] = 1
        for (i in 6..11) r[i] = 0
        return r
    }
}
