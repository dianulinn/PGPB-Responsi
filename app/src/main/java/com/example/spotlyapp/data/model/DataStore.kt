package com.example.spotlyapp.data

import com.example.spotlyapp.data.model.SamplePoint
import com.example.spotlyapp.data.model.SurveyResponse

object DataStore {
    val samplePoints = mutableListOf<SamplePoint>()
    private var samplePointAutoId = 1L

    fun addSamplePoint(
        name: String,
        lat: Double,
        lon: Double,
        pdfUri: String?
    ): SamplePoint {
        val point = SamplePoint(
            id = samplePointAutoId++,
            name = name,
            latitude = lat,
            longitude = lon,
            pdfUri = pdfUri
        )
        samplePoints.add(point)
        return point
    }

    fun getSamplePointById(id: Long): SamplePoint? {
        return samplePoints.find { it.id == id }
    }

    val surveyResponses = mutableListOf<SurveyResponse>()
    private var surveyAutoId = 1L

    fun addSurveyResponse(
        samplePointId: Long,
        kodeSampel: String,
        penggunaanLahan: String,
        kesesuaian: String,
        catatan: String?,
        fotoUri: String?
    ): SurveyResponse {
        val response = SurveyResponse(
            id = surveyAutoId++,
            samplePointId = samplePointId,
            kodeSampel = kodeSampel,
            penggunaanLahan = penggunaanLahan,
            kesesuaian = kesesuaian,
            catatan = catatan,
            fotoUri = fotoUri
        )
        surveyResponses.add(response)
        return response
    }

    fun getSurveyResponseById(id: Long): SurveyResponse? {
        return surveyResponses.find { it.id == id }
    }

    fun updateSurveyResponse(
        id: Long,
        kodeSampel: String,
        penggunaanLahan: String,
        kesesuaian: String,
        catatan: String?,
        fotoUri: String?
    ): Boolean {
        val index = surveyResponses.indexOfFirst { it.id == id }
        if (index == -1) return false

        val old = surveyResponses[index]
        surveyResponses[index] = old.copy(
            kodeSampel = kodeSampel,
            penggunaanLahan = penggunaanLahan,
            kesesuaian = kesesuaian,
            catatan = catatan,
            fotoUri = fotoUri ?: old.fotoUri
        )
        return true
    }

    fun deleteSurveyResponseById(id: Long): Boolean {
        val iterator = surveyResponses.iterator()
        while (iterator.hasNext()) {
            val res = iterator.next()
            if (res.id == id) {
                iterator.remove()
                return true
            }
        }
        return false
    }
}