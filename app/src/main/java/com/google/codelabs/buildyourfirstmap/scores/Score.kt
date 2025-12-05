package com.google.codelabs.buildyourfirstmap.scores

import kotlinx.serialization.Serializable

@Serializable
data class Score(
    val placeId: String,
    val username: String,   // mail
    val score: Float,       // 1..5
    val timestamp: String   // ISO UTC
)