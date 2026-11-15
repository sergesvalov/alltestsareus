package com.alltestsareus.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alltestsareus.app.data.local.UserConceptProgressEntity
import com.alltestsareus.app.model.CourseDto
import com.alltestsareus.app.model.LessonDto
import com.alltestsareus.app.ui.OrienteeringSignBadge
import java.io.File

@Composable
fun CourseDetailScreen(
    course: CourseDto,
    lesson: LessonDto?,
    progressMap: Map<String, UserConceptProgressEntity>,
    preferDeviceTts: Boolean,
    onToggleDeviceTts: () -> Unit,
    onPlayAudio: (localPath: String?, audioText: String?, speechLang: String?) -> Unit,
    onSelectLesson: (LessonDto) -> Unit,
    onDeleteCourse: (String) -> Unit,
    onBack: () -> Unit,
    onStartQuiz: (CourseDto, LessonDto) -> Unit
) {
    val context = LocalContext.current
    val hasAnyAudio = remember(course) {
        course.lessons.any { l ->
            l.concepts.any { !it.audioText.isNullOrBlank() || !it.audioUrl.isNullOrBlank() }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onBack) {
                    Text("← Назад")
                }
                Row {
                    if (course.sourceWebUrl.isNotBlank()) {
                        TextButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(course.sourceWebUrl))
                                    )
                                }
                            }
                        ) {
                            Text("🌐 Сайт")
                        }
                    }
                    TextButton(
                        onClick = { onDeleteCourse(course.courseId) },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF87171))
                    ) {
                        Text("Удалить")
                    }
                }
            }
            Text(
                text = course.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        if (hasAnyAudio) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF132F4C)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (preferDeviceTts) "🗣️ Голос телефона (TTS ${course.speechLang ?: "el-GR"})" else "🔊 Скачанное аудио с теста",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Нажмите для смены источника озвучки",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                        TextButton(onClick = onToggleDeviceTts) {
                            Text(if (preferDeviceTts) "Файл" else "TTS")
                        }
                    }
                }
            }
        }

        if (course.lessons.size > 1) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    course.lessons.forEachIndexed { idx, l ->
                        FilterChip(
                            selected = lesson?.lessonId == l.lessonId,
                            onClick = { onSelectLesson(l) },
                            label = { Text("Урок ${idx + 1}", fontSize = 13.sp) }
                        )
                    }
                }
            }
        }

        if (lesson != null) {
            item {
                Text(
                    text = "📘 ${lesson.title}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = lesson.summary, color = Color(0xFF94A3B8), fontSize = 14.sp)
            }

            items(lesson.theory) { block ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = block.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = block.body,
                            color = Color(0xFFE2E8F0),
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            item {
                val questionCount = remember(lesson) { lesson.concepts.sumOf { it.questions.size } }
                Button(
                    onClick = { onStartQuiz(course, lesson) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🎯 Погонять тест по уроку ($questionCount вопр.)")
                }
            }

            item {
                Text(
                    text = "Единицы памяти (Concepts) в этом уроке (${lesson.concepts.size}):",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.White
                )
            }

            items(lesson.concepts, key = { it.conceptId }) { concept ->
                val prog = progressMap[concept.conceptId]
                val hasAudioFile = concept.localAudioPath?.let { File(it).exists() } == true
                val canSpeak = hasAudioFile || !concept.audioText.isNullOrBlank()

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF162032)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!concept.symbolCode.isNullOrBlank()) {
                            OrienteeringSignBadge(symbolCode = concept.symbolCode, size = 56.dp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = concept.term, fontWeight = FontWeight.Bold, color = Color.White)
                            if (concept.definition.isNotBlank()) {
                                Text(text = concept.definition, color = Color(0xFF94A3B8), fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            val statusText = buildString {
                                if (prog == null) {
                                    append("Статус: Новое понятие")
                                } else {
                                    append("SM-2: ${prog.intervalDays} дн. • Серия: ${prog.repetitions}")
                                }
                                if (hasAudioFile) append(" • 🔊 Аудио на телефоне")
                            }
                            Text(text = statusText, color = Color(0xFF10B981), fontSize = 12.sp)
                        }
                        if (canSpeak) {
                            FilledTonalButton(
                                onClick = {
                                    onPlayAudio(concept.localAudioPath, concept.audioText, course.speechLang)
                                }
                            ) {
                                Text("🔊")
                            }
                        }
                    }
                }
            }
        }
    }
}
