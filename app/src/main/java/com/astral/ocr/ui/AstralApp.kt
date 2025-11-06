package com.astral.ocr.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.astral.ocr.MainViewModel
import com.astral.ocr.ui.screens.HomeScreen
import com.astral.ocr.ui.screens.SettingsScreen
import com.astral.ocr.ui.theme.gradientBackground
import kotlinx.coroutines.flow.collectLatest

sealed class AstralDestination(val route: String) {
    data object Home : AstralDestination("home")
    data object Settings : AstralDestination("settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AstralApp(
    viewModel: MainViewModel,
    pickSingleImage: (Array<String>, (Uri?) -> Unit) -> Unit,
    pickMultipleImages: (Array<String>, (List<Uri>) -> Unit) -> Unit,
    createDocument: (String, (Uri?) -> Unit) -> Unit,
    importPororoModels: (Array<String>, (List<Uri>) -> Unit) -> Unit
) {
    val navController: NavHostController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.notifications.collectLatest { message ->
            message?.let { snackbarHostState.showSnackbar(it) }
        }
    }

    LaunchedEffect(uiState.lastSavedPath) {
        uiState.lastSavedPath?.let {
            snackbarHostState.showSnackbar("Hasil tersimpan sebagai teks.")
            viewModel.setLastSavedPath(null)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("AstralOCR", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { navController.navigate(AstralDestination.Settings.route) }) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Pengaturan")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .gradientBackground()
                .background(Color.Transparent),
            contentAlignment = Alignment.TopCenter
        ) {
            NavHost(
                navController = navController,
                startDestination = AstralDestination.Home.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(AstralDestination.Home.route) {
                    HomeScreen(
                        uiState = uiState,
                        paddingValues = paddingValues,
                        onPickSingle = {
                            pickSingleImage(arrayOf("image/*")) { uri ->
                                uri?.let { viewModel.processSingle(context.contentResolver, it) }
                            }
                        },
                        onPickMultiple = {
                            pickMultipleImages(arrayOf("image/*")) { uris ->
                                viewModel.processBulk(context.contentResolver, uris)
                            }
                        },
                        onToggleBulk = viewModel::toggleBulkMode,
                        onClear = viewModel::clearResults,
                        onSaveResult = { filename, content ->
                            createDocument(filename) { uri ->
                                uri?.let {
                                    saveTextToUri(context.contentResolver, it, content)
                                    viewModel.setLastSavedPath(it.toString())
                                }
                            }
                        }
                    )
                }
                composable(AstralDestination.Settings.route) {
                    SettingsScreen(
                        uiState = uiState,
                        paddingValues = paddingValues,
                        onBack = { navController.popBackStack() },
                        onApiKeyChanged = viewModel::updateApiKey,
                        onModelChanged = viewModel::updateModel,
                        onProviderChanged = viewModel::updateProvider,
                        onImportPororoModels = {
                            importPororoModels(arrayOf("*/*")) { uris ->
                                viewModel.importPororoModels(context.contentResolver, uris)
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun saveTextToUri(contentResolver: ContentResolver, uri: Uri, text: String) {
    contentResolver.openOutputStream(uri)?.use { stream ->
        stream.bufferedWriter().use { writer ->
            writer.write(text)
        }
    }
}
