package mk.amazingapps.digitalphotoframe

import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

object RemoteGalleryManager {

    suspend fun fetchRemoteImages(url: String): List<Uri> {
        return withContext(Dispatchers.IO) {
            try {
                val cleanUrl = url.trim()
                val finalUrl = if (cleanUrl.contains("drive.google.com")) {
                    val fileId = extractGoogleDriveId(cleanUrl)
                    if (fileId != null) "https://drive.google.com/uc?export=download&id=$fileId" else cleanUrl
                } else {
                    cleanUrl
                }

                val content = URL(finalUrl).readText()
                urisFromContent(content)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private fun urisFromContent(content: String): List<Uri> {
        return content.split(Regex("[,\\n\\r]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { link ->
                if (link.contains("drive.google.com")) {
                    val fileId = extractGoogleDriveId(link)
                    if (fileId != null) {
                        "https://drive.google.com/uc?export=download&id=$fileId".toUri()
                    } else {
                        link.toUri()
                    }
                } else {
                    link.toUri()
                }
            }
    }

    private fun extractGoogleDriveId(url: String): String? {
        val regex1 = "/file/d/([^/]+)".toRegex()
        val regex2 = "id=([^&]+)".toRegex()

        return regex1.find(url)?.groupValues?.get(1)
            ?: regex2.find(url)?.groupValues?.get(1)
    }
}
