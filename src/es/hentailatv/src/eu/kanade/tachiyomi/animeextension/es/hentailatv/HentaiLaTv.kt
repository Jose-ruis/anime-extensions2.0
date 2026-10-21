package eu.kanade.tachiyomi.animeextension.es.hentailatv

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import aniyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.utils.addListPreference
import keiyoushi.utils.getPreferencesLazy
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class HentaiLaTv :
    AnimeHttpSource(),
    ConfigurableAnimeSource {

    override val name = "HentaiLa.tv"
    override val baseUrl = "https://hentaila.tv"
    override val lang = "es"
    override val supportsLatest = true
    override val versionId = 2

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val client = network.client.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val playlistUtils by lazy { PlaylistUtils(client, headers) }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/directorio?page=$page", headers)

    private val listItemSelector = "div.elemento, article.anime-card, div.col-6 div.item"

    override fun popularAnimeParse(response: Response): AnimesPage = parseAnimeListing(response.asJsoup())

    private fun parseAnimeListing(document: Document): AnimesPage {
        val animes = document.select("div.row div.item, div.anime-card, article").mapNotNull { element ->
            element.selectFirst("a")?.let { link ->
                SAnime.create().apply {
                    setUrlWithoutDomain(link.absUrl("href"))
                    title = element.selectFirst(".title, h3, h2, .name")?.text() ?: link.attr("title")
                    thumbnail_url = element.selectFirst("img")?.let { img ->
                        img.absUrl("data-src").ifEmpty { img.absUrl("src") }
                    }
                }
            }
        }
        val hasNextPage = document.selectFirst("ul.pagination-list li.active + li, a.next, .pagination .fa-angle-right") != null
        return AnimesPage(animes, hasNextPage)
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/animes?page=$page", headers)

    override fun latestUpdatesParse(response: Response): AnimesPage = parseAnimeListing(response.asJsoup())

    // =============================== Search ===============================

    override fun getFilterList(): AnimeFilterList = AnimeFilterList()

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("buscar")
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchAnimeParse(response: Response): AnimesPage = parseAnimeListing(response.asJsoup())

    // =========================== Anime Details ============================

    override fun getAnimeUrl(anime: SAnime): String = baseUrl + anime.url

    override fun animeDetailsParse(response: Response): SAnime {
        val document = response.asJsoup()
        return SAnime.create().apply {
            title = document.selectFirst("div.s-title h1, h1.title")?.text() ?: ""
            thumbnail_url = document.selectFirst("div.thumb img, .anime-poster img")?.absUrl("src")
            description = document.selectFirst("div.sinopsis, div.description")?.text()
            genre = document.select("div.genres a, .tags a").joinToString { it.text() }
            status = parseStatus(document.selectFirst("span.status, .estado")?.text())
        }
    }

    private fun parseStatus(status: String?): Int {
        val value = status?.lowercase().orEmpty()
        return when {
            "emisi" in value || "ongoing" in value -> SAnime.ONGOING
            "finaliz" in value || "complet" in value -> SAnime.COMPLETED
            else -> SAnime.UNKNOWN
        }
    }

    // ============================== Episodes ==============================

    override fun seasonListParse(response: Response): List<SAnime> = throw UnsupportedOperationException()

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        // HentaiLa suele listar los episodios en bloques o listados numerados
        val episodeElements = document.select("ul.episodes-list li, div.episios a, .list-eps li a, ul#chapters li a")

        if (episodeElements.isEmpty()) {
            // Si la página del anime es directamente el episodio o usa otro selector
            val currentEp = SEpisode.create().apply {
                url = document.location().removePrefix(baseUrl)
                episode_number = 1F
                name = document.selectFirst("h1.title")?.text() ?: "Episodio 1"
            }
            return listOf(currentEp)
        }

        return episodeElements.map { element ->
            val link = if (element.tagName() == "a") element else element.selectFirst("a")!!
            SEpisode.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                val epText = element.text()
                val number = EPISODE_NUMBER_REGEX.find(epText)?.value?.toFloatOrNull() ?: 1F
                episode_number = number
                name = "Episodio ${number.toString().removeSuffix(".0")}"
            }
        }.sortedByDescending { it.episode_number }
    }

    override fun getEpisodeUrl(episode: SEpisode): String = baseUrl + episode.url

    // ============================ Video Links =============================

    override fun hosterListRequest(episode: SEpisode): Request = GET(baseUrl + episode.url, headers)

    override fun hosterListParse(response: Response): List<Hoster> {
        val document = response.asJsoup()

        // Extrae reproductores o iframes incrustados típicos de la web
        val streamHosters = document.select("div.player-options script, div.video-player iframe, div.embed-responsive iframe, ul.nav-tabs li a").mapNotNull { server ->
            val embedUrl = server.attr("data-src").ifEmpty { server.attr("src").ifEmpty { server.attr("data-url") } }
            if (embedUrl.isEmpty()) return@mapNotNull null
            Hoster(
                hosterUrl = embedUrl,
                hosterName = server.text().ifEmpty { embedUrl.toHttpUrl().host },
                internalData = TYPE_STREAM,
            )
        }

        // Búsqueda alternativa de reproductores si están embebidos directamente en la vista
        val fallbackHosters = document.select("iframe").mapNotNull { iframe ->
            val src = iframe.absUrl("src")
            if (src.isEmpty()) return@mapNotNull null
            Hoster(
                hosterUrl = src,
                hosterName = src.toHttpUrl().host,
                internalData = TYPE_STREAM,
            )
        }

        val downloads = document.select("div.downloads-list a, a.btn-download").map { link ->
            Hoster(
                hosterUrl = link.absUrl("href"),
                hosterName = "Descarga ${link.text()}".trim(),
                internalData = TYPE_DOWNLOAD,
            )
        }

        return (streamHosters + fallbackHosters).distinctBy { it.hosterUrl } + downloads
    }

    override suspend fun getVideoList(hoster: Hoster): List<Video> {
        val url = hoster.hosterUrl
        return when (hoster.internalData) {
            TYPE_DOWNLOAD -> listOf(
                Video(
                    videoUrl = url,
                    videoTitle = hoster.hosterName,
                    headers = headers,
                ),
            )
            else -> when {
                url.contains(".m3u8") -> playlistUtils.extractFromHls(
                    playlistUrl = url,
                    referer = "$baseUrl/",
                    videoNameGen = { quality -> "${hoster.hosterName} - $quality" },
                )
                url.contains(".mp4") -> listOf(
                    Video(
                        videoUrl = url,
                        videoTitle = "${hoster.hosterName} - mp4",
                        headers = headers,
                    ),
                )
                else -> videosFromEmbed(url, hoster.hosterName)
            }
        }
    }

    private suspend fun videosFromEmbed(embedUrl: String, label: String): List<Video> = try {
        val document = client.get(embedUrl, headers).asJsoup()
        document.select("video source[src], source[src]").map { source ->
            Video(
                videoUrl = source.absUrl("src"),
                videoTitle = "$label - ${source.attr("label").ifEmpty { source.attr("res").ifEmpty { "Video" } }}",
                headers = headers,
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    private val preferredQuality: String
        get() = preferences.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT) ?: PREF_QUALITY_DEFAULT

    override fun List<Video>.sortVideos(): List<Video> = sortedWith(
        compareByDescending<Video> { it.videoTitle.contains(preferredQuality) }
            .thenByDescending { video ->
                QUALITY_REGEX.find(video.videoTitle)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            },
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addListPreference(
            key = PREF_QUALITY_KEY,
            default = PREF_QUALITY_DEFAULT,
            title = "Calidad preferida",
            summary = "%s",
            entries = PREF_QUALITY_ENTRIES.map { "${it}p" },
            entryValues = PREF_QUALITY_ENTRIES,
        )
    }

    companion object {
        private const val TYPE_STREAM = "stream"
        private const val TYPE_DOWNLOAD = "download"

        private const val PREF_QUALITY_KEY = "preferred_quality"
        private val PREF_QUALITY_ENTRIES = listOf("1080", "720", "480", "360")
        private val PREF_QUALITY_DEFAULT = PREF_QUALITY_ENTRIES.first()

        private val QUALITY_REGEX = Regex("""(\d{3,4})p""")
        private val EPISODE_NUMBER_REGEX = Regex("""\d+(\.\d+)?""")
    }
}
