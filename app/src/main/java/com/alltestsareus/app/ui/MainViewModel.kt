package com.alltestsareus.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alltestsareus.app.BuildConfig
import com.alltestsareus.app.audio.CourseAudioManager
import com.alltestsareus.app.data.local.AppDatabase
import com.alltestsareus.app.data.local.UserConceptProgressEntity
import com.alltestsareus.app.data.repository.LearningRepository
import com.alltestsareus.app.data.repository.SharedCatalogBrowseResult
import com.alltestsareus.app.domain.srs.ReviewGrade
import com.alltestsareus.app.model.CatalogCourseRefDto
import com.alltestsareus.app.model.ConceptDto
import com.alltestsareus.app.model.CourseDto
import com.alltestsareus.app.model.LessonDto
import com.alltestsareus.app.model.QuestionDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class QuizQueueItem(
    val courseId: String,
    val category: String,
    val speechLang: String?,
    val concept: ConceptDto,
    val question: QuestionDto
)

data class UiState(
    val allCourses: List<CourseDto> = emptyList(),
    val selectedCategoryFilter: String = BuildConfig.FLAVOR_CATEGORY,
    val progressByConcept: Map<String, UserConceptProgressEntity> = emptyMap(),
    val activeCourse: CourseDto? = null,
    val activeLesson: LessonDto? = null,
    val activeQuizQueue: List<QuizQueueItem> = emptyList(),
    val preferDeviceTts: Boolean = false,
    val sharedResourceUrl: String = BuildConfig.DEFAULT_WEB_CATALOG_URL,
    val sharedCatalogResult: SharedCatalogBrowseResult? = null,
    val importStatusMessage: String? = null,
    val isImporting: Boolean = false
) {
    val filteredCourses: List<CourseDto>
        get() = if (selectedCategoryFilter == "all") {
            allCourses
        } else {
            allCourses.filter { it.category == selectedCategoryFilter }
        }

    fun getDueReviewItems(nowMs: Long = System.currentTimeMillis()): List<QuizQueueItem> {
        val items = mutableListOf<QuizQueueItem>()
        for (course in filteredCourses) {
            for (lesson in course.lessons) {
                for (concept in lesson.concepts) {
                    val progress = progressByConcept[concept.conceptId]
                    val isDue = progress == null || progress.nextReviewEpochMs <= nowMs
                    if (isDue && concept.questions.isNotEmpty()) {
                        items.add(
                            QuizQueueItem(
                                courseId = course.courseId,
                                category = course.category,
                                speechLang = course.speechLang,
                                concept = concept,
                                question = concept.questions.first()
                            )
                        )
                    }
                }
            }
        }
        return items
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val audioManager = CourseAudioManager(application)
    private val repository: LearningRepository

    private val selectedCategoryFilter = MutableStateFlow(BuildConfig.FLAVOR_CATEGORY)
    private val activeCourse = MutableStateFlow<CourseDto?>(null)
    private val activeLesson = MutableStateFlow<LessonDto?>(null)
    private val activeQuizQueue = MutableStateFlow<List<QuizQueueItem>>(emptyList())
    private val preferDeviceTts = MutableStateFlow(false)
    private val sharedResourceUrl = MutableStateFlow(BuildConfig.DEFAULT_WEB_CATALOG_URL)
    private val sharedCatalogResult = MutableStateFlow<SharedCatalogBrowseResult?>(null)
    private val importStatusMessage = MutableStateFlow<String?>(null)
    private val isImporting = MutableStateFlow(false)

    val uiState: StateFlow<UiState>

    init {
        val db = AppDatabase.getInstance(application)
        repository = LearningRepository(application, db.appDao(), audioManager)
        sharedResourceUrl.value = repository.getSharedResourceUrl()

        val baseFlow = combine(
            repository.observeCoursesForCurrentFlavor(),
            repository.observeConceptProgress(),
            selectedCategoryFilter,
            activeCourse,
            activeLesson
        ) { courses, progressMap, catFilter, course, lesson ->
            UiState(
                allCourses = courses,
                selectedCategoryFilter = catFilter,
                progressByConcept = progressMap,
                activeCourse = course,
                activeLesson = lesson
            )
        }

        val remoteFlow = combine(
            activeQuizQueue,
            preferDeviceTts,
            sharedResourceUrl,
            sharedCatalogResult,
            importStatusMessage,
            isImporting
        ) { arr ->
            @Suppress("UNCHECKED_CAST")
            RemoteUiSlice(
                queue = arr[0] as List<QuizQueueItem>,
                preferTts = arr[1] as Boolean,
                sharedUrl = arr[2] as String,
                catalogRes = arr[3] as SharedCatalogBrowseResult?,
                msg = arr[4] as String?,
                importing = arr[5] as Boolean
            )
        }

        uiState = combine(baseFlow, remoteFlow) { base, remote ->
            base.copy(
                activeQuizQueue = remote.queue,
                preferDeviceTts = remote.preferTts,
                sharedResourceUrl = remote.sharedUrl,
                sharedCatalogResult = remote.catalogRes,
                importStatusMessage = remote.msg,
                isImporting = remote.importing
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState()
        )
    }

    private data class RemoteUiSlice(
        val queue: List<QuizQueueItem>,
        val preferTts: Boolean,
        val sharedUrl: String,
        val catalogRes: SharedCatalogBrowseResult?,
        val msg: String?,
        val importing: Boolean
    )

    override fun onCleared() {
        super.onCleared()
        audioManager.release()
    }

    fun selectCategoryFilter(category: String) {
        if (BuildConfig.FLAVOR_CATEGORY == "all") {
            selectedCategoryFilter.value = category
        }
    }

    fun openCourse(course: CourseDto) {
        activeCourse.value = course
        activeLesson.value = course.lessons.firstOrNull()
    }

    fun selectLesson(lesson: LessonDto) {
        activeLesson.value = lesson
    }

    fun closeCourse() {
        activeCourse.value = null
        activeLesson.value = null
    }

    fun deleteCourse(courseId: String) {
        viewModelScope.launch {
            repository.removeCourseFromDevice(courseId)
            closeCourse()
        }
    }

    fun togglePreferDeviceTts() {
        preferDeviceTts.value = !preferDeviceTts.value
    }

    fun playAudio(localAudioPath: String?, audioText: String?, speechLang: String?) {
        audioManager.playSpeech(
            localAudioPath = localAudioPath,
            audioText = audioText,
            langTag = speechLang,
            preferDeviceTts = preferDeviceTts.value
        )
    }

    fun startLessonQuiz(course: CourseDto, lesson: LessonDto) {
        val queue = lesson.concepts.flatMap { concept ->
            concept.questions.map { question ->
                QuizQueueItem(
                    courseId = course.courseId,
                    category = course.category,
                    speechLang = course.speechLang,
                    concept = concept,
                    question = question
                )
            }
        }
        activeQuizQueue.value = queue
    }

    fun startDailySmartReview() {
        activeQuizQueue.value = uiState.value.getDueReviewItems()
    }

    fun exitQuiz() {
        activeQuizQueue.value = emptyList()
    }

    fun submitAnswerGrade(item: QuizQueueItem, grade: ReviewGrade) {
        viewModelScope.launch {
            repository.recordConceptReview(
                courseId = item.courseId,
                category = item.category,
                conceptId = item.concept.conceptId,
                term = item.concept.term,
                grade = grade
            )
        }
    }

    fun browseSharedCatalog(url: String) {
        viewModelScope.launch {
            isImporting.value = true
            importStatusMessage.value = null
            sharedResourceUrl.value = url.trim()
            val res = repository.browseSharedResourceCatalog(url)
            isImporting.value = false
            res.fold(
                onSuccess = { catalog ->
                    sharedCatalogResult.value = catalog
                    importStatusMessage.value = "✅ Загружен каталог «${catalog.catalogName}». Доступно курсов: ${catalog.items.size}"
                },
                onFailure = { err ->
                    importStatusMessage.value = "❌ Не удалось загрузить каталог с сайта: ${err.localizedMessage ?: err.javaClass.simpleName}"
                }
            )
        }
    }

    fun installCourseFromSharedCatalog(ref: CatalogCourseRefDto) {
        viewModelScope.launch {
            isImporting.value = true
            importStatusMessage.value = null
            val res = repository.installCourseFromUrl(ref.url)
            isImporting.value = false
            res.fold(
                onSuccess = { course ->
                    importStatusMessage.value = "✅ Тест «${course.title}» и озвучка скачаны на телефон!"
                    browseSharedCatalog(sharedResourceUrl.value)
                },
                onFailure = { err ->
                    importStatusMessage.value = "❌ Ошибка скачивания «${ref.title}»: ${err.localizedMessage ?: err.javaClass.simpleName}"
                }
            )
        }
    }

    fun syncAllFromSharedResource(url: String) {
        viewModelScope.launch {
            isImporting.value = true
            importStatusMessage.value = null
            sharedResourceUrl.value = url.trim()
            val res = repository.syncAllFromSharedResource(url)
            isImporting.value = false
            res.fold(
                onSuccess = { count ->
                    importStatusMessage.value = "✅ Скачано курсов с озвучкой: $count"
                    repository.browseSharedResourceCatalog(url).onSuccess {
                        sharedCatalogResult.value = it
                    }
                },
                onFailure = { err ->
                    importStatusMessage.value = "❌ Ошибка скачивания: ${err.localizedMessage ?: err.javaClass.simpleName}"
                }
            )
        }
    }

    fun importFromWebPageUrl(url: String) {
        viewModelScope.launch {
            isImporting.value = true
            importStatusMessage.value = null
            val res = repository.importCoursesFromWebUrl(url)
            isImporting.value = false
            importStatusMessage.value = res.fold(
                onSuccess = { count -> "✅ Успешно загружено курсов с веб-страницы: $count" },
                onFailure = { err -> "❌ Ошибка загрузки: ${err.localizedMessage ?: err.javaClass.simpleName}" }
            )
        }
    }

    fun clearImportStatus() {
        importStatusMessage.value = null
    }
}
