package com.google.codelabs.buildyourfirstmap.place
import com.google.codelabs.buildyourfirstmap.place.Note

import com.google.firebase.Timestamp

data class Note(
    val id: String? = null,
    val text: String = "",
    val createdAt: Timestamp? = null,
    val authorUid: String? = null
)
