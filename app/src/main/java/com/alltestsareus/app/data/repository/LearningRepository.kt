package com.alltestsareus.app.data.repository

import android.content.Context
import com.alltestsareus.app.BuildConfig
import com.alltestsareus.app.audio.CourseAudioManager
import com.alltestsareus.app.data.local.AppDao
import com.alltestsareus.app.data.local.CourseEntity
import com.alltestsareus.app.data.local.UserConceptProgressEntity
import com.alltestsareus.app.data.remote.WebCourseImporter
import com.alltestsareus.app.domain.srs.ReviewGrade
import com.alltestsareus.app.domain.srs.SpacedRepetitionEngine
import com.alltestsareus.app.domain.srs.SrsState
import com.alltestsareus.app.model.CatalogCourseRefDto
import com.alltestsareus.app.model.CourseDto
import com.alltestsareus.app.model.LessonDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

data class SharedCourseStatus(
    val ref: CatalogCourseRefDto,
    val installedVersion: Int?
) {
    val isInstalled: Boolean get() = installedVersion != null
    val hasUpdate: Boolean get() = installedVersion != null && ref.version > installedVersion
}

data class SharedCatalogBrowseResult(
    val catalogName: String,
    val updatedAt: String,
    val items: List<SharedCourseStatus>
)

class LearningRepository(
    context: Context,
    private val dao: AppDao,
    private val audioManager: CourseAudioManager,
    private val webImporter: WebCourseImporter = WebCourseImporter()
) {
    private val json = WebCourseImporter.SharedJson
    private val prefs = context.getSharedPreferences("alltests_prefs", Context.MODE_PRIVATE)

    fun getSharedResourceUrl(): String {
        return prefs.getString("shared_catalog_url", BuildConfig.DEFAULT_WEB_CATALOG_URL)
            ?: BuildConfig.DEFAULT_WEB_CATALOG_URL
    }

    fun saveSharedResourceUrl(url: String) {
        prefs.edit().putString("shared_catalog_url", url.trim()).apply()
    }

    fun observeCoursesForCurrentFlavor(): Flow<List<CourseDto>> {
        val flavorCat = BuildConfig.FLAVOR_CATEGORY
        val entityFlow = if (flavorCat == "all") {
            dao.observeAllCourses()
        } else {
            dao.observeCoursesByCategory(flavorCat)
        }
        return entityFlow
            .map { list -> list.map { it.toDto() } }
            .flowOn(Dispatchers.Default)
    }

    fun observeConceptProgress(): Flow<Map<String, UserConceptProgressEntity>> {
        return dao.observeAllConceptProgress()
            .map { list -> list.associateBy { it.conceptId } }
            .flowOn(Dispatchers.Default)
    }

    suspend fun browseSharedResourceCatalog(catalogUrl: String): Result<SharedCatalogBrowseResult> {
        saveSharedResourceUrl(catalogUrl)
        return webImporter.fetchCatalogManifest(catalogUrl).map { resolved ->
            val relevantCourses = filterForFlavor(resolved.resolvedCourses)
            val localVersions = withContext(Dispatchers.IO) {
                dao.getAllCourseVersions().associate { it.courseId to it.version }
            }
            val items = relevantCourses.map { ref ->
                SharedCourseStatus(ref = ref, installedVersion = localVersions[ref.courseId])
            }
            SharedCatalogBrowseResult(
                catalogName = resolved.manifest.catalogName,
                updatedAt = resolved.manifest.updatedAt,
                items = items
            )
        }
    }

    /**
     * Скачивает курс вместе со всеми его аудиофайлами на телефон и сохраняет в Room.
     */
    suspend fun installCourseFromUrl(courseUrl: String): Result<CourseDto> {
        return webImporter.fetchSingleCourseFromUrl(courseUrl).mapCatching { course ->
            val withLocalAudio = audioManager.downloadCourseAudio(course)
            withContext(Dispatchers.IO) {
                dao.upsertCourse(withLocalAudio.toEntity())
            }
            withLocalAudio
        }
    }

    suspend fun syncAllFromSharedResource(catalogUrl: String): Result<Int> {
        saveSharedResourceUrl(catalogUrl)
        val manifestResult = webImporter.fetchCatalogManifest(catalogUrl)
        return manifestResult.mapCatching { resolved ->
            val targetRefs = filterForFlavor(resolved.resolvedCourses)
            val localVersions = withContext(Dispatchers.IO) {
                dao.getAllCourseVersions().associate { it.courseId to it.version }
            }

            val refsToDownload = targetRefs.filter { ref ->
                val installed = localVersions[ref.courseId]
                installed == null || ref.version > installed
            }
            if (refsToDownload.isEmpty()) return@mapCatching 0

            val downloadedCourses = coroutineScope {
                refsToDownload.map { ref ->
                    async {
                        val course = webImporter.fetchSingleCourseFromUrl(ref.url).getOrThrow()
                        audioManager.downloadCourseAudio(course)
                    }
                }.awaitAll()
            }

            withContext(Dispatchers.IO) {
                dao.upsertCourses(downloadedCourses.map { it.toEntity() })
            }
            downloadedCourses.size
        }
    }

    suspend fun importCoursesFromWebUrl(url: String): Result<Int> {
        return webImporter.fetchCoursesFromWebUrl(url).mapCatching { courses ->
            val withAudio = coroutineScope {
                courses.map { course -> async { audioManager.downloadCourseAudio(course) } }.awaitAll()
            }
            withContext(Dispatchers.IO) {
                dao.upsertCourses(withAudio.map { it.toEntity() })
            }
            withAudio.size
        }
    }

    suspend fun removeCourseFromDevice(courseId: String) = withContext(Dispatchers.IO) {
        dao.deleteCourseById(courseId)
        audioManager.deleteCourseAudio(courseId)
    }

    suspend fun recordConceptReview(
        courseId: String,
        category: String,
        conceptId: String,
        term: String,
        grade: ReviewGrade
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = dao.getConceptProgress(conceptId)
        val currentState = if (existing != null) {
            SrsState(
                easinessFactor = existing.easinessFactor,
                intervalDays = existing.intervalDays,
                repetitions = existing.repetitions,
                nextReviewEpochMs = existing.nextReviewEpochMs
            )
        } else {
            SrsState()
        }

        val nextState = SpacedRepetitionEngine.calculateNextReview(currentState, grade, now)
        dao.upsertConceptProgress(
            UserConceptProgressEntity(
                conceptId = conceptId,
                courseId = courseId,
                category = category,
                term = term,
                easinessFactor = nextState.easinessFactor,
                intervalDays = nextState.intervalDays,
                repetitions = nextState.repetitions,
                nextReviewEpochMs = nextState.nextReviewEpochMs,
                lastReviewedEpochMs = now
            )
        )
    }

    private fun filterForFlavor(refs: List<CatalogCourseRefDto>): List<CatalogCourseRefDto> {
        val flavorCat = BuildConfig.FLAVOR_CATEGORY
        return if (flavorCat == "all") refs else refs.filter { it.category == flavorCat }
    }

    private fun CourseDto.toEntity(): CourseEntity {
        return CourseEntity(
            courseId = courseId,
            category = category,
            version = version,
            title = title,
            description = description,
            author = author,
            sourceWebUrl = sourceWebUrl,
            rawLessonsJson = json.encodeToString(lessons),
            updatedAtEpochMs = System.currentTimeMillis(),
            speechLang = speechLang
        )
    }

    private fun CourseEntity.toDto(): CourseDto {
        val parsedLessons = runCatching {
            json.decodeFromString<List<LessonDto>>(rawLessonsJson)
        }.getOrDefault(emptyList())

        return CourseDto(
            courseId = courseId,
            category = category,
            version = version,
            title = title,
            description = description,
            author = author,
            sourceWebUrl = sourceWebUrl,
            speechLang = speechLang,
            lessons = parsedLessons
        )
    }
}
