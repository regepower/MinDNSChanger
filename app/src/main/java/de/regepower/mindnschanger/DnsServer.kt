package de.regepower.mindnschanger

/** One DNS entry: display name plus primary and optional secondary IPv4 address. */
data class DnsServer(val name: String, val primary: String, val secondary: String?, val custom: Boolean) {
    val addresses: String get() = listOfNotNull(primary, secondary).joinToString(" · ")

    companion object {
        /** Built-in presets (plain IPv4 resolvers). */
        val PRESETS = listOf(
            DnsServer("Cloudflare", "1.1.1.1", "1.0.0.1", false),
            DnsServer("Cloudflare Malware", "1.1.1.2", "1.0.0.2", false),
            DnsServer("Cloudflare Family", "1.1.1.3", "1.0.0.3", false),
            DnsServer("Quad9", "9.9.9.9", "149.112.112.112", false),
            DnsServer("AdGuard", "94.140.14.14", "94.140.15.15", false),
            DnsServer("AdGuard Family", "94.140.14.15", "94.140.15.16", false),
            DnsServer("HaGeZi", "188.34.161.210", "159.69.155.94", false),
            DnsServer("Google", "8.8.8.8", "8.8.4.4", false),
            DnsServer("OpenDNS", "208.67.222.222", "208.67.220.220", false)
        )

        /** Strict dotted-quad check: four decimal parts 0..255, no leading zeros. */
        fun isIpv4(s: String): Boolean {
            val parts = s.split('.')
            if (parts.size != 4) return false
            return parts.all { p ->
                p.isNotEmpty() &&
                    p.length <= 3 &&
                    p.all { it in '0'..'9' } &&
                    (p.length == 1 || p[0] != '0') &&
                    p.toInt() <= 255
            }
        }
    }
}
