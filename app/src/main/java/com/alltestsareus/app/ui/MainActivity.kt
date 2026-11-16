package com.alltestsareus.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alltestsareus.app.ui.components.SharedResourceAndWebImportDialog
import com.alltestsareus.app.ui.screens.CatalogScreen
import com.alltestsareus.app.ui.screens.CourseDetailScreen
import com.alltestsareus.app.ui.screens.QuizPlayerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF38BDF8),
                    secondary = Color(0xFF10B981),
                    background = Color(0xFF0B1120),
                    surface = Color(0xFF1E293B)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AllTestsAppRoot()
                }
            }
        }
    }
}

@Composable
fun AllTestsAppRoot(vm: MainViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    var showWebImportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.allCourses.isEmpty()) {
        if (state.allCourses.isEmpty() && state.sharedCatalogResult == null && !state.isImporting) {
            vm.browseSharedCatalog(state.sharedResourceUrl)
        }
    }

    when {
        state.activeQuizQueue.isNotEmpty() -> {
            QuizPlayerScreen(
                queue = state.activeQuizQueue,
                preferDeviceTts = state.preferDeviceTts,
                onToggleDeviceTts = { vm.togglePreferDeviceTts() },
                onPlayAudio = { localPath, audioText, speechLang ->
                    vm.playAudio(localPath, audioText, speechLang)
                },
                onGradeSubmitted = { item, grade -> vm.submitAnswerGrade(item, grade) },
                onFinishQuiz = { vm.exitQuiz() }
            )
        }
        state.activeCourse != null -> {
            CourseDetailScreen(
                course = state.activeCourse!!,
                lesson = state.activeLesson ?: state.activeCourse!!.lessons.firstOrNull(),
                progressMap = state.progressByConcept,
                preferDeviceTts = state.preferDeviceTts,
                onToggleDeviceTts = { vm.togglePreferDeviceTts() },
                onPlayAudio = { localPath, audioText, speechLang ->
                    vm.playAudio(localPath, audioText, speechLang)
                },
                onSelectLesson = { vm.selectLesson(it) },
                onDeleteCourse = { vm.deleteCourse(it) },
                onBack = { vm.closeCourse() },
                onStartQuiz = { course, lesson -> vm.startLessonQuiz(course, lesson) }
            )
        }
        else -> {
            CatalogScreen(
                state = state,
                onSelectCategory = { vm.selectCategoryFilter(it) },
                onOpenCourse = { vm.openCourse(it) },
                onStartDailyReview = { vm.startDailySmartReview() },
                onOpenWebImport = { showWebImportDialog = true },
                onBrowseCatalog = { vm.browseSharedCatalog(state.sharedResourceUrl) },
                onInstallCatalogCourse = { ref -> vm.installCourseFromSharedCatalog(ref) },
                onSyncAllCatalog = { vm.syncAllFromSharedResource(state.sharedResourceUrl) }
            )
        }
    }

    if (showWebImportDialog) {
        SharedResourceAndWebImportDialog(
            initialSharedUrl = state.sharedResourceUrl,
            sharedCatalog = state.sharedCatalogResult,
            isImporting = state.isImporting,
            statusMessage = state.importStatusMessage,
            onBrowseCatalog = { url -> vm.browseSharedCatalog(url) },
            onSyncAllCatalog = { url -> vm.syncAllFromSharedResource(url) },
            onInstallCourseFromCatalog = { ref -> vm.installCourseFromSharedCatalog(ref) },
            onImportDirectWebPage = { url -> vm.importFromWebPageUrl(url) },
            onDismiss = {
                vm.clearImportStatus()
                showWebImportDialog = false
            }
        )
    }
}
