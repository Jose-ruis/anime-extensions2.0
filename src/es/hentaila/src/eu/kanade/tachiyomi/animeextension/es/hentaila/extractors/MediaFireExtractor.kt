package eu.kanade.tachiyomi.animeextension.es.hentaila.extractors

import android.util.Base64
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import keiyoushi.utils.useAsJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient

class MediaFireExtractor(
    private val client: OkHttpClient,
    private val headers: Headers,
) {
    suspend fun getVideoFromUrl(url: String): List<Video> {
        val document = client.newCall(GET(url, headers)).awaitSuccess().useAsJsoup()
        val button = document.selectFirst("a#downloadButton") ?: return emptyList()

        // MediaFire a veces oculta el enlace real en base64 y deja un href inútil ("#", "javascript:...").
        val scrambled = button.attr("data-scrambled-url")
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { String(Base64.decode(it, Base64.DEFAULT)) }.getOrNull() }
        val href = button.attr("href")
        val downloadUrl = scrambled
            ?: href.takeIf { it.startsWith("http") }
            ?: return emptyList()

        val videoHeaders = headers.newBuilder().set("Referer", url).build()
        return listOf(Video(videoUrl = downloadUrl, videoTitle = "MediaFire", headers = videoHeaders))
    }
}
