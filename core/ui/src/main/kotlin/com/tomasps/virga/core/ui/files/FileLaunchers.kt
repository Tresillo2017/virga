package com.tomasps.virga.core.ui.files

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

/**
 * Storage Access Framework "save as" picker. Launch it with the suggested file name, for example
 * `launch("virga-logs.txt")`. [onResult] gets the document's Uri, or null when the user cancels.
 * [mimeType] is fixed when the launcher is created.
 */
@Composable
fun rememberSaveFileLauncher(
    mimeType: String,
    onResult: (Uri?) -> Unit,
): ManagedActivityResultLauncher<String, Uri?> =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType), onResult)

/**
 * Storage Access Framework "open" picker. Launch it with the accepted MIME types, for example
 * `launch(arrayOf("application/json"))`. [onResult] gets the chosen Uri, or null when the user cancels.
 */
@Composable
fun rememberOpenFileLauncher(
    onResult: (Uri?) -> Unit,
): ManagedActivityResultLauncher<Array<String>, Uri?> =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), onResult)
