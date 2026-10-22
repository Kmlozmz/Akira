package com.akira.app.core.online

import android.net.Uri
import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.request.SuccessResult
import coil.size.Dimension

/** All Akira image surfaces use the same catalogue sizing, including restored queues and the player. */
internal class OnlineArtworkInterceptor : Interceptor {
    private val preferredVariants = java.util.concurrent.ConcurrentHashMap<String, String>()
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val original = chain.request.data.toString()
        val size = maxOf((chain.size.width as? Dimension.Pixels)?.px ?: 1200,
            (chain.size.height as? Dimension.Pixels)?.px ?: 1200).coerceIn(512, 2048)
        val variants = onlineArtworkCandidates(original, size)
        val preferred = preferredVariants[original]
        val candidates = if (preferred == null) variants else variants.sortedBy { if (it == preferred) 0 else 1 }
        if(candidates == listOf(original)) return chain.proceed(chain.request)
        var last: ImageResult? = null
        var best: SuccessResult? = null
        for(url in candidates) {
            val result = chain.proceed(chain.request.newBuilder().data(url).build())
            last = result
            if(result is SuccessResult && (best == null || result.drawable.intrinsicWidth > best.drawable.intrinsicWidth)) best = result
            // Missing YouTube variants may return a successful 120px placeholder instead of a 404.
            if(result is SuccessResult && result.drawable.intrinsicWidth >= 300) {
                // Remember the working video variant; sized Google artwork is
                // still resolved for each surface so the player keeps its detail.
                if (url.contains(".ytimg.com/") || url.contains("img.youtube.com/")) {
                    if (preferredVariants.size > 1024) preferredVariants.clear()
                    preferredVariants[original] = url
                }
                return result
            }
        }
        return best ?: last ?: chain.proceed(chain.request)
    }
}

internal fun onlineArtworkUrl(url: String, edge: Int): String {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return url
    if (uri.scheme != "https") return url
    val host = uri.host.orEmpty()
    if (host in setOf("lh3.googleusercontent.com", "yt3.googleusercontent.com", "lh3.ggpht.com", "yt3.ggpht.com")) {
        val sized = url.replace(Regex("([=\\-])[whs]\\d+")) { match -> "${match.groupValues[1]}${match.value[1]}$edge" }
        return if (sized == url && !url.contains('=')) "$url=w$edge-h$edge-l90-rj" else sized
    }
    if (host.matches(Regex("i\\d*\\.ytimg\\.com")) || host == "img.youtube.com")
        return url.substringBefore('?').replace(Regex("/(?:default|mqdefault|hqdefault|hq720|sddefault|maxresdefault)\\.(jpg|webp)"), "/maxresdefault.$1")
    return url
}

internal fun onlineArtworkCandidates(url: String, edge: Int): List<String> {
    val large = onlineArtworkUrl(url, edge)
    val uri = runCatching { Uri.parse(large) }.getOrNull()
    val youtube = uri?.host.orEmpty().matches(Regex("i\\d*\\.ytimg\\.com")) || uri?.host == "img.youtube.com"
    if(!youtube || !large.contains("/maxresdefault.")) return listOf(large, url).distinct()
    return listOf(large, large.replace("/maxresdefault.", "/hq720."), large.replace("/maxresdefault.", "/sddefault."),
        large.replace("/maxresdefault.", "/hqdefault."), url).distinct()
}
