package com.example.spotlyapp.data.model

data class SurveyResponse(
    val id: Long,
    val samplePointId: Long,
    val kodeSampel: String,
    val penggunaanLahan: String,
    val kesesuaian: String,
    val catatan: String?,
    val fotoUri: String?
)
