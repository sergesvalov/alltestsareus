package com.alltestsareus.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alltestsareus.app.BuildConfig
import com.alltestsareus.app.R
import com.alltestsareus.app.model.CatalogCourseRefDto
import com.alltestsareus.app.model.CourseDto
import com.alltestsareus.app.ui.UiState

private val CategoryChips = listOf(
    "all" to "Все темы",
    "orienteering" to "Ориентирование",
    "languages" to "Языки",
    "reptiles" to "Рептилии"
)

@DrawableRes
private fun serviceIconRes(category: String): Int = when (category) {
    "orienteering" -> R.drawable.ic_service_orienteering
    "languages" -> R.drawable.ic_service_languages
    "reptiles" -> R.drawable.ic_service_reptiles
    else -> R.drawable.ic_service_all
}

@Composable
fun CatalogScreen(
    state: UiState,
    onSelectCategory: (String) -> Unit,
    onOpenCourse: (CourseDto) -> Unit,
    onStartDailyReview: () -> Unit,
    onOpenWebImport: () -> Unit,
    onBrowseCatalog: () -> Unit,
    onInstallCatalogCourse: (CatalogCourseRefDto) -> Unit,
    onSyncAllCatalog: () -> Unit
) {
    val dueItems = remember(state.filteredCourses, state.progressByConcept) {
        state.getDueReviewItems()
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = painterResource(id = serviceIconRes(BuildConfig.FLAVOR_CATEGORY)),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = BuildConfig.FLAVOR_TITLE,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Мои тесты",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = onOpenWebImport) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_service_web_catalog),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Скачать с сайта")
                }
            }
        }

        if (state.allCourses.isEmpty()) {
            item {
                EmptyCatalogCard(
                    state = state,
                    onBrowseCatalog = onBrowseCatalog,
                    onSyncAllCatalog = onSyncAllCatalog,
                    onOpenWebImport = onOpenWebImport
                )
            }
            val catalogItems = state.sharedCatalogResult?.items.orEmpty()
            if (catalogItems.isNotEmpty()) {
                items(catalogItems, key = { it.ref.courseId }) { item ->
                    RemoteCoursePickItem(
                        ref = item.ref,
                        isBusy = state.isImporting,
                        onDownload = { onInstallCatalogCourse(item.ref) }
                    )
                }
            }
        } else {
            if (BuildConfig.FLAVOR_CATEGORY == "all") {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryChips.forEach { (catId, label) ->
                            FilterChip(
                                selected = state.selectedCategoryFilter == catId,
                                onClick = { onSelectCategory(catId) },
                                leadingIcon = {
                                    Image(
                                        painter = painterResource(id = serviceIconRes(catId)),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }

            item {
                SmartReviewCard(dueCount = dueItems.size, onStartDailyReview = onStartDailyReview)
            }

            item {
                Text(
                    text = "Скачанные курсы (${state.filteredCourses.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            if (state.filteredCourses.isEmpty()) {
                item {
                    Text(
                        text = "В этой категории пока нет скачанных тестов. Нажмите «Скачать с сайта».",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp
                    )
                }
            } else {
                items(state.filteredCourses, key = { it.courseId }) { course ->
                    CourseCardItem(course = course, onClick = { onOpenCourse(course) })
                }
            }
        }
    }
}

@Composable
private fun EmptyCatalogCard(
    state: UiState,
    onBrowseCatalog: () -> Unit,
    onSyncAllCatalog: () -> Unit,
    onOpenWebImport: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(id = serviceIconRes(BuildConfig.FLAVOR_CATEGORY)),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Тестов пока нет — какой загрузить?",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Text(
                text = "Приложение установлено без встроенных тестов. Выберите нужный курс с сервера (${state.sharedResourceUrl}) — после скачивания он работает полностью офлайн.",
                color = Color(0xFFCBD5E1),
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            if (state.isImporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (!state.importStatusMessage.isNullOrBlank()) {
                Text(
                    text = state.importStatusMessage,
                    color = Color(0xFF38BDF8),
                    fontSize = 12.sp
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.sharedCatalogResult != null && state.sharedCatalogResult.items.isNotEmpty()) {
                    Button(
                        onClick = onSyncAllCatalog,
                        enabled = !state.isImporting,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Скачать все", fontSize = 13.sp)
                    }
                } else {
                    Button(
                        onClick = onBrowseCatalog,
                        enabled = !state.isImporting,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Запросить с сервера", fontSize = 13.sp)
                    }
                }
                OutlinedButton(
                    onClick = onOpenWebImport,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Другой URL / HTML", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun RemoteCoursePickItem(
    ref: CatalogCourseRefDto,
    isBusy: Boolean,
    onDownload: () -> Unit
) {
    val badgeColor = when (ref.category) {
        "orienteering" -> Color(0xFF10B981)
        "reptiles" -> Color(0xFFEAB308)
        "languages" -> Color(0xFFA855F7)
        else -> Color(0xFF38BDF8)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = badgeColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = serviceIconRes(ref.category)),
                        contentDescription = ref.category,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ref.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (ref.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = ref.description,
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        maxLines = 2
                    )
                }
            }
            Button(
                onClick = onDownload,
                enabled = !isBusy
            ) {
                Text("Скачать", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SmartReviewCard(dueCount: Int, onStartDailyReview: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF132F4C)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_service_srs_brain),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Интервальное повторение (SM-2)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (dueCount > 0) {
                    "Готово к закреплению сегодня: $dueCount понятий"
                } else {
                    "Все изученные понятия повторены! Пройдите новый урок."
                },
                color = Color(0xFFCBD5E1),
                fontSize = 14.sp
            )
            if (dueCount > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(onClick = onStartDailyReview) {
                    Text("Погонять повторение ($dueCount)")
                }
            }
        }
    }
}

@Composable
private fun CourseCardItem(course: CourseDto, onClick: () -> Unit) {
    val badgeColor = when (course.category) {
        "orienteering" -> Color(0xFF10B981)
        "reptiles" -> Color(0xFFEAB308)
        "languages" -> Color(0xFFA855F7)
        else -> Color(0xFF38BDF8)
    }
    val conceptCount = remember(course) { course.lessons.sumOf { it.concepts.size } }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                color = badgeColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = serviceIconRes(course.category)),
                        contentDescription = course.category,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.category.uppercase(),
                    color = badgeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = course.title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = course.description,
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Уроков: ${course.lessons.size} • Понятий: $conceptCount • v${course.version}",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp
                )
            }
        }
    }
}
