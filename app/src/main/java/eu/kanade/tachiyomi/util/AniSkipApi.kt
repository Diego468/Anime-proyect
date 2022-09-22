package eu.kanade.tachiyomi.util

import android.annotation.SuppressLint
import android.util.Log
import android.widget.Toast
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.PlayerActivityBinding
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.ui.player.PlayerActivity
import eu.kanade.tachiyomi.util.lang.launchUI
import `is`.xyz.mpv.MPVLib
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import uy.kohesive.injekt.injectLazy

class AniSkipApi {
    private val client = OkHttpClient()
    private val json: Json by injectLazy()

    // credits: https://github.com/saikou-app/saikou/blob/main/app/src/main/java/ani/saikou/others/AniSkip.kt
    fun getResult(malId: Int, episodeNumber: Int, episodeLength: Long): List<Stamp>? {
        val url =
            "https://api.aniskip.com/v2/skip-times/$malId/$episodeNumber?types[]=ed&types[]=mixed-ed&types[]=mixed-op&types[]=op&types[]=recap&episodeLength=$episodeLength"
        return try {
            val a = client.newCall(GET(url)).execute().body!!.string()
            val res = json.decodeFromString<AniSkipResponse>(a)
            if (res.found) res.results else null
        } catch (e: Exception) {
            null
        }
    }

    fun getMalIdFromAL(id: Long): Long {
        val query = """
                query{
                Media(id:$id){idMal}
                }
        """.trimMargin()
        val response = client.newCall(
            POST(
                "https://graphql.anilist.co",
                body = buildJsonObject { put("query", query) }.toString().toRequestBody(jsonMime),
            ),
        ).execute()
        return response.body!!.string().substringAfter("idMal\":").substringBefore("}")
            .toLongOrNull() ?: 0
    }

    class PlayerUtils(
        private val binding: PlayerActivityBinding,
        private val aniSkipResponse: List<Stamp>,
    ) {
        private val playerControls get() = binding.playerControls
        private val activity: PlayerActivity get() = binding.root.context as PlayerActivity

        fun showSkipButton(skipType: SkipType) {
            val skipButtonString = when (skipType) {
                SkipType.ed -> R.string.player_aniskip_ed
                SkipType.op -> R.string.player_aniskip_op
                SkipType.recap -> R.string.player_aniskip_recap
                SkipType.mixedOp -> R.string.player_aniskip_mixedOp
            }
            launchUI {
                playerControls.binding.controlsSkipIntroBtn.text = activity.getString(skipButtonString)
            }
        }

        // this is used when netflixStyle is enabled
        @SuppressLint("SetTextI18n")
        fun showSkipButton(skipType: SkipType, waitingTime: Int) {
            val skipTime = when (skipType) {
                SkipType.ed -> aniSkipResponse.first { it.skipType == SkipType.ed }.interval
                SkipType.op -> aniSkipResponse.first { it.skipType == SkipType.op }.interval
                SkipType.recap -> aniSkipResponse.first { it.skipType == SkipType.recap }.interval
                SkipType.mixedOp -> aniSkipResponse.first { it.skipType == SkipType.mixedOp }.interval
            }
            if (waitingTime > -1) {
                if (waitingTime > 0) {
                    launchUI {
                        playerControls.binding.controlsSkipIntroBtn.text = activity.getString(R.string.player_aniskip_dontskip)
                    }
                } else {
                    seekTo(skipTime.endTime)
                    // show a toast
                    launchUI {
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.player_aniskip_skip, skipType.getString()),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            } else {
                // when waitingTime is -1, it means that the user cancelled the skip
                showSkipButton(skipType)
            }
        }

        private fun seekTo(time: Double) {
            MPVLib.command(arrayOf("seek", time.toString(), "absolute"))
        }
    }
}

@Serializable
data class AniSkipResponse(
    val found: Boolean,
    val results: List<Stamp>?,
    val message: String?,
    val statusCode: Int,
)

@Serializable
data class Stamp(
    val interval: AniSkipInterval,
    val skipType: SkipType,
    val skipId: String,
    val episodeLength: Double,
)

@Suppress("EnumEntryName")
@Serializable
enum class SkipType {
    op, ed, recap, @SerialName("mixed-op")
    mixedOp;

    fun getString(): String {
        return when (this) {
            op -> "Opening"
            ed -> "Ending"
            recap -> "Recap"
            mixedOp -> "Mixed-op"
        }
    }
}

@Serializable
data class AniSkipInterval(
    val startTime: Double,
    val endTime: Double,
)
