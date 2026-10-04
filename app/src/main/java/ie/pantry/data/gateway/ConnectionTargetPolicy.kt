package ie.pantry.data.gateway

import java.net.InetAddress
import java.net.InetSocketAddress

/** What [ConnectionTargetPolicy.classifyTarget] concluded about the scheme of a raw URL string. */
internal enum class TargetVerdict { SchemeRefused, NoScheme, HttpScheme }

/**
 * Decides, in pure Kotlin, whether a URL's scheme and a socket address may be connected to.
 *
 * [exemptTargets] are exact (address, port) pairs let through the blocked-range check, which is how tests reach
 * a loopback MockWebServer. Only the gateway pairs a policy with exemptions; production uses [STRICT].
 */
internal class ConnectionTargetPolicy(private val exemptTargets: Set<InetSocketAddress> = emptySet()) {

    private val exemptAddresses: Set<InetAddress> = exemptTargets.mapNotNull { it.address }.toSet()

    /**
     * Reads an RFC 3986 scheme (`ALPHA *(ALPHA / DIGIT / "+" / "-" / ".") ":"` before any `/ ? #`). Total: never
     * throws, whatever the input.
     */
    fun classifyTarget(raw: String): TargetVerdict {
        val end = raw.indexOfFirst { it == ':' || it == '/' || it == '?' || it == '#' }
        if (end <= 0 || raw[end] != ':') return TargetVerdict.NoScheme
        val scheme = raw.substring(0, end)
        if (!scheme[0].isAsciiLetter() || !scheme.all { it.isAsciiLetter() || it in '0'..'9' || it in "+-." }) {
            return TargetVerdict.NoScheme
        }
        return if (scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)) {
            TargetVerdict.HttpScheme
        } else {
            TargetVerdict.SchemeRefused
        }
    }

    /** A byte-prefix CIDR match on the address bytes, so it does not depend on how the address was written. */
    fun isBlocked(address: InetAddress): Boolean {
        val bytes = address.address
        return when (bytes.size) {
            4 -> isBlockedIpv4(bytes)
            16 -> if (isIpv4Mapped(bytes)) isBlockedIpv4(bytes.copyOfRange(12, 16)) else isBlockedIpv6(bytes)
            else -> true
        }
    }

    /** The socket-level check: a blocked address is allowed only when its exact address and port are exempt. */
    fun permits(address: InetAddress, port: Int): Boolean =
        !isBlocked(address) || InetSocketAddress(address, port) in exemptTargets

    /** The Dns-level pre-filter. It exempts by address only; the socket guard enforces the port. */
    fun permitsForResolution(address: InetAddress): Boolean = !isBlocked(address) || address in exemptAddresses

    private fun isBlockedIpv4(b: ByteArray): Boolean {
        val first = b[0].toInt() and 0xff
        val second = b[1].toInt() and 0xff
        return first == 0 || // 0.0.0.0/8
            first == 10 || // 10.0.0.0/8
            (first == 100 && second in 64..127) || // 100.64.0.0/10
            first == 127 || // 127.0.0.0/8
            (first == 169 && second == 254) || // 169.254.0.0/16
            (first == 172 && second in 16..31) || // 172.16.0.0/12
            (first == 192 && second == 168) || // 192.168.0.0/16
            first in 224..239 // 224.0.0.0/4
    }

    private fun isBlockedIpv6(b: ByteArray): Boolean {
        val first = b[0].toInt() and 0xff
        val second = b[1].toInt() and 0xff
        val allZeroButLast = (0 until 15).all { b[it].toInt() == 0 }
        return (allZeroButLast && (b[15].toInt() == 0 || b[15].toInt() == 1)) || // ::/128 and ::1/128
            (first and 0xfe) == 0xfc || // fc00::/7
            (first == 0xfe && (second and 0xc0) == 0x80) || // fe80::/10
            (first == 0xfe && (second and 0xc0) == 0xc0) || // fec0::/10
            first == 0xff // ff00::/8
    }

    private fun isIpv4Mapped(b: ByteArray): Boolean =
        (0 until 10).all { b[it].toInt() == 0 } && b[10] == 0xff.toByte() && b[11] == 0xff.toByte()

    private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

    companion object {
        val STRICT: ConnectionTargetPolicy = ConnectionTargetPolicy()
    }
}
