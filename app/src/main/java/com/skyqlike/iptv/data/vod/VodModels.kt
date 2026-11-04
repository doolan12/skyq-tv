package com.skyqlike.iptv.data.vod

data class Movie(
    val id: String,
    val name: String,
    val posterUrl: String?,
    val category: String,
    val year: String,
    val rating: String,
    val addedEpoch: Long,
    val streamUrl: String,
    val description: String = "",
    val genre: String = "",
    val durationMin: Int = 0,
)

data class Series(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val category: String,
    val plot: String,
    val genre: String,
    val year: String,
    val rating: String,
    val addedEpoch: Long,
)

data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val description: String,
    val durationMin: Int,
    val streamUrl: String,
    val imageUrl: String?,
)

data class VodCatalog(val movies: List<Movie>, val series: List<Series>)

/** Where the movies and series list is up to. */
sealed interface VodState {
    data object Idle : VodState
    data object Loading : VodState
    data class Error(val message: String) : VodState
    data class Ready(val catalog: VodCatalog) : VodState
}
