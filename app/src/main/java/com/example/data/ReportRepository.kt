package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportRepository(private val reportDao: ReportDao) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    
    private val weightListType = Types.newParameterizedType(List::class.java, WeightItem::class.java)
    private val jsonAdapter = moshi.adapter<List<WeightItem>>(weightListType)

    val allReports: Flow<List<ReportEntity>> = reportDao.getAllReports()

    suspend fun getReportById(id: Int): ReportEntity? {
        return reportDao.getReportById(id)
    }

    suspend fun insertReport(
        title: String,
        operatorName: String,
        items: List<WeightItem>,
        notes: String = ""
    ): Long {
        val detailsJson = serializeWeightItems(items)
        val weights = items.map { it.weight }
        val count = items.size
        val avg = if (count > 0) weights.average() else 0.0
        val max = if (count > 0) weights.maxOrNull() ?: 0.0 else 0.0
        val min = if (count > 0) weights.minOrNull() ?: 0.0 else 0.0

        val entity = ReportEntity(
            title = title.ifEmpty { "تقرير أوزان عينات AFRILAB - ${getCurrentFormattedDate()}" },
            operatorName = operatorName.ifEmpty { "محلل المختبر" },
            sampleCount = count,
            avgWeight = avg,
            maxWeight = max,
            minWeight = min,
            detailsJson = detailsJson,
            notes = notes
        )
        return reportDao.insertReport(entity)
    }

    suspend fun deleteReport(id: Int) {
        reportDao.deleteReportById(id)
    }

    suspend fun clearAll() {
        reportDao.clearAllReports()
    }

    // JSON parsing functions
    fun serializeWeightItems(items: List<WeightItem>): String {
        return try {
            jsonAdapter.toJson(items)
        } catch (e: Exception) {
            "[]"
        }
    }

    fun deserializeWeightItems(json: String): List<WeightItem> {
        return try {
            jsonAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getCurrentFormattedDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date())
    }
}
