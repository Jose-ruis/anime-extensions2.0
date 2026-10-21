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
import java.util.Locale

class HentaiLaTv : AnimeHttpSource(), ConfigurableAnimeSource {
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

    // The site is WordPress-based: its real archive pagination is /page/N/.
    private fun catalogueRequest(page: Int): Request =
        if (page <= 1) GET("$baseUrl/", headers) else GET("$baseUrl/page/$page/", headers)

    override fun popularAnimeRequest(page: Int): Request = catalogueRequest(page)

    override fun popularAnimeParse(response: Response): AnimesPage = parseAnimeListing(response.asJsoup())

    override fun latestUpdatesRequest(page: Int): Request = catalogueRequest(page)

    override fun latestUpdatesParse(response: Response): AnimesPage = parseAnimeListing(response.asJsoup())

    private fun parseAnimeListing(document: Document): AnimesPage {
        val anime = document.select(".item_card").mapNotNull { item ->
            val link = item.selectFirst("a[href]") ?: return@mapNotNull null
            val title = item.selectFirst(".card__title")?.text()?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: link.attr("title").takeIf(String::isNotEmpty)
                ?: return@mapNotNull null

            SAnime.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                this.title = title
                thumbnail_url = item.selectFirst(".card__cover img, img")?.let { image ->
                    val attribute = if (image.hasAttr("data-src")) "data-src" else "src"
                    image.absUrl(attribute).takeIf(String::isNotEmpty)
                }
            }
        }.distinctBy { it.url }

        val hasNextPage = document.selectFirst(".wp-pagenavi a.nextpostslink") != null
        return AnimesPage(anime, hasNextPage)
    }

    override fun getFilterList(): AnimeFilterList = AnimeFilterList()

    // WordPress search uses ?s=; /buscar?q= returns 404 on the live site.
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("s", query.trim())
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchAnimeParse(response: Response): AnimesPage = parseAnimeListing(response.asJsoup())

    override fun getAnimeUrl(anime: SAnime): String = baseUrl + anime.url

    override fun animeDetailsParse(response: Response): SAnime {
        val document = response.asJsoup()
        return SAnime.create().apply {
            url = response.request.url.encodedPath
            title = document.selectFirst(".hentai__header h1, h1")?.text()?.trim().orEmpty()
            thumbnail_url = document.selectFirst(".hentai_cover img, .hentai__cover img")?.let { image ->
                val attribute = if (image.hasAttr("data-src")) "data-src" else "src"
                image.absUrl(attribute).takeIf(String::isNotEmpty)
            }
            description = document.selectFirst(".vraven_text.single")?.text()?.trim()
            genre = document.select(".single_data a[rel=tag]").joinToString { it.text() }
            initialized = true
        }
    }

    override fun seasonListParse(response: Response): List<SAnime> = throw UnsupportedOperationException()

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val episodeLinks = document.select(".hentai__episodes li.hentai__chapter a[href]")
        if (episodeLinks.isEmpty()) return emptyList()

        return episodeLinks.map { link ->
            val label = link.text().trim()
            val number = EPISODE_NUMBER_REGEX.find(label)?.value?.toFloatOrNull() ?: 0F
            SEpisode.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                episode_number = number
                name = label.ifEmpty { "Episodio ${number.toInt()}" }
            }
        }.sortedByDescending { it.episode_number }
    }

    override fun getEpisodeUrl(episode: SEpisode): String = baseUrl + episode.url

    override fun hosterListRequest(episode: SEpisode): Request = GET(getEpisodeUrl(episode), headers)

    override fun hosterListParse(response: Response): List<Hoster> {
        val document = response.asJsoup()
        val streams = document.select("iframe[src], iframe[data-src]").mapNotNull { iframe ->
            val attribute = if (iframe.hasAttr("src")) "src" else "data-src"
            val embedUrl = iframe.absUrl(attribute)
            if (embedUrl.isBlank() || !embedUrl.startsWith("http")) return@mapNotNull null
            Hoster(
                hosterUrl = embedUrl,
                hosterName = embedUrl.toHttpUrl().host,
                internalData = TYPE_STREAM,
            )
        }

        // Only label true media URLs as downloads; a download landing page is not a video file.
        val downloads = document.select("a[href]").mapNotNull { link ->
            val url = link.absUrl("href")
            val path = url.substringBefore('?').lowercase(Locale.ROOT)
            if (!path.endsWith(".mp4") && !path.endsWith(".m3u8")) return@mapNotNull null
            Hoster(hosterUrl = url, hosterName = "Descarga ${link.text()}".trim(), internalData = TYPE_DOWNLOAD)
        }

        return (streams + downloads).distinctBy { it.hosterUrl }
    }

    override suspend fun getVideoList(hoster: Hoster): List<Video> =
        when (hoster.internalData) {
            TYPE_DOWNLOAD -> videosFromMediaUrl(hoster.hosterUrl, hoster.hosterName)
            else -> videosFromEmbed(hoster.hosterUrl, hoster.hosterName)
        }

    private suspend fun videosFromEmbed(embedUrl: String, label: String): List<Video> {
        val document = client.get(embedUrl, headers).asJsoup()
        val mediaUrls = buildList {
            // Restrict to sources inside <video>; a loose source[src] selector also picked audio.
            document.select("video[src], video source[src], video source[data-src]").forEach { source ->
                val attribute = when {
                    source.hasAttr("src") -> "src"
                    source.hasAttr("data-src") -> "data-src"
                    else -> return@forEach
                }
                source.absUrl(attribute).takeIf(String::isNotEmpty)?.let(::add)
            }
            document.select("meta[property=og:video], meta[property=og:video:url]")
                .mapNotNull { it.attr("content").takeIf(String::isNotEmpty) }
                .forEach(::add)
            val mediaRegex = Regex(
                """https?://[^\s"'<>]+?\.(?:m3u8|mp4)(?:\?[^\s"'<>]*)?""",
                RegexOption.IGNORE_CASE,
            )
            document.select("script").forEach { script ->
                mediaRegex.findAll(script.data().replace("\\/", "/")).forEach { match ->
                    add(match.value)
                }
            }
        }.distinct()

        val videos = mediaUrls.flatMap { videosFromMediaUrl(it, label) }
        if (videos.isEmpty()) throw IllegalStateException("No se encontró una pista de video reproducible")
        return videos
    }

    private suspend fun videosFromMediaUrl(url: String, label: String): List<Video> {
        val path = url.substringBefore('?').lowercase(Locale.ROOT)
        return when {
            path.endsWith(".m3u8") -> playlistUtils.extractFromHls(
                playlistUrl = url,
                referer = "$baseUrl/",
                videoNameGen = { quality -> "$label - $quality" },
            )
            path.endsWith(".mp4") -> listOf(
                Video(videoUrl = url, videoTitle = "$label - MP4", headers = headers),
            )
            else -> emptyList()
        }
    }

    private val preferredQuality: String
        get() = preferences.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT) ?: PREF_QUALITY_DEFAULT

    override fun List<Video>.sortVideos(): List<Video> = sortedWith(
        compareByDescending<Video> { it.videoTitle.contains(preferredQuality) }
            .thenByDescending { video -> QUALITY_REGEX.find(video.videoTitle)?.groupValues?.get(1)?.toIntOrNull() ?: 0 },
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


