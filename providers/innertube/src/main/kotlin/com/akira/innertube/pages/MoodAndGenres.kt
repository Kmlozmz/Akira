// MoodAndGenres.kt

package com.akira.innertube.pages

import com.akira.innertube.models.BrowseEndpoint
import com.akira.innertube.models.GridRenderer
import com.akira.innertube.models.MusicNavigationButtonRenderer
import com.akira.innertube.models.SectionListRenderer
import kotlinx.serialization.Serializable

@Serializable
data class MoodAndGenres(
    val title: String,
    val items: List<Item>,
) {
    @Serializable
    data class Item(
        val title: String,
        val stripeColor: Long,
        val endpoint: BrowseEndpoint,
    )

    companion object {
        fun fromSectionListRendererContent(content: SectionListRenderer.Content): MoodAndGenres? {
            return MoodAndGenres(
                title =
                    content.gridRenderer
                        ?.header
                        ?.gridHeaderRenderer
                        ?.title
                        ?.runs
                        ?.firstOrNull()
                        ?.text ?: return null,
                items =
                    content.gridRenderer.items
                        .mapNotNull(GridRenderer.Item::musicNavigationButtonRenderer)
                        .mapNotNull(::fromMusicNavigationButtonRenderer),
            )
        }

        fun fromMusicNavigationButtonRenderer(renderer: MusicNavigationButtonRenderer): Item? {
            return Item(
                title =
                    renderer.buttonText.runs
                        ?.firstOrNull()
                        ?.text ?: return null,
                stripeColor = renderer.solid?.leftStripeColor ?: return null,
                endpoint = renderer.clickCommand.browseEndpoint ?: return null,
            )
        }
    }
}
