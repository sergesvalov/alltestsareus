package com.alltestsareus.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class CourseVersionRow(
    val courseId: String,
    val version: Int
)

@Dao
interface AppDao {

    @Query("SELECT * FROM courses ORDER BY title ASC")
    fun observeAllCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE category = :category ORDER BY title ASC")
    fun observeCoursesByCategory(category: String): Flow<List<CourseEntity>>

    @Query("SELECT courseId, version FROM courses")
    suspend fun getAllCourseVersions(): List<CourseVersionRow>

    @Query("SELECT * FROM courses WHERE courseId = :courseId LIMIT 1")
    suspend fun getCourseById(courseId: String): CourseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCourse(course: CourseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCourses(courses: List<CourseEntity>)

    @Query("DELETE FROM courses WHERE courseId = :courseId")
    suspend fun deleteCourseById(courseId: String)

    @Query("SELECT * FROM user_concept_progress WHERE conceptId = :conceptId LIMIT 1")
    suspend fun getConceptProgress(conceptId: String): UserConceptProgressEntity?

    @Query("SELECT * FROM user_concept_progress")
    fun observeAllConceptProgress(): Flow<List<UserConceptProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConceptProgress(progress: UserConceptProgressEntity)
}
