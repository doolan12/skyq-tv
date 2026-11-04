package com.skyqlike.iptv.data.vod

/** Made-up movies and series that play the public test videos, so the screens can be tried without a provider. */
object SampleVod {

    private val streams = listOf(
        "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
        "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8",
        "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
    )

    private val categories = listOf("Action", "Comedy", "Family", "Documentary")

    fun catalog(): VodCatalog {
        val now = System.currentTimeMillis() / 1000
        val movies = (1..14).map { i ->
            Movie(
                id = "sample$i",
                name = "Sample Movie $i",
                posterUrl = null,
                category = categories[i % categories.size],
                year = (2015 + i % 10).toString(),
                rating = "%.1f".format(5.0 + (i * 37 % 40) / 10.0),
                addedEpoch = now - i * 86_400L,
                streamUrl = streams[i % streams.size],
                description = "A sample film for testing the movies screen. It plays a public test video.",
                genre = categories[i % categories.size],
                durationMin = 80 + i * 4,
            )
        }
        val series = (1..6).map { i ->
            Series(
                id = "sampleSeries$i",
                name = "Sample Series $i",
                coverUrl = null,
                category = categories[i % categories.size],
                plot = "A sample series for testing the series screen. Every episode plays a public test video.",
                genre = categories[i % categories.size],
                year = (2018 + i % 6).toString(),
                rating = "%.1f".format(6.0 + (i * 13 % 30) / 10.0),
                addedEpoch = now - i * 43_200L,
            )
        }
        return VodCatalog(movies, series)
    }

    fun episodes(series: Series): List<Episode> {
        val out = ArrayList<Episode>()
        for (season in 1..2) {
            for (number in 1..4) {
                out += Episode(
                    id = "${series.id}-s${season}e$number",
                    season = season,
                    number = number,
                    title = "Episode $number",
                    description = "Sample episode $number of season $season. It plays a public test video.",
                    durationMin = 25 + number * 5,
                    streamUrl = streams[(season + number) % streams.size],
                    imageUrl = null,
                )
            }
        }
        return out
    }
}
