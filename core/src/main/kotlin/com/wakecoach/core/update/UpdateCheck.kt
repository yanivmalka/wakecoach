package com.wakecoach.core.update

/** A published build, as read from the GitHub "latest release" response. */
data class ReleaseInfo(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val notes: String,
)

object UpdateCheck {
    /** CI numbers builds 100 + run number and tags them v0.2.<run>. */
    private const val CODE_BASE = 100
    private val codeLine = Regex("""^versionCode:\s*(\d+)\s*$""", RegexOption.MULTILINE)
    private val tagRun = Regex("""^v?\d+\.\d+\.(\d+)$""")

    const val AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

    /**
     * Builds a [ReleaseInfo] from the release fields, or null when the release has no APK
     * or its version cannot be determined (never offer an update we cannot compare).
     */
    fun parse(tag: String, body: String?, assets: List<Pair<String, String>>): ReleaseInfo? {
        val apk = assets.firstOrNull { (name, _) -> name.endsWith(".apk") }?.second ?: return null
        val code = versionCodeOf(tag, body.orEmpty()) ?: return null
        val notes = body.orEmpty().replace(codeLine, "").trim()
        return ReleaseInfo(tag.removePrefix("v"), code, apk, notes)
    }

    fun versionCodeOf(tag: String, body: String): Int? =
        codeLine.find(body)?.groupValues?.get(1)?.toIntOrNull()
            ?: tagRun.find(tag)?.groupValues?.get(1)?.toIntOrNull()?.let { CODE_BASE + it }

    fun isNewer(release: ReleaseInfo, installedVersionCode: Int): Boolean = release.versionCode > installedVersionCode

    fun dueForAutoCheck(lastCheckMs: Long, nowMs: Long): Boolean =
        nowMs - lastCheckMs >= AUTO_CHECK_INTERVAL_MS || nowMs < lastCheckMs
}
