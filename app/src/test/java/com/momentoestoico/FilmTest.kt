package com.momentoestoico

import com.momentoestoico.share.MomentFilm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmTest {
    @Test
    fun dampedSpringStartsStillAndSettlesWithoutBigOvershoot() {
        assertEquals(0f, MomentFilm.damped(0f), 1e-6f)
        // Arranca despacio (sin tirón)
        assertTrue(MomentFilm.damped(0.05f) < 0.02f)
        val samples = (0..200).map { MomentFilm.damped(it / 100f) }
        assertTrue("rebote ${samples.max()}", samples.max() < 1.02f)
        assertEquals(1f, MomentFilm.damped(MomentFilm.MORPH_END - MomentFilm.MORPH_START), 0.01f)
    }

    @Test
    fun timelineLeavesTenSecondsToReadWithinTwenty() {
        assertTrue(MomentFilm.DURATION <= 20f)
        // La cita está casi del todo visible medio segundo después de empezar a aparecer
        assertTrue(MomentFilm.DURATION - (MomentFilm.TEXT_START + 0.5f) >= 10f)
        assertTrue(MomentFilm.INHALE_START < MomentFilm.EXHALE_START && MomentFilm.EXHALE_START < MomentFilm.MORPH_START)
    }
}
