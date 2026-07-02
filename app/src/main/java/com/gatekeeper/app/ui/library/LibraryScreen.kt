package com.gatekeeper.app.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.gatekeeper.app.data.db.Book
import com.gatekeeper.app.reader.BookRepository
import com.gatekeeper.app.reader.ImportResult
import com.gatekeeper.app.reader.ReaderActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** EPUB library (Section 7): import via SAF, list with covers and progress. */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookRepository: BookRepository,
) : ViewModel() {

    val books = bookRepository.books
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val importing = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    fun import(uri: Uri) {
        viewModelScope.launch {
            importing.value = true
            message.value = when (val result = bookRepository.importEpub(uri)) {
                is ImportResult.Success -> "Imported \"${result.book.title}\"."
                is ImportResult.DrmProtected ->
                    "This EPUB is DRM-protected (LCP). Gatekeeper v1 only supports DRM-free EPUBs."
                is ImportResult.Error -> "Import failed: ${result.message}"
            }
            importing.value = false
        }
    }

    fun delete(book: Book) {
        viewModelScope.launch { bookRepository.delete(book) }
    }

    fun consumeMessage() {
        message.value = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: LibraryViewModel = hiltViewModel()) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf<Book?>(null) }

    // R-7.1: SAF document picker for EPUBs.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::import) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Books") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                picker.launch(arrayOf("application/epub+zip"))
            }) { Icon(Icons.Filled.Add, contentDescription = "Import EPUB") }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (importing) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Importing…")
                }
            }
            if (books.isEmpty() && !importing) {
                Text(
                    "No books yet. Import a DRM-free EPUB to power the reading gate.",
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            LazyColumn {
                items(books, key = { it.id }) { book ->
                    Card(
                        onClick = {
                            context.startActivity(ReaderActivity.intent(context, book.id))
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (book.coverPath != null && File(book.coverPath).exists()) {
                                AsyncImage(
                                    model = File(book.coverPath),
                                    contentDescription = null,
                                    modifier = Modifier.size(width = 48.dp, height = 68.dp),
                                )
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                                if (book.author != null) {
                                    Text(book.author, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                }
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { book.furthestProgression.toFloat().coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    "${(book.furthestProgression * 100).toInt()}% · ${book.totalPositions} pages",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            IconButton(onClick = { confirmDelete = book }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete book")
                            }
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete \"${book.title}\"?") },
            text = { Text("The imported copy and reading progress will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(book)
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            },
        )
    }
}
