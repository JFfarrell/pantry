package ie.pantry.data.gateway

import ie.pantry.testutil.RepoPaths
import java.io.File
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** R2 AC1 and AC4 (scheme part), R3 AC1 and AC2: pure JVM tests of [ConnectionTargetPolicy]. */
class ConnectionTargetPolicyTest {

    private val policy = ConnectionTargetPolicy.STRICT

    private fun ip(literal: String): InetAddress = InetAddress.getByName(literal)

    /** An IPv4-mapped IPv6 address built from raw bytes, since `getByName` turns these into `Inet4Address`. */
    private fun mapped(a: Int, b: Int, c: Int, d: Int): Inet6Address {
        val bytes = ByteArray(16)
        bytes[10] = 0xff.toByte()
        bytes[11] = 0xff.toByte()
        bytes[12] = a.toByte()
        bytes[13] = b.toByte()
        bytes[14] = c.toByte()
        bytes[15] = d.toByte()
        return Inet6Address.getByAddress(null, bytes, null)
    }

    private fun assertAllBlocked(vararg literals: String) {
        for (literal in literals) assertTrue(policy.isBlocked(ip(literal)), "$literal must be refused")
    }

    private fun assertAllAllowed(vararg literals: String) {
        for (literal in literals) assertFalse(policy.isBlocked(ip(literal)), "$literal must be allowed")
    }

    @Test
    fun `ipv4 addresses inside every blocked range are refused`() {
        assertAllBlocked(
            "127.0.0.1", "127.255.255.254", "169.254.169.254", "10.0.0.1", "172.16.0.1", "172.31.255.255",
            "192.168.1.1", "0.0.0.0", "0.0.0.1", "100.64.0.1", "100.127.255.254", "224.0.0.1",
        )
    }

    @Test
    fun `ipv4 boundary neighbours outside every blocked range are allowed`() {
        assertAllAllowed(
            "126.255.255.255", "128.0.0.0", "169.253.255.255", "169.255.0.0", "9.255.255.255", "11.0.0.0",
            "172.15.255.255", "172.32.0.0", "192.167.255.255", "192.169.0.0", "100.63.255.255", "100.128.0.0",
            "223.255.255.255", "240.0.0.0", "1.0.0.0",
        )
    }

    @Test
    fun `ipv6 addresses inside every blocked range are refused`() {
        assertAllBlocked(
            "::", "::1", "fc00::", "fc00::1", "fd12:3456::1", "fdff:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
            "fe80::", "fe80::1", "febf:ffff:ffff:ffff:ffff:ffff:ffff:ffff", "fec0::", "fec0::1",
            "feff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", "ff00::", "ff02::1", "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
        )
    }

    @Test
    fun `ipv6 boundary neighbours are allowed`() {
        assertAllAllowed(
            "::2", "fbff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", "fe00::", "fe7f:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
            "2001:4860:4860::8888",
        )
    }

    @Test
    fun `ipv4 mapped forms of blocked addresses are refused`() {
        assertTrue(policy.isBlocked(mapped(127, 0, 0, 1)))
        assertTrue(policy.isBlocked(mapped(10, 0, 0, 1)))
        assertTrue(policy.isBlocked(mapped(169, 254, 169, 254)))
        assertTrue(policy.isBlocked(ip("::ffff:127.0.0.1")))
    }

    @Test
    fun `ipv4 mapped form of an allowed address is allowed`() {
        assertFalse(policy.isBlocked(mapped(126, 255, 255, 255)))
        assertFalse(policy.isBlocked(mapped(8, 8, 8, 8)))
    }

    @Test
    fun `permits refuses a blocked address unless its exact address and port are exempt`() {
        val loopback = ip("127.0.0.1")
        val exempting = ConnectionTargetPolicy(setOf(InetSocketAddress(loopback, 4000)))

        assertFalse(policy.permits(loopback, 4000))
        assertTrue(exempting.permits(loopback, 4000))
        assertFalse(exempting.permits(loopback, 4001))
        assertFalse(exempting.permits(ip("10.0.0.1"), 4000))
        assertTrue(exempting.permits(ip("8.8.8.8"), 4001))
    }

    @Test
    fun `permitsForResolution exempts by address regardless of port`() {
        val loopback = ip("127.0.0.1")
        val exempting = ConnectionTargetPolicy(setOf(InetSocketAddress(loopback, 4000)))

        assertFalse(policy.permitsForResolution(loopback))
        assertTrue(exempting.permitsForResolution(loopback))
        assertFalse(exempting.permitsForResolution(ip("10.0.0.1")))
        assertTrue(exempting.permitsForResolution(ip("8.8.8.8")))
    }

    @Test
    fun `classifyTarget refuses every non http scheme`() {
        for (raw in listOf(
            "intent://evil#Intent;end", "content://ie.pantry.provider/x", "file:///etc/passwd",
            "javascript:alert(1)", "ftp://host/x", "data:text/plain,hi",
        )) {
            assertEquals(TargetVerdict.SchemeRefused, policy.classifyTarget(raw), raw)
        }
    }

    @Test
    fun `classifyTarget accepts http and https case insensitively`() {
        assertEquals(TargetVerdict.HttpScheme, policy.classifyTarget("HTTP://x"))
        assertEquals(TargetVerdict.HttpScheme, policy.classifyTarget("https://x"))
        assertEquals(TargetVerdict.HttpScheme, policy.classifyTarget("HtTpS://x"))
    }

    @Test
    fun `classifyTarget reports no scheme for empty whitespace and malformed input`() {
        for (raw in listOf("", " ", "  \t\n", "ht!tp://x", "/relative/path", "//host/path", "?q=a:b", "#frag:x", "1http://x")) {
            assertEquals(TargetVerdict.NoScheme, policy.classifyTarget(raw), "'$raw'")
        }
    }

    @Test
    fun `policy source imports neither okhttp nor android`() {
        val source = File(RepoPaths.repoRoot(), "app/src/main/java/ie/pantry/data/gateway/ConnectionTargetPolicy.kt")
        val imports = source.readLines().filter { it.trimStart().startsWith("import ") }
        assertTrue(imports.isNotEmpty())
        assertTrue(imports.none { "okhttp3" in it || it.trim().startsWith("import android") }, imports.toString())
    }
}
