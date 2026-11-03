package com.rteats.calendarik.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.rteats.calendarik.R

/** Stable reaction IDs keep emoji fonts and Android versions out of the UI. */
internal data class MoodChoice(val name: String, val label: String) {
    val id: String get() = "fa:$name"
}

internal object MoodCatalog {
    val choices: List<MoodChoice> = listOf(
        MoodChoice("face-smile", "Улыбка"),
        MoodChoice("face-grin", "Радость"),
        MoodChoice("face-grin-hearts", "Влюблён"),
        MoodChoice("face-laugh", "Весело"),
        MoodChoice("face-grin-stars", "Восторг"),
        MoodChoice("face-meh", "Нейтрально"),
        MoodChoice("face-meh-blank", "Без эмоций"),
        MoodChoice("face-sad-tear", "Грустно"),
        MoodChoice("face-sad-cry", "Плачу"),
        MoodChoice("face-frown", "Уныло"),
        MoodChoice("face-angry", "Злость"),
        MoodChoice("face-tired", "Усталость"),
        MoodChoice("face-dizzy", "Головокружение"),
        MoodChoice("face-rolling-eyes", "Закатил глаза"),
        MoodChoice("poop", "Какашка"),
        MoodChoice("poo-storm", "Какашка-буря"),
        MoodChoice("skull", "Череп"),
        MoodChoice("heart", "Сердце"),
        MoodChoice("heart-crack", "Разбитое"),
        MoodChoice("fire", "Огонь"),
        MoodChoice("gift", "Подарок"),
        MoodChoice("champagne-glasses", "Праздник"),
    )
    val byId: Map<String, MoodChoice> = choices.associateBy { it.id }
    fun label(id: String): String = byId[id]?.label ?: id

    fun resource(id: String?): Int? = when (id?.removePrefix("fa:")) {
        "face-smile" -> R.drawable.fa_face_smile
        "face-smile-beam" -> R.drawable.fa_face_smile_beam
        "face-grin" -> R.drawable.fa_face_grin
        "face-grin-wide" -> R.drawable.fa_face_grin_wide
        "face-grin-hearts" -> R.drawable.fa_face_grin_hearts
        "face-laugh" -> R.drawable.fa_face_laugh
        "face-laugh-squint" -> R.drawable.fa_face_laugh_squint
        "face-grin-squint-tears" -> R.drawable.fa_face_grin_squint_tears
        "face-grin-tears" -> R.drawable.fa_face_grin_tears
        "face-grin-stars" -> R.drawable.fa_face_grin_stars
        "face-kiss" -> R.drawable.fa_face_kiss
        "face-kiss-beam" -> R.drawable.fa_face_kiss_beam
        "face-grin-wink" -> R.drawable.fa_face_grin_wink
        "face-smile-wink" -> R.drawable.fa_face_smile_wink
        "face-meh" -> R.drawable.fa_face_meh
        "face-meh-blank" -> R.drawable.fa_face_meh_blank
        "face-sad-tear" -> R.drawable.fa_face_sad_tear
        "face-sad-cry" -> R.drawable.fa_face_sad_cry
        "face-frown" -> R.drawable.fa_face_frown
        "face-frown-open" -> R.drawable.fa_face_frown_open
        "face-angry" -> R.drawable.fa_face_angry
        "face-flushed" -> R.drawable.fa_face_flushed
        "face-tired" -> R.drawable.fa_face_tired
        "face-dizzy" -> R.drawable.fa_face_dizzy
        "face-surprise" -> R.drawable.fa_face_surprise
        "face-grimace" -> R.drawable.fa_face_grimace
        "face-rolling-eyes" -> R.drawable.fa_face_rolling_eyes
        "face-grin-beam-sweat" -> R.drawable.fa_face_grin_beam_sweat
        "face-grin-beam" -> R.drawable.fa_face_grin_beam
        "poop" -> R.drawable.fa_poop
        "poo" -> R.drawable.fa_poop // Legacy value uses the faceless icon
        "poo-storm" -> R.drawable.fa_poo_storm
        "ghost" -> R.drawable.fa_ghost
        "robot" -> R.drawable.fa_robot
        "skull" -> R.drawable.fa_skull
        "masks-theater" -> R.drawable.fa_masks_theater
        "cat" -> R.drawable.fa_cat
        "bug" -> R.drawable.fa_bug
        "face-grin-tongue" -> R.drawable.fa_face_grin_tongue
        "face-grin-tongue-wink" -> R.drawable.fa_face_grin_tongue_wink
        "face-grin-tongue-squint" -> R.drawable.fa_face_grin_tongue_squint
        "heart" -> R.drawable.fa_heart
        "heart-crack" -> R.drawable.fa_heart_crack
        "heart-pulse" -> R.drawable.fa_heart_pulse
        "fire" -> R.drawable.fa_fire
        "star" -> R.drawable.fa_star
        "star-of-life" -> R.drawable.fa_star_of_life
        "moon" -> R.drawable.fa_moon
        "sun" -> R.drawable.fa_sun
        "cloud" -> R.drawable.fa_cloud
        "cloud-rain" -> R.drawable.fa_cloud_rain
        "snowflake" -> R.drawable.fa_snowflake
        "clover" -> R.drawable.fa_clover
        "rainbow" -> R.drawable.fa_rainbow
        "seedling" -> R.drawable.fa_seedling
        "gift" -> R.drawable.fa_gift
        "bolt" -> R.drawable.fa_bolt
        "music" -> R.drawable.fa_music
        "champagne-glasses" -> R.drawable.fa_champagne_glasses
        else -> null
    }
}

@Composable
internal fun MoodGlyph(
    value: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFF7C5BAE),
) {
    val drawable = if (value?.startsWith("fa:") == true) MoodCatalog.resource(value) else null
    if (drawable != null) {
        Icon(
            painter = painterResource(drawable),
            contentDescription = MoodCatalog.label(value!!),
            modifier = modifier,
            tint = tint,
        )
    } else {
        // Previously saved Unicode reactions remain readable.
        Text(value ?: "·", modifier = modifier, fontSize = 20.sp,
            maxLines = 1, overflow = TextOverflow.Clip)
    }
}

/** A single shared pink heart with a clear number from 1 to 5. */
@Composable
internal fun HeartGlyph(
    rating: Int,
    modifier: Modifier = Modifier,
    numberSize: androidx.compose.ui.unit.TextUnit = 12.sp,
) {
    require(rating in 1..5)
    androidx.compose.foundation.layout.Box(
        modifier = modifier,
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.fa_heart),
            contentDescription = "Сердечко $rating из 5",
            modifier = Modifier.fillMaxSize(),
            tint = Color(0xFFE68BC7),
        )
        Text(
            rating.toString(),
            fontSize = numberSize,
            color = Color.White,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
        )
    }
}
