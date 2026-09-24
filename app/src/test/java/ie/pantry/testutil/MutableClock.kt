package ie.pantry.testutil

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A [Clock] whose instant only moves when a test says so. */
class MutableClock(
    start: Instant = Instant.parse("2026-01-01T00:00:00Z"),
    private val zone: ZoneId = ZoneOffset.UTC,
) : Clock() {

    private var now: Instant = start

    fun advanceBy(duration: Duration) {
        now = now.plus(duration)
    }

    fun set(instant: Instant) {
        now = instant
    }

    override fun instant(): Instant = now

    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
}
