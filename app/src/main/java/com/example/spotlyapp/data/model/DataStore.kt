package com.example.spotlyapp.data

import com.example.spotlyapp.data.model.SamplePoint
import com.example.spotlyapp.data.model.SurveyResponse

/**
 * Penyimpanan data sederhana (in-memory).
 * Tidak menggunakan database agar ekspor CSV tetap mudah.
 */
object DataStore {

    // ============================
    // DATA TITIK SAMPEL
    // ============================
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


    // ============================
    // DATA FORM / SURVEY RESPONSE
    // ============================
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

    // contoh skema DataStore kamu kurang lebih kayak gini:
// object DataStore {
//     val samplePoints = mutableListOf<SamplePoint>()
//     val surveyResponses = mutableListOf<SurveyResponse>()
//     ...
// }

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
