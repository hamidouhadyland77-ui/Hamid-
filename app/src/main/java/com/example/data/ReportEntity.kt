package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WeightItem(
    val index: Int = 1,
    val sampleId: String,
    val weight: Double,
    val unit: String = "g",
    val status: String = "مقبول", // e.g. مقبول, مرتفع, منخفض
    val notes: String = ""
)

@Entity(tableName = "reports")
data class ReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val operatorName: String = "محلل المختبر",
    val sampleCount: Int = 0,
    val avgWeight: Double = 0.0,
    val maxWeight: Double = 0.0,
    val minWeight: Double = 0.0,
    val detailsJson: String = "[]", // Serialized list of WeightItem
    val notes: String = ""
)
