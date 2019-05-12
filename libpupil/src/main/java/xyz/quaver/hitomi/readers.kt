package xyz.quaver.hitomi

import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlinx.serialization.list
import kotlinx.serialization.parseList
import org.jsoup.Jsoup
import java.net.URL

fun getReferer(galleryID: Int) = "https://hitomi.la/reader/$galleryID.html"

@Serializable
data class GalleryInfo(
    val width: Int,
    val haswebp: Int,
    val name: String,
    val height: Int
)
data class Reader(
    val title: String,
    val images: List<Pair<URL, GalleryInfo?>>
)
//Set header `Referer` to reader url to avoid 403 error
fun getReader(galleryID: Int) : Reader {
    val readerUrl = "https://hitomi.la/reader/$galleryID.html"
    val galleryInfoUrl = "https://ltn.hitomi.la/galleries/$galleryID.js"

    val doc = Jsoup.connect(readerUrl).get()

    val title = doc.selectFirst("title").text()

    val images = doc.select(".img-url").map {
        URL(protocol + urlFromURL(it.text()))
    }

    val galleryInfo = ArrayList<GalleryInfo?>()

    galleryInfo.addAll(
        Json(JsonConfiguration.Stable).parse(
            GalleryInfo.serializer().list,
            Regex("""\[.+]""").find(
                URL(galleryInfoUrl).readText()
            )?.value ?: "[]"
        )
    )

    if (images.size > galleryInfo.size)
        galleryInfo.addAll(arrayOfNulls(images.size - galleryInfo.size))

    return Reader(title, images zip galleryInfo)
}