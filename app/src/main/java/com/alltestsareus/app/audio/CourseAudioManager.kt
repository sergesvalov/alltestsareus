package com.alltestsareus.app.audio

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import com.alltestsareus.app.model.CourseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Управляет загрузкой аудиофайлов курса на телефон и озвучкой на греческом/других языках:
 * 1. Воспроизводит скачанный вместе с тестом локальный аудиофайл (MediaPlayer, 100% офлайн).
 * 2. Поддерживает встроенный синтез речи телефона (Android TextToSpeech, например el-GR)
 *    как альтернативный режим или автоматический fallback.
 */
class CourseAudioManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var mediaPlayer: MediaPlayer? = null
    private var pendingSpeak: Pair<String, String>? = null

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context.applicationContext) { status ->
            isTtsReady = (status == TextToSpeech.SUCCESS)
            if (isTtsReady) {
                pendingSpeak?.let { (text, lang) ->
                    pendingSpeak = null
                    speakWithDeviceTts(text, lang)
                }
            }
        }
    }

    /**
     * Скачивает все аудиофайлы курса параллельно в filesDir/course_audio/<courseId>/
     * и возвращает обновлённый CourseDto с заполненными localAudioPath.
     */
    suspend fun downloadCourseAudio(course: CourseDto): CourseDto = withContext(Dispatchers.IO) {
        val allUrls = buildSet {
            course.lessons.forEach { lesson ->
                lesson.concepts.forEach { concept ->
                    concept.audioUrl?.takeIf { it.startsWith("http") }?.let(::add)
                    concept.questions.forEach { q ->
                        q.audioUrl?.takeIf { it.startsWith("http") }?.let(::add)
                    }
                }
            }
        }
        if (allUrls.isEmpty()) return@withContext course

        val audioDir = File(context.filesDir, "course_audio/${course.courseId}").apply { mkdirs() }
        val urlToLocalPath = coroutineScope {
            allUrls.map { url ->
                async {
                    val fileName = url.substringAfterLast('/').substringBefore('?').ifBlank {
                        "${url.hashCode()}.mp3"
                    }
                    val targetFile = File(audioDir, fileName)
                    val downloaded = downloadBinaryIfNeeded(url, targetFile)
                    if (downloaded) url to targetFile.absolutePath else null
                }
            }.awaitAll().filterNotNull().toMap()
        }

        if (urlToLocalPath.isEmpty()) return@withContext course

        course.copy(
            lessons = course.lessons.map { lesson ->
                lesson.copy(
                    concepts = lesson.concepts.map { concept ->
                        concept.copy(
                            localAudioPath = concept.audioUrl?.let { urlToLocalPath[it] } ?: concept.localAudioPath,
                            questions = concept.questions.map { q ->
                                q.copy(
                                    localAudioPath = q.audioUrl?.let { urlToLocalPath[it] } ?: q.localAudioPath
                                )
                            }
                        )
                    }
                )
            }
        )
    }

    fun deleteCourseAudio(courseId: String) {
        File(context.filesDir, "course_audio/$courseId").deleteRecursively()
    }

    /**
     * Озвучивает слово или фразу:
     * - если preferDeviceTts == false и скачан локальный файл — играет файл с телефона;
     * - иначе использует голосовой движок телефона (Android TextToSpeech, напр. el-GR).
     */
    fun playSpeech(
        localAudioPath: String?,
        audioText: String?,
        langTag: String?,
        preferDeviceTts: Boolean = false
    ) {
        val file = localAudioPath?.let(::File)?.takeIf { it.exists() && it.length() > 0L }
        if (!preferDeviceTts && file != null) {
            if (playLocalFile(file)) return
        }

        if (!audioText.isNullOrBlank()) {
            speakWithDeviceTts(audioText, langTag ?: "el-GR")
        } else if (file != null) {
            playLocalFile(file)
        }
    }

    private fun playLocalFile(file: File): Boolean {
        return runCatching {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { mp ->
                    mp.release()
                    if (mediaPlayer === mp) mediaPlayer = null
                }
                prepare()
                start()
            }
            true
        }.getOrDefault(false)
    }

    private fun speakWithDeviceTts(text: String, langTag: String) {
        val engine = tts ?: return
        if (!isTtsReady) {
            pendingSpeak = text to langTag
            return
        }
        val locale = Locale.forLanguageTag(langTag)
        val langResult = engine.setLanguage(locale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.language = Locale("el", "GR")
        }
        engine.setSpeechRate(0.92f)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance_${text.hashCode()}")
    }

    private fun downloadBinaryIfNeeded(urlString: String, targetFile: File): Boolean {
        return runCatching {
            val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "AllTestsAreUs-Android/1.0")
            }
            try {
                if (connection.responseCode !in 200..299) return@runCatching false
                val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
                connection.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempFile.length() > 0L) {
                    tempFile.renameTo(targetFile)
                    true
                } else {
                    tempFile.delete()
                    false
                }
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(false)
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

