// AccountMenuResponse.kt

package com.akira.innertube.models.response

import com.akira.innertube.models.AccountInfo
import com.akira.innertube.models.Runs
import com.akira.innertube.models.Thumbnails
import kotlinx.serialization.Serializable

@Serializable
data class AccountMenuResponse(
    val actions: List<Action>,
) {
    @Serializable
    data class Action(
        val openPopupAction: OpenPopupAction? = null,
    ) {
        @Serializable
        data class OpenPopupAction(
            val popup: Popup? = null,
        ) {
            @Serializable
            data class Popup(
                val multiPageMenuRenderer: MultiPageMenuRenderer? = null,
            ) {
                @Serializable
                data class MultiPageMenuRenderer(
                    val header: Header? = null,
                ) {
                    @Serializable
                    data class Header(
                        val activeAccountHeaderRenderer: ActiveAccountHeaderRenderer,
                    ) {
                        @Serializable
                        data class ActiveAccountHeaderRenderer(
                            val accountName: Runs,
                            val email: Runs?,
                            val channelHandle: Runs?,
                            val accountPhoto: Thumbnails,
                        ) {
                            fun toAccountInfo() =
                                AccountInfo(
                                    name = accountName.runs!!.first().text,
                                    email = email?.runs?.first()?.text,
                                    channelHandle = channelHandle?.runs?.first()?.text,
                                    thumbnailUrl = accountPhoto.thumbnails.lastOrNull()?.url?.let {
                                        if (it.startsWith("//")) "https:$it" else it
                                    },
                                )
                        }
                    }
                }
            }
        }
    }
}
