package com.bleelblep.glyphsharge.data

import android.content.Context
import android.util.Log
import com.bleelblep.glyphsharge.glyph.device.DeviceType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The animation store: a catalogue fetched over HTTPS, cached on disk, and
 * nothing else.
 *
 * **No backend, no account, no payment.** The catalogue is a JSON file in a
 * public repository and the scripts beside it, so publishing one is a pull
 * request and reading one is a GET. `HttpURLConnection` is used directly
 * because the project depends on no HTTP client and adding one for two calls
 * would be a dependency bought for nothing.
 *
 * ### What the cache is for
 *
 * The index is kept in `filesDir/glyph_store` together with the `ETag` and
 * `Last-Modified` the server last sent. [refresh] sends them back as
 * `If-None-Since` / `If-None-Match`, so an unchanged catalogue costs one
 * `304` and no body. A `304` is a success, not a failure — treating it as an
 * error would leave the store permanently broken after the first run.
 *
 * The cache also means the screen opens with something on it offline. Which is
 * the difference between [LoadState] carrying the data and carrying an error,
 * and why the two are separate rather than one nullable field.
 *
 * ### What a hash is for
 *
 * [sha256] in the index is checked before a script is saved, and a file that
 * does not match is refused. This is not defence against a hostile catalogue —
 * a hostile catalogue controls the hash as well — it is the check that the
 * downloaded bytes are the ones the catalogue described, which is what catches
 * a truncated download, a CDN serving a stale object, and an index edited
 * without its file.
 */
@Singleton
class ScriptStoreRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "ScriptStore"

        /**
         * The catalogue index.
         *
         * One request, one file. Everything the store knows about itself is
         * in this JSON — name, author, licence, the models it works on, and
         * where to fetch the script — so the phone never walks the repository
         * and never asks the CDN what a pack looks like. A catalogue of
         * fifty animations costs the same single `GET` as one.
         *
         * A public repository over jsDelivr rather than GitHub Pages: the CDN
         * serves the file with long-lived caching headers, which a `gh-pages`
         * branch does not. Pinning a commit instead of a branch would make the
         * URL change on every publish, so the branch stays and freshness comes
         * from `ETag`.
         *
         * The path is case-sensitive, and it is written here rather than
         * derived — a store that silently 404s because a repository was
         * renamed would look like an empty catalogue, not a broken URL.
         */
        const val INDEX_URL =
            "https://raw.githubusercontent.com/hardWorker254/Glyph-Sharge-Store/refs/heads/main/index.json"

        private const val STORE_DIR = "glyph_store"
        private const val INDEX_FILE = "index.json"
        private const val ETAG_FILE = "index.etag"
        private const val MODIFIED_FILE = "index.modified"

        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000

        /** Refuses a catalogue larger than this before reading it into memory. */
        private const val MAX_INDEX_BYTES = 2 * 1024 * 1024

        /** Refuses a script larger than this; an animation is a few KB. */
        private const val MAX_SCRIPT_BYTES = 512 * 1024

        /** Index format this build understands. */
        private const val SUPPORTED_FORMAT = 1

        private const val HTTP_OK = 200
        private const val HTTP_NOT_MODIFIED = 304

        /** A published hash is 64 lowercase hex characters, or it is wrong. */
        private val SHA256 = Regex("[0-9a-f]{64}")
    }

    // region State

    /** Why the last load ended the way it did, as the screen needs to say. */
    sealed interface LoadState {
        /** Nothing fetched yet. */
        data object Idle : LoadState

        /** A request is in flight. */
        data object Loading : LoadState

        /** Fresh data, from the network. */
        data object UpToDate : LoadState

        /**
         * Data, but from the cache rather than the network.
         *
         * Separate from [Failed] because the screen can still be useful here:
         * a catalogue is worth showing with a note, and worth not showing at
         * all when there is nothing cached.
         */
        data object Stale : LoadState

        /** The request failed and there is nothing cached to fall back on. */
        data class Failed(val reason: String) : LoadState
    }

    private val _items = MutableStateFlow<List<StoreItem>>(emptyList())
    val items: StateFlow<List<StoreItem>> = _items.asStateFlow()

    private val _state = MutableStateFlow<LoadState>(LoadState.Idle)
    val state: StateFlow<LoadState> = _state.asStateFlow()

    // endregion

    // region Reading the catalogue

    /**
     * Fetches the index, falling back to the cache.
     *
     * Never throws: a network layer whose failure mode is an exception puts a
     * try/catch in every caller, and the two callers here are a screen opening
     * and a user pulling to refresh.
     */
    suspend fun refresh(): LoadState = withContext(Dispatchers.IO) {
        if (_items.value.isEmpty()) _state.value = LoadState.Loading

        try {
            when (val fetched = fetchIndex()) {
                is FetchResult.Fresh -> {
                    _items.value = fetched.items
                    _state.value = LoadState.UpToDate
                    LoadState.UpToDate
                }

                is FetchResult.NotModified -> {
                    // A 304 means "what you sent me is still current". If we
                    // have nothing to show, that statement is useless to us —
                    // there is no cached copy for it to be true *about* — so
                    // ask again without the validators rather than report
                    // success over an empty screen. Once per refresh: a server
                    // answering 304 to a bare request is not a server to keep
                    // arguing with.
                    if (_items.value.isEmpty() && !readCache()) {
                        clearValidators()
                        val forced = fetchIndex()
                        (forced as? FetchResult.Fresh)?.let { _items.value = it.items }
                    }

                    // `UpToDate` only if there is something on screen to be
                    // up to date about.
                    //
                    // The forced fetch above can still come back `NotModified` —
                    // a server answering 304 to a bare request is not one to
                    // keep arguing with, and it was already given that chance.
                    // Reporting success there left the screen showing an empty
                    // catalogue with no spinner, no error and nothing to pull,
                    // which reads as "there is nothing to install" rather than
                    // as the failure it is.
                    if (_items.value.isEmpty()) {
                        Log.w(TAG, "The server answered 304 but the catalogue is still empty")
                        LoadState.Failed(
                            "The catalogue server reported nothing new and this device has " +
                                "no copy of it. Try again later.",
                        ).also { _state.value = it }
                    } else {
                        _state.value = LoadState.UpToDate
                        LoadState.UpToDate
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Index fetch failed", e)
            val cached = readCache()
            _state.value = if (cached) LoadState.Stale else LoadState.Failed(e.readableReason())
            _state.value
        }
    }

    /**
     * The cached catalogue, without touching the network.
     *
     * Called when the screen opens so it has something to draw before the
     * request finishes, and called again on failure so a store that has been
     * seen once still opens with no signal.
     */
    suspend fun loadFromCache(): Boolean = withContext(Dispatchers.IO) { readCache() }

    private fun readCache(): Boolean {
        val file = File(storeDir, INDEX_FILE)
        if (!file.exists()) return false

        val items = runCatching { parseIndex(file.readText()) }
            .onFailure { Log.w(TAG, "Cached index unreadable", it) }
            .getOrDefault(emptyList())

        if (items.isEmpty()) return false

        // Painted only over an empty list. `loadFromCache` and `refresh`
        // run concurrently on open, and a disk read that finishes after a
        // successful fetch would otherwise overwrite the fresh catalogue
        // with the older one that fetch just replaced — with the newer
        // validators already on disk, so every later `304` would agree
        // with the stale list until the next launch. A caller falling
        // back here on failure already holds the newer of the two, so it
        // keeps it; the answer it needs is whether a cache exists at all.
        if (_items.value.isEmpty()) _items.value = items
        return true
    }

    /**
     * Writes the index body to the cache, replacing it atomically.
     *
     * Through a temporary file and a rename, because the alternative — writing
     * in place — can leave a half-written index if the process dies mid-write,
     * and a truncated file that fails to parse is a cache that reports itself
     * as absent. The rename is what makes the old copy or the new one the
     * only thing ever visible.
     *
     * `internal` so the round trip can be asserted directly: the pairing of
     * "the ETag is stored" and "the body is stored" is the whole contract
     * [refresh] depends on, and it is invisible to any test that does not go
     * to the network.
     */
    internal fun writeCache(body: String) {
        runCatching {
            val target = File(storeDir, INDEX_FILE)
            val temp = File(storeDir, "$INDEX_FILE.tmp")
            temp.writeText(body)
            if (!temp.renameTo(target)) {
                // Some filesystems refuse a rename onto an existing file.
                target.delete()
                check(temp.renameTo(target)) { "Could not replace the cached index." }
            }
        }.onFailure { Log.w(TAG, "Could not cache the index", it) }
    }

    // endregion

    // region Downloading a script

    /**
     * Downloads [item]'s file and checks it against the hash the catalogue
     * published.
     *
     * The source comes back as text, not as a saved animation: the caller
     * decides whether this is a test run or an install, and only the install
     * goes through
     * [CustomAnimationRepository.importFrom].
     *
     * @throws IOException when the download fails or the hash does not match
     */
    suspend fun download(item: StoreItem): String = withContext(Dispatchers.IO) {
        val bytes = readAll(URL(item.url))

        val actual = bytes.sha256()
        if (!actual.equals(item.sha256, ignoreCase = true)) {
            // The catalogue described one file and the network served another.
            // Refusing is the whole point of publishing a hash, so this is a
            // failure rather than a warning.
            throw IOException(
                "The downloaded file does not match the catalogue (expected " +
                    "${item.sha256.take(12)}…, got ${actual.take(12)}…).",
            )
        }

        bytes.toString(Charsets.UTF_8)
    }

    // endregion

    // region Networking

    private sealed interface FetchResult {
        data class Fresh(val items: List<StoreItem>) : FetchResult
        data object NotModified : FetchResult
    }

    private fun fetchIndex(): FetchResult {
        val connection = open(URL(INDEX_URL))
        try {
            readValidators()?.let { (etag, modified) ->
                etag?.let { connection.setRequestProperty("If-None-Match", it) }
                modified?.let { connection.setRequestProperty("If-Modified-Since", it) }
            }

            when (val code = connection.responseCode) {
                HTTP_NOT_MODIFIED -> return FetchResult.NotModified

                HTTP_OK -> {
                    val body = connection.readBounded(MAX_INDEX_BYTES)
                    val items = parseIndex(body)
                    // **Both halves of the cache, in that order.** The
                    // validators go down only after the body is safely on disk:
                    // written the other way round, a crash in between leaves an
                    // ETag the server will answer 304 to and no body to answer
                    // it with, which is an empty store that never recovers.
                    writeCache(body)
                    writeValidators(connection)
                    return FetchResult.Fresh(items)
                }

                else -> throw IOException("The store answered $code.")
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * The last validators, as an `(etag, lastModified)` pair.
     *
     * Both or neither: a stale `If-Modified-Since` against a current ETag is
     * how a catalogue that did change gets reported as unchanged by whichever
     * header the server happens to honour first.
     */
    private fun readValidators(): Pair<String?, String?>? {
        val etag = File(storeDir, ETAG_FILE).takeIf { it.exists() }?.readText()?.trim()
        val modified = File(storeDir, MODIFIED_FILE).takeIf { it.exists() }?.readText()?.trim()
        if (etag.isNullOrBlank() && modified.isNullOrBlank()) return null
        return etag to modified
    }

    private fun writeValidators(connection: HttpURLConnection) = writeValidators(
        connection.getHeaderField("ETag")?.trim()?.takeIf { it.isNotBlank() },
        connection.getHeaderField("Last-Modified")?.trim()?.takeIf { it.isNotBlank() },
    )

    /**
     * The validators a response carried, stored together or not at all.
     *
     * `internal` for the same reason [writeCache] is: the pairing is the
     * contract [readValidators] documents, and a test has to be able to
     * hand over a response that carries only one of the two to see that
     * the other's file does not survive it.
     */
    internal fun writeValidators(etag: String?, modified: String?) {
        // Both or neither. A response carrying only one header would
        // leave the other's file on disk from an earlier response, and
        // the next request would then send a fresh validator against a
        // stale one — the mixed pair that makes a catalogue which did
        // change answer `304`. Dropping both costs one uncached
        // request; keeping the pair costs the catalogue.
        if ((etag == null) || (modified == null)) {
            clearValidators()
        } else {
            File(storeDir, ETAG_FILE).writeText(etag)
            File(storeDir, MODIFIED_FILE).writeText(modified)
        }
    }

    /**
     * Forgets the validators, so the next request is unconditional.
     *
     * The escape from a `304` that has nothing behind it. Deleting the ETag
     * rather than merely not sending it matters: the file is what
     * [readValidators] reads, so leaving it behind would put the same dead end
     * back on the following launch.
     */
    private fun clearValidators() {
        runCatching {
            File(storeDir, ETAG_FILE).delete()
            File(storeDir, MODIFIED_FILE).delete()
        }.onFailure { Log.w(TAG, "Could not clear the cache validators", it) }
    }

    private fun open(url: URL): HttpURLConnection {
        if (url.protocol != "https") {
            // The catalogue is public and unsigned, so plain HTTP would be a
            // way for anything on the path to choose what gets installed.
            throw IOException("The store must be reached over HTTPS.")
        }

        return (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json, text/plain")
        }
    }

    /**
     * Reads at most [limit] bytes.
     *
     * The bound is what stops a wrong URL from parking the app on a stream that
     * never ends: without it, `readBytes()` waits for a body that a
     * misconfigured catalogue happily promises and never delivers.
     */
    private fun HttpURLConnection.readBounded(limit: Int): String {
        val stream = inputStream
        stream.use { input ->
            val bytes = input.readNBytes(limit + 1)
            if (bytes.size > limit) throw IOException("The file is larger than $limit bytes.")
            return bytes.toString(Charsets.UTF_8)
        }
    }

    private fun readAll(url: URL): ByteArray {
        val connection = open(url)
        return try {
            if (connection.responseCode != HTTP_OK) {
                throw IOException(
                    "The file could not be downloaded (HTTP ${connection.responseCode}).",
                )
            }
            connection.inputStream.use { it.readNBytes(MAX_SCRIPT_BYTES + 1) }
                .also {
                    if (it.size > MAX_SCRIPT_BYTES) {
                        throw IOException("The file is larger than $MAX_SCRIPT_BYTES bytes.")
                    }
                }
        } finally {
            connection.disconnect()
        }
    }

    // endregion

    // region Parsing

    /**
     * Reads the index.
     *
     * A bad entry is skipped rather than failing the whole catalogue: one
     * malformed item should cost the user that item, not the store. An index
     * whose `format` this build does not know is refused outright instead,
     * because its fields may well be named differently.
     */
    fun parseIndex(body: String): List<StoreItem> {
        val root = JSONObject(body)
        val format = root.optInt("format", SUPPORTED_FORMAT)
        if (format != SUPPORTED_FORMAT) {
            throw IOException("The catalogue is format $format; this build reads $SUPPORTED_FORMAT.")
        }

        val items = root.optJSONArray("items") ?: JSONArray()
        val parsed = (0 until items.length()).mapNotNull { index ->
            runCatching { parseItem(items.optJSONObject(index) ?: return@mapNotNull null) }
                .onFailure { Log.w(TAG, "Catalogue entry $index skipped", it) }
                .getOrNull()
        }

        // Duplicates dropped here, and not rendered.
        //
        // The id is the identity the whole install path keys on — it is what
        // `StoreInstallRepository` records against and what the "Update" badge
        // looks up. Two catalogue entries sharing one therefore cannot both be
        // installed into the history, and the second silently replaces the
        // first's record: the user installs from card A, card B still says
        // "Install", they press it, and their own script is duplicated on the
        // phone as `Wave (2)` while the store continues to believe A is what
        // they have.
        //
        // First wins, so the outcome does not depend on how a later publish
        // happened to order the array. A malformed entry is dropped instead,
        // because one broken item should cost the user that item and not the
        // store — but a duplicate id is not malformed, it is a contradiction
        // between two entries that both parsed.
        val seen = HashSet<String>(parsed.size)
        return parsed.filter { seen.add(foldId(it.id)) }
            .also { dropped ->
                if (dropped.size != parsed.size) {
                    Log.w(
                        TAG,
                        "Catalogue repeats ${parsed.size - dropped.size} item id(s); " +
                            "the duplicates were dropped",
                    )
                }
            }
    }

    /**
     * The one place a catalogue id is folded for comparison.
     *
     * Matching [StoreInstallRepository]'s own
     * folding, because the two have to agree: if the store deduplicates on one
     * rule and records history on the other, the duplicate this removes comes
     * straight back as two history rows.
     */
    private fun foldId(id: String): String = id.trim().lowercase(Locale.ROOT)

    private fun parseItem(item: JSONObject): StoreItem? {
        val id = item.optString("id").trim()
        val url = item.optString("url").trim()
        val sha256 = item.optString("sha256").trim()

        // The three fields an install cannot do without: an id to key the item
        // on, somewhere to fetch from, and the hash to check what arrived.
        if (id.isBlank() || url.isBlank() || sha256.isBlank()) return null
        if (!sha256.matches(SHA256)) return null

        val devices = runCatching {
            StoreItem.parseDevices(
                (item.optJSONArray("devices") ?: JSONArray())
                    .let { array ->
                        (0 until array.length()).joinToString(",") { array.optString(it) }
                    }
                    .ifBlank { item.optString("devices") },
            )
        }.getOrDefault(DeviceType.entries.toSet())

        return StoreItem(
            id = id,
            name = item.optString("name").trim().ifBlank { id },
            author = item.optString("author").trim(),
            license = item.optString("license").trim(),
            description = item.optString("description").trim(),
            url = url,
            sha256 = sha256.lowercase(),
            devices = devices,
            version = item.optInt("version", 1),
        )
    }

    // endregion

    private val storeDir: File
        get() = File(context.filesDir, STORE_DIR).apply { if (!exists()) mkdirs() }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(this)
            .joinToString("") { "%02x".format(it) }

    private fun Exception.readableReason(): String =
        message?.takeIf { it.isNotBlank() } ?: this::class.simpleName ?: "Unknown error"
}