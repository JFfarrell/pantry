package com.pantry.app

import com.pantry.app.importer.Iso8601Duration
import com.pantry.app.seasonality.SeasonStatus
import com.pantry.app.seasonality.Seasonality
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DurationAndSeasonTest {

    @Test
    fun `parses iso durations`() {
        assertEquals(90, Iso8601Duration.toMinutes("PT1H30M"))
        assertEquals(25, Iso8601Duration.toMinutes("PT25M"))
        assertEquals(1440, Iso8601Duration.toMinutes("P1D"))
    }

    @Test
    fun `falls back to prose durations`() {
        assertEquals(90, Iso8601Duration.toMinutes("1 hr 30 mins"))
        assertEquals(45, Iso8601Duration.toMinutes("45 minutes"))
    }

    @Test
    fun `formats for display`() {
        assertEquals("25 min", Iso8601Duration.format(25))
        assertEquals("2 hr", Iso8601Duration.format(120))
        assertEquals("1 hr 15 min", Iso8601Duration.format(75))
    }

    @Test
    fun `asparagus is in season in May and out in November`() {
        assertEquals(
            SeasonStatus.IN_SEASON,
            Seasonality.advise("asparagus", LocalDate.of(2026, 5, 10)).status
        )
        assertEquals(
            SeasonStatus.OUT_OF_SEASON,
            Seasonality.advise("asparagus spears", LocalDate.of(2026, 11, 10)).status
        )
    }

    @Test
    fun `the month either side of a season is a shoulder`() {
        assertEquals(
            SeasonStatus.SHOULDER,
            Seasonality.advise("asparagus", LocalDate.of(2026, 3, 10)).status
        )
    }

    @Test
    fun `store cupboard items are not second-guessed`() {
        assertEquals(SeasonStatus.UNKNOWN, Seasonality.advise("plain flour").status)
    }
}
