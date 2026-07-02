package com.gatekeeper.app.reader

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.gatekeeper.app.data.db.Book
import com.gatekeeper.app.data.db.BookDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.util.Try
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

sealed class ImportResult {
    data class Success(val book: Book) : ImportResult()
    /** R-7.3: DRM-protected (LCP) EPUBs are rejected with a clear message. */
    data object DrmProtected : ImportResult()
    data class Error(val message: String) : ImportResult()
}

/**
 * EPUB library (Section 7). Files are copied into app-private storage on
 * import so access is stable even if the original document goes away (R-7.1).
 */
@Singleton
class BookRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val readium: ReadiumProvider,
) {
    val books: Flow<List<Book>> = bookDao.observeAll()

    suspend fun get(id: Long): Book? = bookDao.get(id)

    suspend fun update(book: Book) = bookDao.update(book)

    private fun booksDir(): File = File(context.filesDir, "books").apply { mkdirs() }
    private fun coversDir(): File = File(context.filesDir, "covers").apply { mkdirs() }

    suspend fun importEpub(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val destination = File(booksDir(), "${UUID.randomUUID()}.epub")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext ImportResult.Error("Could not open the selected file.")

            if (isLcpProtected(destination)) {
                destination.delete()
                return@withContext ImportResult.DrmProtected
            }

            val publication = when (val result = readium.openPublication(destination)) {
                is Try.Success -> result.value
                is Try.Failure -> {
                    destination.delete()
                    return@withContext ImportResult.Error(
                        "Not a readable DRM-free EPUB: ${result.value.message}"
                    )
                }
            }

            // R-7.2: persist title, author, cover and total synthetic positions.
            val title = publication.metadata.title ?: destination.nameWithoutExtension
            val author = publication.metadata.authors.firstOrNull()?.name
            val totalPositions = publication.positions().size
            val coverPath = publication.cover()?.let { saveCover(it) }
            publication.close()

            val book = Book(
                title = title,
                author = author,
                coverPath = coverPath,
                filePath = destination.absolutePath,
                totalPositions = totalPositions,
            )
            val id = bookDao.insert(book)
            ImportResult.Success(book.copy(id = id))
        } catch (e: Exception) {
            destination.delete()
            ImportResult.Error(e.message ?: "Import failed.")
        }
    }

    /** LCP-protected EPUBs carry a META-INF/license.lcpl entry (R-7.3). */
    private fun isLcpProtected(file: File): Boolean = runCatching {
        ZipFile(file).use { zip -> zip.getEntry("META-INF/license.lcpl") != null }
    }.getOrDefault(false)

    private fun saveCover(bitmap: Bitmap): String? = runCatching {
        val file = File(coversDir(), "${UUID.randomUUID()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }
        file.absolutePath
    }.getOrNull()

    suspend fun delete(book: Book) = withContext(Dispatchers.IO) {
        runCatching { File(book.filePath).delete() }
        book.coverPath?.let { runCatching { File(it).delete() } }
        bookDao.delete(book)
    }
}
