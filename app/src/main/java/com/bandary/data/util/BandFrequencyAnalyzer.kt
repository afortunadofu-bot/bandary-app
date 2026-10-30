package com.bandary.data.util

import com.bandary.domain.model.Concert

/**
 * Analyzes concert history to extract per-artist attendance statistics.
 */
object BandFrequencyAnalyzer {

    data class ArtistStats(
        val artistName: String,
        val attendCount: Int,
        val totalSpent: Double,
        val firstSeen: kotlinx.datetime.LocalDateTime?,
        val lastSeen: kotlinx.datetime.LocalDateTime?,
        val cities: Set<String>,
        val venues: Set<String>
    ) {
        val isLoyal: Boolean get() = attendCount >= 3
    }

    /**
     * Returns artist stats sorted by attendance count (descending).
     */
    fun analyze(concerts: List<Concert>): List<ArtistStats> {
        val grouped = concerts.groupBy { it.artistName.trim() }
            .filter { it.key.isNotBlank() }

        return grouped.map { (artist, entries) ->
            ArtistStats(
                artistName = artist,
                attendCount = entries.size,
                totalSpent = entries.sumOf { it.ticketPrice ?: 0.0 },
                firstSeen = entries.minByOrNull { it.dateTime }?.dateTime,
                lastSeen = entries.maxByOrNull { it.dateTime }?.dateTime,
                cities = entries.mapNotNull { it.city }.toSet(),
                venues = entries.map { it.venue }.toSet()
            )
        }.sortedWith(
            compareByDescending<ArtistStats> { it.attendCount }
                .thenByDescending { it.totalSpent }
        )
    }

    /**
     * Total artists you've ever seen (different bands).
     */
    fun totalArtists(concerts: List<Concert>): Int =
        concerts.map { it.artistName.trim() }.filter { it.isNotBlank() }.distinct().size

    /**
     * Bands you've seen multiple times (>=2 attendances).
     */
    fun repeatBands(stats: List<ArtistStats>): List<ArtistStats> =
        stats.filter { it.attendCount >= 2 }
}
