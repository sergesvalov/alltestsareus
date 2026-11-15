package com.alltestsareus.app.domain.srs

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Оценка качества ответа пользователя для интервального повторения (алгоритм SM-2).
 */
enum class ReviewGrade(val quality: Int) {
    AGAIN(1), // Ошибка / Забыл -> сброс интервала на 1 день
    HARD(3),  // Вспомнил с трудом
    GOOD(4),  // Ответил верно
    EASY(5)   // Ответил мгновенно
}

data class SrsState(
    val easinessFactor: Float = 2.5f,
    val intervalDays: Int = 0,
    val repetitions: Int = 0,
    val nextReviewEpochMs: Long = 0L
)

object SpacedRepetitionEngine {

    private const val DAY_MS = 24L * 60L * 60L * 1000L

    /**
     * Вычисляет новую дату повторения и интервал для понятия (Concept)
     * на основе классического алгоритма SuperMemo SM-2.
     */
    fun calculateNextReview(
        current: SrsState,
        grade: ReviewGrade,
        nowEpochMs: Long = System.currentTimeMillis()
    ): SrsState {
        val q = grade.quality
        val newRepetitions: Int
        val newIntervalDays: Int

        if (q < 3) {
            // При ошибке сбрасываем серию и повторяем снова через 10 минут (в рамках дня = 0 дней)
            newRepetitions = 0
            newIntervalDays = 0
        } else {
            newRepetitions = current.repetitions + 1
            newIntervalDays = when (newRepetitions) {
                1 -> 1
                2 -> 3
                else -> (current.intervalDays * current.easinessFactor).roundToInt().coerceAtLeast(4)
            }
        }

        // Обновляем коэффициент лёгкости EF' = EF + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))
        val delta = 0.1f - (5 - q) * (0.08f + (5 - q) * 0.02f)
        val newEf = max(1.3f, current.easinessFactor + delta)

        val nextReviewMs = if (newIntervalDays == 0) {
            // Если ошибся — показать снова в сегодняшней сессии
            nowEpochMs
        } else {
            nowEpochMs + newIntervalDays * DAY_MS
        }

        return SrsState(
            easinessFactor = newEf,
            intervalDays = newIntervalDays,
            repetitions = newRepetitions,
            nextReviewEpochMs = nextReviewMs
        )
    }
}

