package com.zerointerrupt.piano.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GitHubReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseName: String,
    val body: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?
)

object GitHubUpdateChecker {
    private const val REPO_OWNER = "CHen-YU-ZhoU"
    private const val REPO_NAME = "ZeroInterruptPiano"
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
    const val RELEASES_PAGE_URL = "https://github.com/$REPO_OWNER/$REPO_NAME/releases"

    private const val PREF_NAME = "piano_update_prefs"
    private const val KEY_IGNORED_TAG = "ignored_tag"

    fun getIgnoredTag(context: Context): String? {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_IGNORED_TAG, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun setIgnoredTag(context: Context, tagName: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_IGNORED_TAG, tagName.trim())
            .apply()
    }

    suspend fun checkLatestRelease(currentVersion: String): Result<GitHubReleaseInfo?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "ZeroInterruptPiano/$currentVersion")
                }
                try {
                    val code = conn.responseCode
                    if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                        return@runCatching null
                    }
                    if (code !in 200..299) {
                        throw IllegalStateException("GitHub API 返回 HTTP $code")
                    }
                    val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val json = JSONObject(text)
                    val tagName = json.optString("tag_name", "").trim()
                    if (tagName.isBlank()) return@runCatching null

                    val remoteVersion = normalizeVersion(tagName)
                    if (!isNewerVersion(remoteVersion, currentVersion)) {
                        return@runCatching null
                    }

                    val releaseName = json.optString("name", tagName).trim().ifBlank { tagName }
                    val body = json.optString("body", "").trim()
                    val htmlUrl = json.optString("html_url", RELEASES_PAGE_URL).trim().ifBlank { RELEASES_PAGE_URL }

                    var apkUrl: String? = null
                    var apkName: String? = null
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.optJSONObject(i) ?: continue
                            val name = asset.optString("name", "")
                            val dl = asset.optString("browser_download_url", "")
                            if (name.endsWith(".apk", ignoreCase = true) && dl.isNotBlank()) {
                                apkUrl = dl
                                apkName = name
                                break
                            }
                        }
                    }

                    GitHubReleaseInfo(
                        tagName = tagName,
                        versionName = remoteVersion,
                        releaseName = releaseName,
                        body = body,
                        htmlUrl = htmlUrl,
                        apkDownloadUrl = apkUrl,
                        apkFileName = apkName
                    )
                } finally {
                    conn.disconnect()
                }
            }
        }

    internal fun normalizeVersion(raw: String): String {
        return raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
            .trim()
    }

    internal fun isNewerVersion(remoteRaw: String, currentRaw: String): Boolean {
        val remoteParts = normalizeVersion(remoteRaw).split('.').map { it.toIntOrNull() ?: 0 }
        val currentParts = normalizeVersion(currentRaw).split('.').map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }
}
