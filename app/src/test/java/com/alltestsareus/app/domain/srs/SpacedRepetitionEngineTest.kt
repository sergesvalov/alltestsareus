package com.alltestsareus.app.domain.srs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpacedRepetitionEngineTest {

    @Test
    fun `good reviews grow interval from 1 to 3 days and beyond`() {
        val now = 1_700_000_000_000L
        val first = SpacedRepetitionEngine.calculateNextReview(SrsState(), ReviewGrade.GOOD, now)
        assertEquals(1, first.repetitions)
        assertEquals(1, first.intervalDays)

        val second = SpacedRepetitionEngine.calculateNextReview(first, ReviewGrade.GOOD, now)
        assertEquals(2, second.repetitions)
        assertEquals(3, second.intervalDays)

        val third = SpacedRepetitionEngine.calculateNextReview(second, ReviewGrade.GOOD, now)
        assertEquals(3, third.repetitions)
        assertTrue(third.intervalDays >= 4)
    }

    @Test
    fun `again grade resets repetitions and schedules immediate review`() {
        val now = 1_700_000_000_000L
        val learned = SrsState(easinessFactor = 2.5f, intervalDays = 10, repetitions = 4)
        val reset = SpacedRepetitionEngine.calculateNextReview(learned, ReviewGrade.AGAIN, now)

        assertEquals(0, reset.repetitions)
        assertEquals(0, reset.intervalDays)
        assertEquals(now, reset.nextReviewEpochMs)
    }
}

