package com.skyqlike.iptv.ui.player

import com.skyqlike.iptv.data.intro.Segment
import com.skyqlike.iptv.data.model.PlayItem
import com.skyqlike.iptv.data.subs.SubResult
import java.io.File

/** Optional player features, handed to the player in one piece so its signature stays readable. */
class PlayerExtras(
    /** Disk space for rewinding live TV in MB (0 = off). */
    val rewindMb: Int = 0,
    /** Show a "Skip intro" button where the episode has one. */
    val skipIntro: Boolean = true,
    /** Skip intros and recaps without waiting to be asked. */
    val autoSkipIntro: Boolean = false,
    /** Go straight on to the next episode when one ends. */
    val autoNext: Boolean = true,
    /** Skippable parts of an episode (needs the episode length). */
    val segments: suspend (PlayItem, Long) -> List<Segment> = { _, _ -> emptyList() },
    /** True when a subtitle search key has been entered. */
    val subtitleSearchOn: Boolean = false,
    val searchSubtitles: suspend (PlayItem) -> List<SubResult> = { emptyList() },
    val downloadSubtitle: suspend (PlayItem, SubResult) -> File? = { _, _ -> null },
    /** A subtitle chosen earlier for this title. */
    val savedSubtitle: (PlayItem) -> File? = { null },
)
