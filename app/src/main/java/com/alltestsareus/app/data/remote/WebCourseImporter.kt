package com.alltestsareus.app.data.remote

import com.alltestsareus.app.model.CatalogCourseRefDto
import com.alltestsareus.app.model.CatalogManifestDto
import com.alltestsareus.app.model.CourseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class ResolvedCatalogManifest(
    val manifest: CatalogManifestDto,
    val resolvedCourses: List<CatalogCourseRefDto>
)

/**
 * Загрузчик и парсер тестов с общего ресурса (catalog.json) и внешних веб-страниц.
 *
 * Поддерживает 3 сценария:
 * 1. Общий ресурс каталога (catalog.json) — получение витрины курсов, проверка версий и параллельное скачивание.
 * 2. HTML веб-страница, содержащая блок <script id="alltests-course-data" type="application/json">{ ... }</script>.
 * 3. Прямая ссылка на JSON-файл курса.
 */
class WebCourseImporter(
    private val json: Json = SharedJson
) {

    suspend fun fetchCatalogManifest(catalogUrl: String): Result<ResolvedCatalogManifest> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trimmedUrl = validateHttpUrl(catalogUrl)
                val rawContent = downloadText(trimmedUrl)
                val manifest = json.decodeFromString<CatalogManifestDto>(rawContent.trim())
                val baseUri = URI.create(trimmedUrl)

                val resolved = manifest.courses.map { ref ->
                    ref.copy(url = baseUri.resolve(ref.url).toString())
                }
                ResolvedCatalogManifest(manifest = manifest, resolvedCourses = resolved)
            }
        }

    suspend fun fetchSingleCourseFromUrl(targetUrl: String): Result<CourseDto> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trimmedUrl = validateHttpUrl(targetUrl)
                val rawContent = downloadText(trimmedUrl)
                parseWebPayload(rawContent, sourceUrl = trimmedUrl).firstOrNull()
                    ?: throw IllegalStateException("Не удалось извлечь курс из $trimmedUrl")
            }
        }

    suspend fun fetchCoursesFromWebUrl(targetUrl: String): Result<List<CourseDto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trimmedUrl = validateHttpUrl(targetUrl)
                val rawContent = downloadText(trimmedUrl)
                parseWebPayload(rawContent, sourceUrl = trimmedUrl)
            }
        }

    suspend fun parseWebPayload(rawContent: String, sourceUrl: String): List<CourseDto> {
        val trimmed = rawContent.trim()

        if (isHtmlPayload(trimmed)) {
            val extractedJson = extractEmbeddedJsonFromHtml(trimmed)
                ?: throw IllegalArgumentException(
                    "На веб-странице не найден блок <script id=\"alltests-course-data\" type=\"application/json\">"
                )
            return listOf(decodeCourseWithSource(extractedJson, sourceUrl))
        }

        if (trimmed.contains("\"catalogVersion\"") && trimmed.contains("\"courses\"")) {
            val manifest = json.decodeFromString<CatalogManifestDto>(trimmed)
            val baseUri = URI.create(sourceUrl)
            return coroutineScope {
                manifest.courses.map { ref ->
                    async(Dispatchers.IO) {
                        val resolvedUrl = baseUri.resolve(ref.url).toString()
                        decodeCourseWithSource(downloadText(resolvedUrl), resolvedUrl)
                    }
                }.awaitAll()
            }
        }

        return listOf(decodeCourseWithSource(trimmed, sourceUrl))
    }

    internal fun extractEmbeddedJsonFromHtml(html: String): String? {
        return EMBEDDED_COURSE_REGEX.find(html)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun decodeCourseWithSource(rawJson: String, fallbackUrl: String): CourseDto {
        val decoded = json.decodeFromString<CourseDto>(rawJson)
        val effectiveSource = if (decoded.sourceWebUrl.isBlank()) fallbackUrl else decoded.sourceWebUrl
        val baseUri = runCatching { URI.create(fallbackUrl) }.getOrNull()

        fun resolveUrl(relOrAbs: String?): String? {
            if (relOrAbs.isNullOrBlank()) return null
            if (relOrAbs.startsWith("http://") || relOrAbs.startsWith("https://")) return relOrAbs
            return baseUri?.let { runCatching { it.resolve(relOrAbs).toString() }.getOrNull() } ?: relOrAbs
        }

        return decoded.copy(
            sourceWebUrl = effectiveSource,
            lessons = decoded.lessons.map { lesson ->
                lesson.copy(
                    concepts = lesson.concepts.map { concept ->
                        concept.copy(
                            audioUrl = resolveUrl(concept.audioUrl),
                            questions = concept.questions.map { q ->
                                q.copy(audioUrl = resolveUrl(q.audioUrl))
                            }
                        )
                    }
                )
            }
        )
    }

    private fun isHtmlPayload(content: String): Boolean {
        return content.startsWith("<!DOCTYPE", ignoreCase = true) ||
            content.startsWith("<html", ignoreCase = true) ||
            content.contains("<script", ignoreCase = true)
    }

    private fun validateHttpUrl(url: String): String {
        val trimmedUrl = url.trim()
        require(trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://")) {
            "URL должен начинаться с http:// или https://"
        }
        return trimmedUrl
    }

    private fun downloadText(urlString: String): String {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json, text/html, */*")
            setRequestProperty("User-Agent", "AllTestsAreUs-Android/1.0")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("Сервер вернул код HTTP $code для $urlString")
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TIMEOUT_MS = 10_000
        val SharedJson: Json = Json { ignoreUnknownKeys = true; isLenient = true }
        private val EMBEDDED_COURSE_REGEX = Regex(
            pattern = """<script[^>]*id\s*=\s*["']alltests-course-data["'][^>]*>([\s\S]*?)</script>""",
            option = RegexOption.IGNORE_CASE
        )
    }
}
