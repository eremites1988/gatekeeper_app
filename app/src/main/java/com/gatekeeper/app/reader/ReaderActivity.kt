package com.gatekeeper.app.reader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.gatekeeper.app.data.SettingsRepository
import com.gatekeeper.app.data.db.Book
import com.gatekeeper.app.data.db.CompletionLog
import com.gatekeeper.app.data.db.CompletionLogDao
import com.gatekeeper.app.data.db.LogType
import com.gatekeeper.app.databinding.ActivityReaderBinding
import com.gatekeeper.app.ui.theme.GatekeeperTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Try
import java.io.File
import javax.inject.Inject
import kotlin.math.max

/**
 * Embedded EPUB reader (Section 7). Hosts Readium's WebView-based
 * [EpubNavigatorFragment]. In gate mode it counts forward page turns
 * (synthetic-position based, R-7.6) toward the required page count and
 * finishes with RESULT_OK once the target is reached (R-7.7).
 *
 * Counting is honor-system by default; optional strict mode adds a minimum
 * per-page dwell time and net-new-progress-only counting (R-7.10).
 */
@OptIn(ExperimentalReadiumApi::class)
@AndroidEntryPoint
class ReaderActivity : AppCompatActivity() {

    @Inject lateinit var bookRepository: BookRepository
    @Inject lateinit var readium: ReadiumProvider
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var logDao: CompletionLogDao

    private lateinit var binding: ActivityReaderBinding

    private var book: Book? = null
    private var publication: Publication? = null
    private var navigator: EpubNavigatorFragment? = null

    private val gateMode: Boolean by lazy { intent.getBooleanExtra(EXTRA_GATE_MODE, false) }
    private val requiredPages: Int by lazy { intent.getIntExtra(EXTRA_REQUIRED_PAGES, 0) }
    /** Pages already read this gate (carried over from a previous book, R-7.9). */
    private val carriedPages: Int by lazy { intent.getIntExtra(EXTRA_CARRIED_PAGES, 0) }

    // Counting state
    private var pagesReadState by mutableIntStateOf(0)
    private var lastPosition: Int? = null
    private var lastCountedTurnAt = 0L
    private var furthestProgression = 0.0
    private var gatePassed by mutableStateOf(false)
    private var bookFinished by mutableStateOf(false)
    private var showToc by mutableStateOf(false)
    private var bookTitle by mutableStateOf("")
    private var errorMessage by mutableStateOf<String?>(null)
    private var toc: List<Link> = emptyList()
    private val sessionStartedAt = SystemClock.elapsedRealtime()
    private var resultDelivered = false

    // Strict-mode settings snapshot (loaded before the navigator starts).
    private var strictMode = false
    private var dwellMillis = 3000L

    override fun onCreate(savedInstanceState: Bundle?) {
        // The navigator fragment can't be restored before the publication is
        // (asynchronously) reopened, so never let the FragmentManager try.
        supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
        super.onCreate(savedInstanceState)
        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (savedInstanceState != null) {
            supportFragmentManager.fragments.forEach {
                supportFragmentManager.beginTransaction().remove(it).commitNow()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finishWithResult(passed = gatePassed)
        })

        binding.readerOverlay.setContent { GatekeeperTheme { ReaderOverlay() } }

        lifecycleScope.launch { openBook() }
    }

    private suspend fun openBook() {
        val settings = settingsRepository.settings.first()
        strictMode = settings.readingStrictMode
        dwellMillis = settings.readingDwellSeconds * 1000L

        val bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1)
        val loaded = bookRepository.get(bookId) ?: run {
            errorMessage = "Book not found. It may have been removed."
            return
        }
        book = loaded
        bookTitle = loaded.title
        furthestProgression = loaded.furthestProgression

        val pub = when (val result = readium.openPublication(File(loaded.filePath))) {
            is Try.Success -> result.value
            is Try.Failure -> {
                errorMessage = "Couldn't open this book: ${result.value.message}"
                return
            }
        }
        publication = pub
        toc = pub.tableOfContents

        val initialLocator = loaded.lastLocatorJson
            ?.let { runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull() }

        val navigatorFactory = EpubNavigatorFactory(pub)
        val fragmentFactory = navigatorFactory.createFragmentFactory(
            initialLocator = initialLocator,
            listener = null,
        )
        supportFragmentManager.fragmentFactory = fragmentFactory
        supportFragmentManager.beginTransaction()
            .replace(
                binding.readerFragmentContainer.id,
                EpubNavigatorFragment::class.java,
                Bundle(),
                NAVIGATOR_TAG,
            )
            .commitNow()

        val nav = supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as EpubNavigatorFragment
        navigator = nav
        // Tap left/right edges (and volume/arrow keys) to turn pages.
        nav.addInputListener(DirectionalNavigationAdapter(nav))
        applyReaderPreferences(settings.readerFontSizePercent, settings.readerDarkTheme)

        nav.currentLocator
            .onEach { onLocatorChanged(it) }
            .launchIn(lifecycleScope)
    }

    /** R-7.6: each forward page turn (synthetic position increase) counts as one page. */
    private fun onLocatorChanged(locator: Locator) {
        val position = locator.locations.position
        val progression = locator.locations.totalProgression ?: 0.0
        val previous = lastPosition
        lastPosition = position

        if (position != null && previous != null && position > previous) {
            val now = SystemClock.elapsedRealtime()
            val dwellOk = !strictMode || now - lastCountedTurnAt >= dwellMillis
            val netNewOk = !strictMode || progression > furthestProgression
            if (dwellOk && netNewOk) {
                pagesReadState += 1
                lastCountedTurnAt = now
                if (gateMode && !gatePassed && carriedPages + pagesReadState >= requiredPages) {
                    gatePassed = true
                    finishWithResult(passed = true)
                }
            }
        }
        furthestProgression = max(furthestProgression, progression)

        // R-7.9: end of book reached before the target — offer to pick another book.
        val total = book?.totalPositions ?: 0
        if (gateMode && !gatePassed && total > 0 && position != null && position >= total) {
            bookFinished = true
        }
    }

    private fun applyReaderPreferences(fontSizePercent: Int, dark: Boolean) {
        navigator?.submitPreferences(
            EpubPreferences(
                fontSize = fontSizePercent / 100.0,
                theme = if (dark) Theme.DARK else Theme.LIGHT,
            )
        )
    }

    /** R-7.8: persist resume point and write a reading-session log. */
    private fun finishWithResult(passed: Boolean, pickAnother: Boolean = false) {
        if (resultDelivered) return
        resultDelivered = true

        val pagesRead = pagesReadState
        val durationSeconds = ((SystemClock.elapsedRealtime() - sessionStartedAt) / 1000).toInt()
        val currentBook = book
        val locatorJson = navigator?.currentLocator?.value?.toJSON()?.toString()

        val data = Intent()
            .putExtra(RESULT_PAGES_READ, pagesRead)
            .putExtra(RESULT_PICK_ANOTHER, pickAnother)
        setResult(if (passed) Activity.RESULT_OK else RESULT_INCOMPLETE, data)

        lifecycleScope.launch {
            if (currentBook != null) {
                bookRepository.update(
                    currentBook.copy(
                        lastLocatorJson = locatorJson ?: currentBook.lastLocatorJson,
                        furthestProgression = max(
                            currentBook.furthestProgression,
                            furthestProgression,
                        ),
                    )
                )
                if (pagesRead > 0) {
                    logDao.insert(
                        CompletionLog(
                            type = LogType.READING,
                            refId = currentBook.id,
                            detail = currentBook.title,
                            amount = pagesRead,
                            durationSeconds = durationSeconds,
                        )
                    )
                }
            }
            finish()
        }
    }

    @androidx.compose.runtime.Composable
    private fun ReaderOverlay() {
        Box(Modifier.fillMaxSize()) {
            // Top bar
            Surface(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                tonalElevation = 2.dp,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { finishWithResult(passed = gatePassed) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        bookTitle,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    IconButton(onClick = { adjustFontSize(-10) }) {
                        Icon(Icons.Filled.TextDecrease, contentDescription = "Smaller text")
                    }
                    IconButton(onClick = { adjustFontSize(+10) }) {
                        Icon(Icons.Filled.TextIncrease, contentDescription = "Larger text")
                    }
                    IconButton(onClick = { toggleTheme() }) {
                        Icon(Icons.Filled.DarkMode, contentDescription = "Toggle dark theme")
                    }
                    if (toc.isNotEmpty()) {
                        IconButton(onClick = { showToc = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.List,
                                contentDescription = "Table of contents",
                            )
                        }
                    }
                }
            }

            // Live gate counter (R-7.7)
            if (gateMode) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 4.dp,
                ) {
                    Text(
                        text = "${(carriedPages + pagesReadState).coerceAtMost(requiredPages)} / $requiredPages pages read this session",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            errorMessage?.let { message ->
                AlertDialog(
                    onDismissRequest = { finishWithResult(passed = false) },
                    title = { Text("Can't open book") },
                    text = { Text(message) },
                    confirmButton = {
                        TextButton(onClick = { finishWithResult(passed = false) }) { Text("Close") }
                    },
                )
            }

            if (bookFinished && gateMode && !gatePassed) {
                AlertDialog(
                    onDismissRequest = { bookFinished = false },
                    title = { Text("You finished this book!") },
                    text = {
                        Text(
                            "You read $pagesReadState of the pages needed. Pick another book " +
                                "to finish the gate — your progress carries over."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            finishWithResult(passed = false, pickAnother = true)
                        }) { Text("Pick another book") }
                    },
                    dismissButton = {
                        TextButton(onClick = { bookFinished = false }) { Text("Keep reading") }
                    },
                )
            }

            if (showToc) {
                AlertDialog(
                    onDismissRequest = { showToc = false },
                    title = { Text("Contents") },
                    text = {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(toc) { link ->
                                TextButton(onClick = {
                                    showToc = false
                                    goTo(link)
                                }) {
                                    Text(link.title ?: link.href.toString(), maxLines = 1)
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showToc = false }) { Text("Close") }
                    },
                )
            }
        }
    }

    private fun goTo(link: Link) {
        val pub = publication ?: return
        val nav = navigator ?: return
        pub.locatorFromLink(link)?.let { nav.go(it, animated = false) }
    }

    private fun adjustFontSize(deltaPercent: Int) {
        lifecycleScope.launch {
            val current = settingsRepository.settings.first()
            val newSize = (current.readerFontSizePercent + deltaPercent).coerceIn(50, 300)
            settingsRepository.setReaderFontSizePercent(newSize)
            applyReaderPreferences(newSize, current.readerDarkTheme)
        }
    }

    private fun toggleTheme() {
        lifecycleScope.launch {
            val current = settingsRepository.settings.first()
            val dark = !current.readerDarkTheme
            settingsRepository.setReaderDarkTheme(dark)
            applyReaderPreferences(current.readerFontSizePercent, dark)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        publication?.close()
        publication = null
    }

    companion object {
        private const val NAVIGATOR_TAG = "epub_navigator"
        const val EXTRA_BOOK_ID = "book_id"
        const val EXTRA_GATE_MODE = "gate_mode"
        const val EXTRA_REQUIRED_PAGES = "required_pages"
        const val EXTRA_CARRIED_PAGES = "carried_pages"
        const val RESULT_PAGES_READ = "pages_read"
        const val RESULT_PICK_ANOTHER = "pick_another"
        const val RESULT_INCOMPLETE = Activity.RESULT_FIRST_USER

        fun intent(
            context: Context,
            bookId: Long,
            gateMode: Boolean = false,
            requiredPages: Int = 0,
            carriedPages: Int = 0,
        ): Intent = Intent(context, ReaderActivity::class.java)
            .putExtra(EXTRA_BOOK_ID, bookId)
            .putExtra(EXTRA_GATE_MODE, gateMode)
            .putExtra(EXTRA_REQUIRED_PAGES, requiredPages)
            .putExtra(EXTRA_CARRIED_PAGES, carriedPages)
    }
}
