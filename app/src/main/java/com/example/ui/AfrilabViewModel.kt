package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ReportEntity
import com.example.data.ReportRepository
import com.example.data.WeightItem
import com.example.network.GeminiParser
import com.example.utils.WordGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AfrilabViewModel(private val repository: ReportRepository) : ViewModel() {

    // Database report entries Flow
    val savedReports: StateFlow<List<ReportEntity>> = repository.allReports
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current screen input states
    val inputDataText = MutableStateFlow("")
    val selectedImage = MutableStateFlow<Bitmap?>(null)
    
    // Extracted target data state
    val candidateItems = MutableStateFlow<List<WeightItem>>(emptyList())
    
    // Metadata states
    val reportTitle = MutableStateFlow("")
    val operatorName = MutableStateFlow("")
    val reportNotes = MutableStateFlow("")

    val isProcessing = MutableStateFlow(false)
    val isSyncing = MutableStateFlow(false)
    
    // Outcome alerts
    val exportedFile = MutableStateFlow<File?>(null)
    val errorMessage = MutableStateFlow<String?>(null)

    // Statistics derived state helper
    fun computeStats(): Triple<Int, Double, Double> {
        val list = candidateItems.value
        val count = list.size
        if (count == 0) return Triple(0, 0.0, 0.0)
        val weights = list.map { it.weight }
        val sum = weights.sum()
        val avg = sum / count
        val max = weights.maxOrNull() ?: 0.0
        val min = weights.minOrNull() ?: 0.0
        return Triple(count, avg, max)
    }

    /**
     * Parses the current text and/or selected screen image using the Gemini 3.5 API.
     */
    fun parseDataWithAI() {
        viewModelScope.launch {
            if (inputDataText.value.isEmpty() && selectedImage.value == null) {
                errorMessage.value = "الرجاء كتابة نصوص لوج أو إرفاق صورة أولاً للاستخراج"
                return@launch
            }
            
            isProcessing.value = true
            errorMessage.value = null
            
            try {
                val parsed = GeminiParser.parseAfrilabData(
                    inputText = inputDataText.value,
                    imageBitmap = selectedImage.value
                )
                
                // Format indices correctly
                val alignedList = parsed.mapIndexed { idx, it ->
                    it.copy(index = idx + 1)
                }
                
                candidateItems.value = alignedList
                if (alignedList.isEmpty()) {
                    errorMessage.value = "لم يتم تحديد أوزان متوافقة. جرب صيغة نص مختلفة أو صورة أوضح."
                }
            } catch (e: Exception) {
                errorMessage.value = "فشل في معالجة تحليل البيانات: ${e.localizedMessage}"
            } finally {
                isProcessing.value = false
            }
        }
    }

    /**
     * Simulates fetching real weight readings directly from "Afrilab System Server".
     */
    fun pullDataFromAfrilab() {
        viewModelScope.launch {
            isSyncing.value = true
            errorMessage.value = null
            kotlinx.coroutines.delay(1800) // Realistic server ping delay
            
            try {
                // Highly realistic laboratory sample weights representation (balance measures g)
                val liveList = listOf(
                    WeightItem(1, "Afrilab-26B-01", 145.22, "g", "مقبول", "عينة خام زراعية"),
                    WeightItem(2, "Afrilab-26B-02", 145.89, "g", "مقبول", "نسبة رطوبة مستقرة"),
                    WeightItem(3, "Afrilab-26B-03", 148.10, "g", "مرتفع", "تجاوز الحد المسموح"),
                    WeightItem(4, "Afrilab-26B-04", 144.15, "g", "مقبول", "فحص ميكروبيولوجي"),
                    WeightItem(5, "Afrilab-26B-05", 139.40, "g", "منخفض", "تحتاج إعادة تعبئة"),
                    WeightItem(6, "Afrilab-26B-06", 144.95, "g", "مقبول", "فحص هيدروجيني")
                )
                candidateItems.value = liveList
            } catch (e: Exception) {
                errorMessage.value = "فشل في سحب البيانات: ${e.localizedMessage}"
            } finally {
                isSyncing.value = false
            }
        }
    }

    fun addManualWeight(id: String, weight: Double, notes: String) {
        val currentList = candidateItems.value.toMutableList()
        val newIndex = currentList.size + 1
        
        // Simple heuristic status classification
        val status = if (weight > 146.0) "مرتفع" else if (weight < 140.0) "منخفض" else "مقبول"
        
        currentList.add(WeightItem(newIndex, id, weight, "g", status, notes))
        candidateItems.value = currentList
    }

    fun updateManualWeight(idx: Int, id: String, weight: Double, notes: String) {
        val currentList = candidateItems.value.toMutableList()
        val targetIdx = currentList.indexOfFirst { it.index == idx }
        if (targetIdx != -1) {
            val status = if (weight > 146.0) "مرتفع" else if (weight < 140.0) "منخفض" else "مقبول"
            currentList[targetIdx] = WeightItem(idx, id, weight, "g", status, notes)
            candidateItems.value = currentList
        }
    }

    fun deleteWeight(idx: Int) {
        val updatedList = candidateItems.value.filterNot { it.index == idx }
            .mapIndexed { index, item -> item.copy(index = index + 1) }
        candidateItems.value = updatedList
    }

    fun clearCandidateList() {
        candidateItems.value = emptyList()
        inputDataText.value = ""
        selectedImage.value = null
        reportTitle.value = ""
        reportNotes.value = ""
    }

    /**
     * Saves the report automatically in the Room Database,
     * writes the formatted word document (.doc), and exposes it.
     */
    fun saveReportAndGenerateWord(context: Context) {
        viewModelScope.launch {
            if (candidateItems.value.isEmpty()) {
                errorMessage.value = "قائمة الأوزان فارغة! أضف أوزاناً أو اسحب البيانات لحفظ التقرير."
                return@launch
            }

            isProcessing.value = true
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            val finalTitle = reportTitle.value.ifEmpty { "تقرير موازين عينات AFRILAB ($dateStr)" }
            val finalOperator = operatorName.value.ifEmpty { "مكتب موازين المختبر" }

            try {
                // 1. Persist automatically inside local Room database
                val rowId = repository.insertReport(
                    title = finalTitle,
                    operatorName = finalOperator,
                    items = candidateItems.value,
                    notes = reportNotes.value
                )

                val savedReport = repository.getReportById(rowId.toInt())
                if (savedReport != null) {
                    // 2. Generate and write structured Arabic Word Microsoft Document .doc file
                    val file = WordGenerator.generateWordReport(
                        context = context,
                        report = savedReport,
                        items = candidateItems.value
                    )
                    exportedFile.value = file
                } else {
                    errorMessage.value = "فشل التحقق من حفظ التقرير بقاعدة البيانات المحلية"
                }
            } catch (e: Exception) {
                errorMessage.value = "حدث خطأ أثناء حفظ أو تصدير التقرير: ${e.localizedMessage}"
            } finally {
                isProcessing.value = false
            }
        }
    }

    fun deleteSavedReport(reportId: Int) {
        viewModelScope.launch {
            repository.deleteReport(reportId)
        }
    }

    fun clearAllReports() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun deserializeWeightItems(json: String): List<WeightItem> {
        return repository.deserializeWeightItems(json)
    }

    fun clearExportResult() {
        exportedFile.value = null
    }

    fun clearErrorMessage() {
        errorMessage.value = null
    }
}

// Custom ViewModelFactory to enable passing repositories safely
class AfrilabViewModelFactory(private val repository: ReportRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AfrilabViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AfrilabViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
