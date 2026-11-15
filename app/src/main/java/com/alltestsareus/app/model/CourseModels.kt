package com.alltestsareus.app.model

import kotlinx.serialization.Serializable

@Serializable
data class CatalogManifestDto(
    val catalogVersion: Int = 1,
    val updatedAt: String = "",
    val catalogName: String = "AllTestsAreUs Shared Catalog",
    val courses: List<CatalogCourseRefDto> = emptyList()
)

@Serializable
data class CatalogCourseRefDto(
    val courseId: String,
    val category: String,
    val version: Int = 1,
    val title: String,
    val description: String = "",
    val url: String
)

@Serializable
data class CourseDto(
    val courseId: String,
    val category: String, // "orienteering", "reptiles", "languages"
    val version: Int = 1,
    val title: String,
    val description: String = "",
    val author: String = "",
    val sourceWebUrl: String = "",
    val speechLang: String? = null, // Например "el-GR" для греческого
    val lessons: List<LessonDto> = emptyList()
)

@Serializable
data class LessonDto(
    val lessonId: String,
    val title: String,
    val summary: String = "",
    val theory: List<TheoryBlockDto> = emptyList(),
    val concepts: List<ConceptDto> = emptyList()
)

@Serializable
data class TheoryBlockDto(
    val title: String,
    val body: String,
    val imageUrl: String? = null
)

@Serializable
data class ConceptDto(
    val conceptId: String,
    val term: String,
    val definition: String = "",
    val symbolCode: String? = null,
    val audioText: String? = null,
    val audioUrl: String? = null,
    val localAudioPath: String? = null,
    val questions: List<QuestionDto> = emptyList()
)

@Serializable
data class QuestionDto(
    val questionId: String,
    val type: String, // "TRUE_FALSE", "SINGLE_CHOICE", "MULTI_CHOICE", "WORD_ORDER", "FLASHCARD"
    val prompt: String,
    val mediaUrl: String? = null,
    val symbolCode: String? = null,
    val audioText: String? = null,
    val audioUrl: String? = null,
    val localAudioPath: String? = null,
    val correctBool: Boolean? = null,
    val options: List<OptionDto> = emptyList(),
    val wordTokens: List<String> = emptyList(),
    val answerText: String? = null,
    val explanation: String = ""
)

@Serializable
data class OptionDto(
    val id: String,
    val text: String,
    val isCorrect: Boolean = false
)
