package com.gatekeeper.app.reader

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.Error
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared Readium plumbing: opens EPUB publications from app-private files.
 * (The HTTP client is required by Readium's constructor signatures but is
 * never used — all books are local files and the app has no INTERNET
 * permission, so nothing can leave the device.)
 */
@Singleton
class ReadiumProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val httpClient = DefaultHttpClient()

    val assetRetriever = AssetRetriever(context.contentResolver, httpClient)

    private val publicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            context,
            httpClient = httpClient,
            assetRetriever = assetRetriever,
            pdfFactory = null, // PDF is out of scope for v1 (Section 16).
        ),
    )

    suspend fun openPublication(file: File): Try<Publication, Error> {
        val asset = assetRetriever.retrieve(file.toUrl())
            .getOrElse { return Try.failure(it) }
        return publicationOpener.open(asset, allowUserInteraction = false)
    }
}
