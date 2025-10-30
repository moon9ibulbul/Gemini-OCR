package com.astral.ocr

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.astral.ocr.ui.AstralApp
import com.astral.ocr.ui.theme.AstralOCRTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(applicationContext)
    }

    private val singleImagePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            viewModel.onImagePickedCallback?.invoke(uri)
        }

    private val multipleImagePicker =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            viewModel.onMultipleImagesPickedCallback?.invoke(uris)
        }

    private val documentCreator =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            viewModel.onDocumentCreatedCallback?.invoke(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        setContent {
            AstralOCRTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AstralApp(
                        viewModel = viewModel,
                        pickSingleImage = { mimeTypes, callback ->
                            viewModel.onImagePickedCallback = callback
                            singleImagePicker.launch(mimeTypes.first())
                        },
                        pickMultipleImages = { mimeTypes, callback ->
                            viewModel.onMultipleImagesPickedCallback = callback
                            multipleImagePicker.launch(mimeTypes)
                        },
                        createDocument = { name, callback ->
                            viewModel.onDocumentCreatedCallback = callback
                            documentCreator.launch(name)
                        }
                    )
                }
            }
        }
    }
}
