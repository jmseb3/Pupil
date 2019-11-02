/*
 *    Copyright 2019 tom5079
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package xyz.quaver.hitomi

import kotlinx.serialization.Serializable
import org.jsoup.Jsoup

fun getReferer(galleryID: Int) = "https://hitomi.la/reader/$galleryID.html"
fun webpUrlFromUrl(url: String) = url.replace("/galleries/", "/webp/") + ".webp"

@Serializable
data class GalleryInfo(
    val width: Int,
    val hash: String?,
    val haswebp: Int,
    val name: String,
    val height: Int
)

@Serializable
open class Reader(val title: String, val galleryInfo: List<GalleryInfo>)

//Set header `Referer` to reader url to avoid 403 error
fun getReader(galleryID: Int) : Reader {
    val readerUrl = "https://hitomi.la/reader/$galleryID.html"

    val doc = Jsoup.connect(readerUrl).get()

   return Reader(doc.title(), getGalleryInfo(galleryID))
}