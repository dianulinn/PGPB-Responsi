package com.example.spotlyapp.data.model

data class SamplePoint(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val pdfUri: String? = null
)
