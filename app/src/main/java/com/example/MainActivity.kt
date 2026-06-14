package com.example

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import com.example.utils.WordGenerator
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppDatabase
import com.example.data.ReportEntity
import com.example.data.ReportRepository
import com.example.data.WeightItem
import com.example.ui.AfrilabViewModel
import com.example.ui.AfrilabViewModelFactory
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.StatusAlert
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: AfrilabViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Setup Database + Repository + ViewModel Factory
        val database = AppDatabase.getDatabase(this)
        val repository = ReportRepository(database.reportDao())
        val factory = AfrilabViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[AfrilabViewModel::class.java]

        setContent {
            MyApplicationTheme {
                // Ensure RTL layout support for proper Arabic interface
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Scaffold(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("main_scaffold"),
                        topBar = { AfrilabTopBar() }
                    ) { innerPadding ->
                        AfrilabAppContent(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AfrilabTopBar() {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "شارة ميزان",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = "مختبرات AFRILAB",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = "نظام قراءة أوزان العينات الفني وتصدير التقارير",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        actions = {
            Text(
                text = "مؤتمت بالكامل",
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .padding(end = 4.dp)
            )
        }
    )
}

@Composable
fun AfrilabAppContent(
    viewModel: AfrilabViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("تقرير جديد", "الأرشيف المحفوظ")

    // Collect variables
    val reports by viewModel.savedReports.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val exportedFile by viewModel.exportedFile.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    // Dialog state controllers
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialogItem by remember { mutableStateOf<WeightItem?>(null) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    // Floating error toast handling
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearErrorMessage()
        }
    }

    // Success Report Export dialog
    exportedFile?.let { file ->
        ReportExportSuccessDialog(
            file = file,
            onDismiss = { viewModel.clearExportResult() },
            onShare = { shareWordFile(context, file) },
            onOpen = { openWordFile(context, file) }
        )
    }

    // Manual item creator dialog
    if (showAddDialog) {
        AddWeightItemDialog(
            onDismiss = { showAddDialog = false },
            onSave = { id, weight, notes ->
                viewModel.addManualWeight(id, weight, notes)
                showAddDialog = false
            }
        )
    }

    // Edit item dialog
    showEditDialogItem?.let { item ->
        EditWeightItemDialog(
            item = item,
            onDismiss = { showEditDialogItem = null },
            onSave = { id, weight, notes ->
                viewModel.updateManualWeight(item.index, id, weight, notes)
                showEditDialogItem = null
            }
        )
    }

    // Clear history dialog
    if (showClearHistoryDialog) {
        ConfirmClearHistoryDialog(
            onDismiss = { showClearHistoryDialog = false },
            onConfirm = {
                viewModel.clearAllReports()
                showClearHistoryDialog = false
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabs.forEachIndexed { index, label ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    modifier = Modifier.testTag("tab_${index}"),
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (index == 0) Icons.Default.AddCircle else Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (selectedTab == 0) {
                // TAB 1: NEW REPORT SHEET Creator
                NewReportScreen(
                    viewModel = viewModel,
                    isProcessing = isProcessing,
                    isSyncing = isSyncing,
                    onAddNewItem = { showAddDialog = true },
                    onEditItem = { showEditDialogItem = it },
                    onGenerateReport = { viewModel.saveReportAndGenerateWord(context) }
                )
            } else {
                // TAB 2: HISTORY SHEETS
                HistoryScreen(
                    reports = reports,
                    viewModel = viewModel,
                    onClearAll = { showClearHistoryDialog = true }
                )
            }

            // Spinner/Overlay during AI computations
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier.padding(32.dp).widthIn(max = 320.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 5.dp,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                "ذكاء اصطناعي AFRILAB",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 16.sp
                            )
                            Text(
                                "جاري قراءة البيانات وصياغة تقرير وورد الفني بدقة وتنسيق منظم...",
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NewReportScreen(
    viewModel: AfrilabViewModel,
    isProcessing: Boolean,
    isSyncing: Boolean,
    onAddNewItem: () -> Unit,
    onEditItem: (WeightItem) -> Unit,
    onGenerateReport: () -> Unit
) {
    val context = LocalContext.current
    val candidateItems by viewModel.candidateItems.collectAsStateWithLifecycle()
    val inputText by viewModel.inputDataText.collectAsStateWithLifecycle()
    val rTitle by viewModel.reportTitle.collectAsStateWithLifecycle()
    val rOperator by viewModel.operatorName.collectAsStateWithLifecycle()
    val rNotes by viewModel.reportNotes.collectAsStateWithLifecycle()
    val selectedImage by viewModel.selectedImage.collectAsStateWithLifecycle()

    // Activity launcher for choosing an image from gallery
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = uriToBitmap(context, it)
            viewModel.selectedImage.value = bitmap
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // --- SECTION 1: DATA SOURCES (Afrilab Interface, Image QR, manual paste) ---
        ElevatedCard(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "١. مزامنة وسحب أوزان العينات",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "اختر الطريقة المناسبة لقراءة الأوزان من ميزان أو نظام Afrilab:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                )

                // Simulating connection button (Wi-Fi lab pull)
                Button(
                    onClick = { viewModel.pullDataFromAfrilab() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sync_afrilab_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isSyncing && !isProcessing
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                        Text("جاري الاتصال بسيرفر الأجهزة وسحب الموازين...")
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Text("اتصال فوري وسحب الأوزان من نظام Afrilab", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // Image scanner method
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (selectedImage == null) "إرفاق صورة الميزان" else "تعديل الصورة", fontSize = 12.sp)
                    }

                    if (selectedImage != null) {
                        Button(
                            onClick = { viewModel.parseDataWithAI() },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("مسح الصورة بالذكاء", fontSize = 12.sp)
                        }
                    }
                }

                // Show selected picture preview
                selectedImage?.let { bmp ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.05f))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "صورة الميزان المرفقة",
                            modifier = Modifier.fillMaxHeight().aspectRatio(1.5f)
                        )
                        IconButton(
                            onClick = { viewModel.selectedImage.value = null },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "حذف الصورة",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Log or manual text paste
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { viewModel.inputDataText.value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .testTag("telemetry_log_input"),
                    label = { Text("لصق سجل الموازين أو قيمTelemetry من Afrilab", fontSize = 12.sp) },
                    placeholder = { Text("مثال:\nعينة S-01: وزن 145.2g\nعينة S-02: وزن 141.5g", fontSize = 11.sp, color = Color.Gray) },
                    shape = RoundedCornerShape(8.dp)
                )

                if (inputText.isNotEmpty() && selectedImage == null) {
                    Button(
                        onClick = { viewModel.parseDataWithAI() },
                        modifier = Modifier.align(Alignment.End),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("استخراج الأوزان من النص عبر AI", fontSize = 12.sp)
                    }
                }
            }
        }

        // --- SECTION 2: DATASHEET TABLE EDITING ---
        ElevatedCard(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "٢. موازين العينات المقروءة",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "الأوزان المستخلصة الجاهزة للكتابة على Word",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { viewModel.clearCandidateList() },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "تفريغ القائمة", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = onAddNewItem,
                            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "عينة مادية جديدة", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // Statistcs quick dashboard
                val (count, avg, max) = viewModel.computeStats()
                if (count > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("العدد الكلي", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Text("$count عينات", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Column {
                            Text("الوزن المتوسط", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Text(String.format(Locale.ENGLISH, "%.3f g", avg), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = StatusSuccess)
                        }
                        Column {
                            Text("الذروة الأقصى", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Text("$max g", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // Grid list
                if (candidateItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(40.dp))
                            Text(
                                "لا يوجد أوزان محملة حالياً",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Gray
                            )
                            Text(
                                "جرب سحب البيانات الفوري أو مسح ورقة موازين.",
                                fontSize = 11.sp,
                                color = Color.Gray.copy(alpha = 0.8f)
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        candidateItems.forEach { item ->
                            WeightRowItem(
                                item = item,
                                onEdit = { onEditItem(item) },
                                onDelete = { viewModel.deleteWeight(item.index) }
                            )
                        }
                    }
                }
            }
        }

        // --- SECTION 3: REPORT CONFIG (Title, Operator, Notes) ---
        ElevatedCard(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "٣. بيانات واعتمادات التقرير",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = rTitle,
                    onValueChange = { viewModel.reportTitle.value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("report_title_input"),
                    label = { Text("عنوان التقرير الفني", fontSize = 12.sp) },
                    placeholder = { Text("مثال: تقرير موازين دفعة الرطوبة B-26", fontSize = 11.sp, color = Color.Gray) },
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = rOperator,
                    onValueChange = { viewModel.operatorName.value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("report_operator_input"),
                    label = { Text("م محلل المختبر (الفاحص)", fontSize = 12.sp) },
                    placeholder = { Text("مثال: م. علي الزهراني", fontSize = 11.sp, color = Color.Gray) },
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = rNotes,
                    onValueChange = { viewModel.reportNotes.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("ملاحظات فنية إضافية تظهر بمستند Word", fontSize = 12.sp) },
                    shape = RoundedCornerShape(8.dp),
                    minLines = 2
                )
            }
        }

        // --- BOTTOM GENERATE BUTTON ---
        Button(
            onClick = onGenerateReport,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("generate_word_button")
                .padding(bottom = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(24.dp))
                Text("حفظ التقرير تلقائياً وتوليد ملف WORD", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun WeightRowItem(
    item: WeightItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = when (item.status) {
        "مرتفع" -> StatusAlert
        "منخفض" -> StatusWarning
        else -> StatusSuccess
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${item.index}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.sampleId,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (item.notes.isNotEmpty()) {
                        Text(
                            text = item.notes,
                            fontSize = 10.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${item.weight} ${item.unit}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = item.status,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(
    reports: List<ReportEntity>,
    viewModel: AfrilabViewModel,
    onClearAll: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "سجلات تقارير الأوزان المحفوظة تلقائياً",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "قاعدة البيانات تحتوي على ${reports.size} تقريراً مؤرشفاّ",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            if (reports.isNotEmpty()) {
                OutlinedButton(
                    onClick = onClearAll,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("مسح الكل", fontSize = 11.sp)
                }
            }
        }

        if (reports.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.0f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "أرخصف فارغ",
                        tint = Color.LightGray,
                        modifier = Modifier.size(60.dp)
                    )
                    Text(
                        text = "الأرشيف فارغ حالياً!",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Text(
                        text = "جميع تقارير الموازين التي يتم تصديرها تحفظ هنا تلقائياً لسرعة مراجعتها ومشاركتها في المستقبل.",
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        color = Color.Gray.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(reports, key = { it.id }) { report ->
                    HistoryCardItem(
                        report = report,
                        onShare = {
                            val items = viewModel.deserializeWeightItems(report.detailsJson)
                            val file = WordGenerator.generateWordReport(context, report, items)
                            if (file != null) {
                                shareWordFile(context, file)
                            } else {
                                Toast.makeText(context, "فشل توليد التقرير لمشاركته", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onOpen = {
                            val items = viewModel.deserializeWeightItems(report.detailsJson)
                            val file = WordGenerator.generateWordReport(context, report, items)
                            if (file != null) {
                                openWordFile(context, file)
                            } else {
                                Toast.makeText(context, "فشل فتح التقرير", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDelete = { viewModel.deleteSavedReport(report.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryCardItem(
    report: ReportEntity,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val formattedDate = sdf.format(Date(report.timestamp))

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = report.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "التاريخ: $formattedDate | المحلل: ${report.operatorName}",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف التقرير السجل",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Stats row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.03f), RoundedCornerShape(4.dp))
                    .padding(6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "العينات: ${report.sampleCount}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(text = "متوسط الوزن: ${String.format(Locale.ENGLISH, "%.2f g", report.avgWeight)}", fontSize = 11.sp)
                Text(text = "أعلى وزن: ${report.maxWeight} g", fontSize = 11.sp, color = StatusSuccess)
            }

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "مشاركة المستند", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(
                    onClick = onOpen,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).padding(end = 4.dp)
                    )
                    Text("فتح في Word", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Dialog for report creation success
@Composable
fun ReportExportSuccessDialog(
    file: File,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = StatusSuccess,
                    modifier = Modifier.size(64.dp)
                )

                Text(
                    text = "تم التصدير والحفظ بنجاح!",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "تم كتابة موازين العينات وتنسيقها بشكل منظم للغاية داخل وثيقة وورد (.doc) وحُفظت تلقائياً في أرشيف قاعدة البيانات المحلي.",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                        .padding(10.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "اسم الملف: ${file.name}", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(text = "الحجم: ${String.format(Locale.ENGLISH, "%.2f KB", file.length() / 1024.0)}", fontSize = 10.sp, color = Color.Gray)
                        Text(text = "المسار: ملفات التطبيق الرسمية/Documents", fontSize = 9.sp, color = Color.Gray)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("مشاركة", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onOpen,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("فتح المستند", fontSize = 12.sp)
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("إغلاق النافذة", fontSize = 12.sp)
                }
            }
        }
    }
}

// Dialog to add individual manual item
@Composable
fun AddWeightItemDialog(
    onDismiss: () -> Unit,
    onSave: (String, Double, String) -> Unit
) {
    var sampleId by remember { mutableStateOf("") }
    var weightText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "إضافة عينة مادية موازين جديدة",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = sampleId,
                    onValueChange = { sampleId = it },
                    label = { Text("رمز معرف العينة (Sample ID)", fontSize = 11.sp) },
                    placeholder = { Text("مثال: S-06", fontSize = 11.sp) },
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = { Text("الوزن المقاس (g)", fontSize = 11.sp) },
                    placeholder = { Text("مثال: 142.6", fontSize = 11.sp) },
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    )
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات إضافية", fontSize = 11.sp) },
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (isError) {
                    Text("الرجاء التحقق من المدخلات وكتابة قيمة وزن صحيحة", color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("إلغاء") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val weight = weightText.toDoubleOrNull()
                            if (sampleId.trim().isEmpty() || weight == null) {
                                isError = true
                            } else {
                                onSave(sampleId, weight, notes)
                            }
                        }
                    ) {
                        Text("إضافة العينة")
                    }
                }
            }
        }
    }
}

// Dialog to edit individual item
@Composable
fun EditWeightItemDialog(
    item: WeightItem,
    onDismiss: () -> Unit,
    onSave: (String, Double, String) -> Unit
) {
    var sampleId by remember { mutableStateOf(item.sampleId) }
    var weightText by remember { mutableStateOf(item.weight.toString()) }
    var notes by remember { mutableStateOf(item.notes) }
    var isError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "تعديل وزن ومعطيات عينة ${item.index}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = sampleId,
                    onValueChange = { sampleId = it },
                    label = { Text("رمز معرف العينة (Sample ID)", fontSize = 11.sp) },
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = { Text("الوزن المقاس (g)", fontSize = 11.sp) },
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    )
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات إضافية", fontSize = 11.sp) },
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (isError) {
                    Text("الرجاء مد وزن رقمي صحيح واسم عينة", color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("إلغاء") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val weight = weightText.toDoubleOrNull()
                            if (sampleId.trim().isEmpty() || weight == null) {
                                isError = true
                            } else {
                                onSave(sampleId, weight, notes)
                            }
                        }
                    ) {
                        Text("حفظ التغييرات")
                    }
                }
            }
        }
    }
}

// Dialog to confirm clear history
@Composable
fun ConfirmClearHistoryDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مسح الأرشيف العام؟") },
        text = { Text("هل أنت متأكد من رغبتك بالقيام بمسح وإفراغ جميع تقارير المختبرات المسجلة بقاعدة بيانات هذا الجهاز تلقائياً؟ هذا الإجراء غير قابل للتراجع.") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("تأكيد مسح الأرشيف")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

// Conversions & File Operations Helpers
fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        BitmapFactory.decodeStream(inputStream)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun shareWordFile(context: Context, file: File) {
    try {
        val authority = "${context.packageName}.provider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/msword"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "تقرير موازين Afrilab للتحاليل")
            putExtra(Intent.EXTRA_TEXT, "مرفق بتقرير موازين العينات الفني الصادر تلقائياً من نظام Afrilab للهواتف.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(Intent.createChooser(intent, "شارك تقرير Word عبر..."))
    } catch (e: Exception) {
        Toast.makeText(context, "فشل في مشاركة الملف: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}

fun openWordFile(context: Context, file: File) {
    try {
        val authority = "${context.packageName}.provider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/msword")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback option: share if no compatible reader is configured
        Toast.makeText(context, "لم يتم العثور على تطبيق متوافق لفتح مستندات وورد مباشرة. جاري تحويلك لمشاركة المستند.", Toast.LENGTH_LONG).show()
        shareWordFile(context, file)
    }
}
