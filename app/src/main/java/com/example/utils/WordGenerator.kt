package com.example.utils

import android.content.Context
import android.os.Environment
import com.example.data.ReportEntity
import com.example.data.WeightItem
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WordGenerator {

    /**
     * Generates a beautifully formatted HTML-based Microsoft Word document (.doc)
     * containing the report weights list, metadata, and stats summaries.
     * Saves the file automatically onto external storage.
     */
    fun generateWordReport(
        context: Context,
        report: ReportEntity,
        items: List<WeightItem>
    ): File? {
        val timestampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(report.timestamp))
        val fileName = "Afrilab_Report_$timestampStr.doc"

        // Ensure App Documents directory exists
        val documentsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        if (documentsDir != null && !documentsDir.exists()) {
            documentsDir.mkdirs()
        }

        val file = File(documentsDir, fileName)

        val htmlContent = buildHtmlReport(report, items)

        return try {
            val fos = FileOutputStream(file)
            val osw = OutputStreamWriter(fos, "UTF-8")
            osw.write(htmlContent)
            osw.flush()
            osw.close()
            fos.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun buildHtmlReport(report: ReportEntity, items: List<WeightItem>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val dateString = dateFormat.format(Date(report.timestamp))

        val itemsHtmlRows = StringBuilder()
        for (item in items) {
            val statusColor = when (item.status) {
                "مرتفع" -> "#D32F2F" // Dark Red
                "منخفض" -> "#EF6C00" // Orange-ish
                else -> "#2E7D32"     // Green
            }

            itemsHtmlRows.append("""
                <tr>
                    <td style="border: 1px solid #B0BEC5; padding: 10px; text-align: center; font-size: 11pt;">${item.index}</td>
                    <td style="border: 1px solid #B0BEC5; padding: 10px; text-align: right; font-weight: bold; font-size: 11pt;">${item.sampleId}</td>
                    <td style="border: 1px solid #B0BEC5; padding: 10px; text-align: center; font-size: 12pt; font-weight: bold; color: #004D40;">${item.weight}</td>
                    <td style="border: 1px solid #B0BEC5; padding: 10px; text-align: center; font-size: 11pt; color: #546E7A;">${item.unit}</td>
                    <td style="border: 1px solid #B0BEC5; padding: 10px; text-align: center; font-weight: bold; color: $statusColor; font-size: 11pt;">${item.status}</td>
                    <td style="border: 1px solid #B0BEC5; padding: 10px; text-align: right; font-size: 10pt; color: #546E7A;">${item.notes.ifEmpty { "-" }}</td>
                </tr>
            """.trimIndent())
        }

        return """
            <!DOCTYPE html>
            <html dir="rtl" lang="ar">
            <head>
                <meta http-equiv="Content-Type" content="text/html; charset=utf-8">
                <title>${report.title}</title>
                <style>
                    body {
                        font-family: 'Segoe UI', Tahoma, Arial, sans-serif;
                        color: #263238;
                        background-color: #ffffff;
                        margin: 20px;
                        line-height: 1.5;
                    }
                    .header-table {
                        width: 100%;
                        border-collapse: collapse;
                        margin-bottom: 25px;
                        background-color: #004D40;
                        color: #ffffff;
                        border-radius: 6px;
                        overflow: hidden;
                    }
                    .header-content {
                        padding: 20px;
                        text-align: center;
                    }
                    .header-title-en {
                        font-family: Arial, Helvetica, sans-serif;
                        font-size: 12pt;
                        letter-spacing: 2px;
                        opacity: 0.85;
                        margin-bottom: 5px;
                    }
                    .header-title-ar {
                        font-size: 22pt;
                        font-weight: bold;
                        margin: 0;
                    }
                    .subtitle {
                        font-size: 10pt;
                        font-style: italic;
                        opacity: 0.8;
                    }
                    .meta-table {
                        width: 100%;
                        border-collapse: collapse;
                        margin-bottom: 20px;
                        background-color: #F5F7F8;
                    }
                    .meta-label {
                        width: 15%;
                        background-color: #00796B;
                        color: #ffffff;
                        font-weight: bold;
                        padding: 8px 12px;
                        text-align: right;
                        border: 1px solid #004D40;
                        font-size: 10.5pt;
                    }
                    .meta-value {
                        width: 35%;
                        padding: 8px 12px;
                        border: 1px solid #CFD8DC;
                        text-align: right;
                        font-size: 10.5pt;
                    }
                    .dashboard-table {
                        width: 100%;
                        border-collapse: collapse;
                        margin-bottom: 25px;
                    }
                    .dashboard-card {
                        width: 25%;
                        border: 2px solid #004D40;
                        background-color: #E0F2F1;
                        padding: 15px;
                        text-align: center;
                    }
                    .card-label {
                        font-size: 10pt;
                        color: #004D40;
                        font-weight: bold;
                        text-transform: uppercase;
                    }
                    .card-value {
                        font-size: 18pt;
                        font-weight: bold;
                        color: #00796B;
                        margin-top: 5px;
                    }
                    .data-table {
                        width: 100%;
                        border-collapse: collapse;
                        margin-bottom: 30px;
                    }
                    .data-table th {
                        background-color: #37474F;
                        color: #ffffff;
                        padding: 12px 10px;
                        text-align: center;
                        font-weight: bold;
                        border: 1px solid #263238;
                        font-size: 11pt;
                    }
                    .notes-box {
                        border: 1px dashed #004D40;
                        background-color: #F9FBE7;
                        padding: 15px;
                        margin-bottom: 35px;
                        border-radius: 4px;
                    }
                    .notes-title {
                        font-weight: bold;
                        color: #33691E;
                        font-size: 12pt;
                        margin-bottom: 8px;
                    }
                    .notes-body {
                        font-size: 11pt;
                        color: #3E2723;
                    }
                    .signatures-table {
                        width: 100%;
                        border-collapse: collapse;
                        margin-top: 50px;
                    }
                    .signature-cell {
                        width: 50%;
                        text-align: center;
                        vertical-align: top;
                        font-size: 11pt;
                    }
                    .signature-line {
                        width: 60%;
                        border-bottom: 1px solid #78909C;
                        margin: 25px auto 8px auto;
                    }
                </style>
            </head>
            <body>

                <!-- Header Branding Banner -->
                <table class="header-table">
                    <tr>
                        <td class="header-content">
                            <div class="header-title-en">AFRILAB QUALITY CONTROL LABS</div>
                            <h1 class="header-title-ar">مختبرات أفريلاب لرقابة الجودة والتحاليل</h1>
                            <div class="subtitle">نظام موازين العينات الرقمي المؤتمت - تقرير فني رسمي</div>
                        </td>
                    </tr>
                </table>

                <!-- Report General Metadata -->
                <h3 style="color: #004D40; border-bottom: 2px solid #004D40; padding-bottom: 5px; font-size: 14pt;">١. بيانات التقرير العامة</h3>
                <table class="meta-table">
                    <tr>
                        <td class="meta-label">اسم التقرير:</td>
                        <td class="meta-value">${report.title}</td>
                        <td class="meta-label">تاريخ الاصدار:</td>
                        <td class="meta-value">$dateString</td>
                    </tr>
                    <tr>
                        <td class="meta-label">محلل المختبر:</td>
                        <td class="meta-value">${report.operatorName}</td>
                        <td class="meta-label">مصدر البيانات:</td>
                        <td class="meta-value">موازين نظام Afrilab الرقمي</td>
                    </tr>
                </table>

                <!-- Statistics Summary Dashboard -->
                <h3 style="color: #004D40; border-bottom: 2px solid #004D40; padding-bottom: 5px; font-size: 14pt;">٢. المؤشرات الإحصائية العامة للعينات</h3>
                <table class="dashboard-table">
                    <tr>
                        <td class="dashboard-card" style="border-right-width: 2px;">
                            <div class="card-label">إجمالي العينات</div>
                            <div class="card-value">${report.sampleCount}</div>
                        </td>
                        <td class="dashboard-card" style="background-color: #E8F5E9; border-color: #2E7D32;">
                            <div class="card-label" style="color: #2E7D32;">متوسط الوزن</div>
                            <div class="card-value" style="color: #2E7D32;">${String.format(Locale.ENGLISH, "%.3f", report.avgWeight)} g</div>
                        </td>
                        <td class="dashboard-card">
                            <div class="card-label">أعلى وزن مسجل</div>
                            <div class="card-value">${report.maxWeight} g</div>
                        </td>
                        <td class="dashboard-card">
                            <div class="card-label">أقل وزن مسجل</div>
                            <div class="card-value">${report.minWeight} g</div>
                        </td>
                    </tr>
                </table>

                <!-- Detailed Weights Table -->
                <h3 style="color: #004D40; border-bottom: 2px solid #004D40; padding-bottom: 5px; font-size: 14pt;">٣. جدول تفاصيل أوزان العينات المفصل</h3>
                <table class="data-table">
                    <thead>
                        <tr>
                            <th style="width: 8%;">مسلسل</th>
                            <th style="width: 27%;">رمز المعرف للعينة (Sample ID)</th>
                            <th style="width: 17%;">الوزن المقاس</th>
                            <th style="width: 10%;">الوحدة</th>
                            <th style="width: 15%;">حالة مطابقة الوزن</th>
                            <th style="width: 23%;">ملاحظات إضافية</th>
                        </tr>
                    </thead>
                    <tbody>
                        $itemsHtmlRows
                    </tbody>
                </table>

                <!-- Notes Block -->
                ${if (report.notes.isNotEmpty()) {
                    """
                    <div class="notes-box">
                        <div class="notes-title">ملاحظات فنية وتوصيات عامة:</div>
                        <div class="notes-body">${report.notes}</div>
                    </div>
                    """.trimIndent()
                } else ""}

                <!-- Signatures / Approval Line -->
                <table class="signatures-table">
                    <tr>
                        <td class="signature-cell">
                            <strong>محلل المختبر المسؤول / الفاحص</strong>
                            <div class="signature-line"></div>
                            <span>الاسم الرسمي: __________________</span><br>
                            <span style="font-size: 9pt; color: #78909C; margin-top: 5px; display: inline-block;">نظام موازين Afrilab الإلكتروني</span>
                        </td>
                        <td class="signature-cell">
                            <strong>مدير تأكيد وضبط جودة المختبر (QA/QC)</strong>
                            <div class="signature-line"></div>
                            <span>التوقيع والختم الرسمي: __________________</span><br>
                            <span style="font-size: 9pt; color: #78909C; margin-top: 5px; display: inline-block;">صادر بتاريخ التقرير المؤتمت المذكور أعلاه</span>
                        </td>
                    </tr>
                </table>

            </body>
            </html>
        """.trimIndent()
    }
}
