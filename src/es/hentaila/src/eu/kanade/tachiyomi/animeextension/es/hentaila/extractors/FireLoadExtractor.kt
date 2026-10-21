package eu.kanade.tachiyomi.animeextension.es.hentaila.extractors

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import keiyoushi.utils.useAsJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient

class FireLoadExtractor(
    private val client: OkHttpClient,
    private val headers: Headers,
) {
    suspend fun getVideoFromUrl(url: String): List<Video> {
        val document = client.newCall(GET(url, headers)).awaitSuccess().useAsJsoup()
        val script = document.selectFirst("script:containsData(dlink)")?.data() ?: return emptyList()
        val downloadUrl = DLINK_REGEX.find(script)?.groupValues?.get(1)
            ?.replace("\\/", "/")
            ?.takeIf { it.isNotBlank() }
            ?: return emptyList()

        val videoHeaders = headers.newBuilder().set("Referer", url).build()
        return listOf(Video(videoUrl = downloadUrl, videoTitle = "FireLoad", headers = videoHeaders))
    }

    private companion object {
        private val DLINK_REGEX = Regex("""["']dlink["']\s*:\s*["']([^"']+)["']""")
    }
}
