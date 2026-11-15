package com.alltestsareus.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey val courseId: String,
    val category: String,
    val version: Int,
    val title: String,
    val description: String,
    val author: String,
    val sourceWebUrl: String,
    val rawLessonsJson: String, // Полный JSON уроков и вопросов для быстрого чтения
    val updatedAtEpochMs: Long
)

@Entity(tableName = "user_concept_progress")
data class UserConceptProgressEntity(
    @PrimaryKey val conceptId: String,
    val courseId: String,
    val category: String,
    val term: String,
    val easinessFactor: Float = 2.5f,
    val intervalDays: Int = 0,
    val repetitions: Int = 0,
    val nextReviewEpochMs: Long = 0L,
    val lastReviewedEpochMs: Long = 0L
)

