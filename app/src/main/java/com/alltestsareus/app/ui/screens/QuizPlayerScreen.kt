package com.alltestsareus.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alltestsareus.app.domain.srs.ReviewGrade
import com.alltestsareus.app.model.QuestionDto
import com.alltestsareus.app.ui.OrienteeringSignBadge
import com.alltestsareus.app.ui.QuizQueueItem
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuizPlayerScreen(
    queue: List<QuizQueueItem>,
    preferDeviceTts: Boolean,
    onToggleDeviceTts: () -> Unit,
    onPlayAudio: (localPath: String?, audioText: String?, speechLang: String?) -> Unit,
    onGradeSubmitted: (QuizQueueItem, ReviewGrade) -> Unit,
    onFinishQuiz: () -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    var correctCount by remember { mutableIntStateOf(0) }
    var selectedOptionId by remember { mutableStateOf<String?>(null) }
    var selectedMultiIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedBoolAnswer by remember { mutableStateOf<Boolean?>(null) }
    var selectedTokenIndices by remember { mutableStateOf<List<Int>>(emptyList()) }
    var flashcardRevealed by remember { mutableStateOf(false) }
    var isAnswered by remember { mutableStateOf(false) }

    val currentItem = queue.getOrNull(currentIndex)

    if (currentItem == null) {
        QuizSummaryScreen(
            correctCount = correctCount,
            totalCount = queue.size,
            onFinishQuiz = onFinishQuiz
        )
        return
    }

    val q = currentItem.question
    val symbolCode = q.symbolCode ?: currentItem.concept.symbolCode
    val audioLocalPath = q.localAudioPath ?: currentItem.concept.localAudioPath
    val audioText = q.audioText ?: currentItem.concept.audioText
    val hasLocalFile = audioLocalPath?.let { File(it).exists() } == true
    val canSpeak = hasLocalFile || !audioText.isNullOrBlank()

    // Автоматически озвучиваем слово на греческом при открытии нового вопроса с озвучкой
    LaunchedEffect(q.questionId) {
        if (canSpeak) {
            onPlayAudio(audioLocalPath, audioText, currentItem.speechLang)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Вопрос ${currentIndex + 1} из ${queue.size}",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onFinishQuiz) {
                Text("Завершить")
            }
        }

        LinearProgressIndicator(
            progress = { (currentIndex + 1).toFloat() / queue.size.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth()
        )

        QuestionTypeBadge(type = q.type)

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!symbolCode.isNullOrBlank()) {
                    OrienteeringSignBadge(symbolCode = symbolCode, size = 80.dp)
                }
                Text(
                    text = q.prompt,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (canSpeak) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = { onPlayAudio(audioLocalPath, audioText, currentItem.speechLang) },
                    modifier = Modifier.weight(1f)
                ) {
                    val labelText = audioText?.let { "🔊 Озвучить: $it" } ?: "🔊 Озвучить слово"
                    Text(labelText, maxLines = 1)
                }
                OutlinedButton(onClick = onToggleDeviceTts) {
                    Text(
                        text = if (preferDeviceTts || !hasLocalFile) "TTS тел." else "Файл",
                        fontSize = 12.sp
                    )
                }
            }
        }

        when (q.type) {
            "TRUE_FALSE" -> {
                TrueFalseSection(
                    question = q,
                    selectedBool = selectedBoolAnswer,
                    isAnswered = isAnswered,
                    onSelect = { chosen ->
                        selectedBoolAnswer = chosen
                        isAnswered = true
                        val expected = resolveExpectedBool(q)
                        val isCorrect = chosen == expected
                        if (isCorrect) correctCount++
                        onGradeSubmitted(
                            currentItem,
                            if (isCorrect) ReviewGrade.GOOD else ReviewGrade.AGAIN
                        )
                    }
                )
            }

            "SINGLE_CHOICE" -> {
                q.options.forEach { option ->
                    val bgColor = when {
                        !isAnswered -> Color(0xFF1E293B)
                        option.isCorrect -> Color(0xFF065F46)
                        selectedOptionId == option.id -> Color(0xFF7F1D1D)
                        else -> Color(0xFF1E293B)
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = bgColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isAnswered) {
                                selectedOptionId = option.id
                                isAnswered = true
                                if (option.isCorrect) correctCount++
                                onGradeSubmitted(
                                    currentItem,
                                    if (option.isCorrect) ReviewGrade.GOOD else ReviewGrade.AGAIN
                                )
                            }
                    ) {
                        Text(
                            text = option.text,
                            color = Color.White,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            "MULTI_CHOICE" -> {
                MultiChoiceSection(
                    question = q,
                    selectedIds = selectedMultiIds,
                    isAnswered = isAnswered,
                    onToggleId = { optId ->
                        selectedMultiIds = if (optId in selectedMultiIds) {
                            selectedMultiIds - optId
                        } else {
                            selectedMultiIds + optId
                        }
                    },
                    onConfirm = {
                        isAnswered = true
                        val correctIds = q.options.filter { it.isCorrect }.map { it.id }.toSet()
                        val isCorrect = selectedMultiIds == correctIds
                        if (isCorrect) correctCount++
                        onGradeSubmitted(
                            currentItem,
                            if (isCorrect) ReviewGrade.GOOD else ReviewGrade.AGAIN
                        )
                    }
                )
            }

            "WORD_ORDER" -> {
                val shuffledIndexed = remember(q.questionId) {
                    q.wordTokens.mapIndexed { idx, word -> idx to word }.shuffled()
                }
                val assembledText = selectedTokenIndices.joinToString(" ") { idx -> q.wordTokens[idx] }
                Text("Ваш ответ: $assembledText", color = Color(0xFF38BDF8), fontSize = 16.sp)

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    shuffledIndexed.forEach { (origIdx, word) ->
                        val isUsed = origIdx in selectedTokenIndices
                        FilterChip(
                            selected = isUsed,
                            enabled = !isAnswered && !isUsed,
                            onClick = {
                                val next = selectedTokenIndices + origIdx
                                selectedTokenIndices = next
                                if (next.size == q.wordTokens.size) {
                                    isAnswered = true
                                    val assembledWords = next.map { q.wordTokens[it] }
                                    val isCorrect = assembledWords == q.wordTokens
                                    if (isCorrect) correctCount++
                                    onGradeSubmitted(
                                        currentItem,
                                        if (isCorrect) ReviewGrade.GOOD else ReviewGrade.AGAIN
                                    )
                                }
                            },
                            label = { Text(word) }
                        )
                    }
                }

                if (!isAnswered && selectedTokenIndices.isNotEmpty()) {
                    TextButton(onClick = { selectedTokenIndices = emptyList() }) {
                        Text("Сбросить слова")
                    }
                }
            }

            "FLASHCARD" -> {
                if (!flashcardRevealed) {
                    Button(
                        onClick = { flashcardRevealed = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Показать ответ карточки")
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = q.answerText ?: currentItem.concept.definition,
                            color = Color.White,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    if (!isAnswered) {
                        Text("Оцените, насколько легко вспомнили:", color = Color(0xFF94A3B8), fontSize = 13.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    isAnswered = true
                                    onGradeSubmitted(currentItem, ReviewGrade.AGAIN)
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Забыл") }
                            OutlinedButton(
                                onClick = {
                                    isAnswered = true
                                    correctCount++
                                    onGradeSubmitted(currentItem, ReviewGrade.HARD)
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Трудно") }
                            Button(
                                onClick = {
                                    isAnswered = true
                                    correctCount++
                                    onGradeSubmitted(currentItem, ReviewGrade.EASY)
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Легко!") }
                        }
                    }
                }
            }
        }

        if (isAnswered) {
            if (q.explanation.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF172554)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "💡 Пояснение: ${q.explanation}",
                        color = Color(0xFFDBEAFE),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
            Button(
                onClick = {
                    selectedOptionId = null
                    selectedMultiIds = emptySet()
                    selectedBoolAnswer = null
                    selectedTokenIndices = emptyList()
                    flashcardRevealed = false
                    isAnswered = false
                    currentIndex++
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Следующий вопрос →")
            }
        }
    }
}

@Composable
private fun QuestionTypeBadge(type: String) {
    val label = when (type) {
        "TRUE_FALSE" -> "⚖️ Правда или Ложь"
        "SINGLE_CHOICE" -> "☝️ Выберите один вариант"
        "MULTI_CHOICE" -> "☑️ Выберите все подходящие варианты"
        "WORD_ORDER" -> "🧩 Соберите фразу"
        "FLASHCARD" -> "🗂️ Карточка самопроверки"
        else -> type
    }
    Text(
        text = label,
        color = Color(0xFF94A3B8),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun TrueFalseSection(
    question: QuestionDto,
    selectedBool: Boolean?,
    isAnswered: Boolean,
    onSelect: (Boolean) -> Unit
) {
    val expected = resolveExpectedBool(question)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        listOf(true to "✅ Правда", false to "❌ Ложь").forEach { (value, label) ->
            val bgColor = when {
                !isAnswered -> Color(0xFF1E293B)
                value == expected -> Color(0xFF065F46)
                selectedBool == value -> Color(0xFF7F1D1D)
                else -> Color(0xFF1E293B)
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = bgColor),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !isAnswered) { onSelect(value) }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp)
                ) {
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun MultiChoiceSection(
    question: QuestionDto,
    selectedIds: Set<String>,
    isAnswered: Boolean,
    onToggleId: (String) -> Unit,
    onConfirm: () -> Unit
) {
    question.options.forEach { option ->
        val isChecked = option.id in selectedIds
        val bgColor = when {
            !isAnswered && isChecked -> Color(0xFF1E3A5F)
            !isAnswered -> Color(0xFF1E293B)
            option.isCorrect && isChecked -> Color(0xFF065F46)
            option.isCorrect && !isChecked -> Color(0xFF78350F)
            !option.isCorrect && isChecked -> Color(0xFF7F1D1D)
            else -> Color(0xFF1E293B)
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = bgColor),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isAnswered) { onToggleId(option.id) }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = if (!isAnswered) { _ -> onToggleId(option.id) } else null
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = option.text, color = Color.White, fontSize = 15.sp)
                    if (isAnswered && option.isCorrect && !isChecked) {
                        Text(
                            text = "Нужно было выбрать",
                            color = Color(0xFFFCD34D),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }

    if (!isAnswered) {
        Button(
            onClick = onConfirm,
            enabled = selectedIds.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Проверить выбор (${selectedIds.size})")
        }
    }
}

private fun resolveExpectedBool(question: QuestionDto): Boolean {
    return question.correctBool
        ?: question.options.firstOrNull { it.isCorrect }?.id?.equals("true", ignoreCase = true)
        ?: true
}

@Composable
private fun QuizSummaryScreen(
    correctCount: Int,
    totalCount: Int,
    onFinishQuiz: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🎉 Тренировка завершена!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Результат: $correctCount из $totalCount верных ответов",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF10B981)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Алгоритм SM-2 обновил расписание повторений в базе телефона.",
            color = Color(0xFF94A3B8)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onFinishQuiz) {
            Text("Вернуться к курсам")
        }
    }
}
