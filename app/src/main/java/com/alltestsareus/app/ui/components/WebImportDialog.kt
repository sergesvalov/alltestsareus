package com.alltestsareus.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alltestsareus.app.BuildConfig
import com.alltestsareus.app.R
import com.alltestsareus.app.data.repository.SharedCatalogBrowseResult
import com.alltestsareus.app.model.CatalogCourseRefDto

@Composable
fun SharedResourceAndWebImportDialog(
    initialSharedUrl: String,
    sharedCatalog: SharedCatalogBrowseResult?,
    isImporting: Boolean,
    statusMessage: String?,
    onBrowseCatalog: (String) -> Unit,
    onSyncAllCatalog: (String) -> Unit,
    onInstallCourseFromCatalog: (CatalogCourseRefDto) -> Unit,
    onImportDirectWebPage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var sharedUrlInput by remember { mutableStateOf(initialSharedUrl) }
    var directPageUrlInput by remember { mutableStateOf(BuildConfig.DEFAULT_SAMPLE_WEBPAGE_URL) }

    LaunchedEffect(Unit) {
        if (sharedCatalog == null && sharedUrlInput.isNotBlank()) {
            onBrowseCatalog(sharedUrlInput)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_service_web_catalog),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Text("Скачать тесты с сайта")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = { Text("Каталог сайта") }
                    )
                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        label = { Text("HTML / JSON URL") }
                    )
                }

                if (selectedTab == 0) {
                    OutlinedTextField(
                        value = sharedUrlInput,
                        onValueChange = { sharedUrlInput = it },
                        label = { Text("Адрес каталога сайта (catalog.json)") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onBrowseCatalog(sharedUrlInput) },
                            enabled = !isImporting && sharedUrlInput.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Обновить список", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { onSyncAllCatalog(sharedUrlInput) },
                            enabled = !isImporting && sharedUrlInput.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Скачать все", fontSize = 12.sp)
                        }
                    }

                    if (sharedCatalog != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${sharedCatalog.catalogName} (${sharedCatalog.items.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        sharedCatalog.items.forEach { item ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.ref.title,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = Color.White
                                        )
                                        val statusLabel = when {
                                            item.hasUpdate -> "Доступно обновление (v${item.ref.version})"
                                            item.isInstalled -> "Скачан (v${item.installedVersion})"
                                            else -> "Доступен на сайте (v${item.ref.version})"
                                        }
                                        Text(
                                            text = statusLabel,
                                            fontSize = 11.sp,
                                            color = if (item.hasUpdate) Color(0xFFEAB308) else Color(0xFF10B981)
                                        )
                                    }
                                    TextButton(
                                        onClick = { onInstallCourseFromCatalog(item.ref) },
                                        enabled = !isImporting
                                    ) {
                                        Text(if (item.isInstalled && !item.hasUpdate) "Обновить" else "Скачать")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Вставьте прямую ссылку на HTML-страницу с блоком alltests-course-data или на .json файл курса:",
                        fontSize = 12.sp,
                        color = Color(0xFFCBD5E1)
                    )
                    OutlinedTextField(
                        value = directPageUrlInput,
                        onValueChange = { directPageUrlInput = it },
                        label = { Text("URL веб-страницы или JSON") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onImportDirectWebPage(directPageUrlInput) },
                        enabled = !isImporting && directPageUrlInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Скачать со страницы")
                    }
                }

                if (isImporting) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (statusMessage != null) {
                    Text(text = statusMessage, fontSize = 12.sp, color = Color(0xFF38BDF8))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Готово")
            }
        }
    )
}

