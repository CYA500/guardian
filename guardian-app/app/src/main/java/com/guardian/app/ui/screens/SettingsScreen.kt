package com.guardian.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardian.app.ui.theme.*

@Composable
fun SettingsScreen(
    quranPackage: String,
    onQuranPackageChange: (String) -> Unit,
    imageClassificationEnabled: Boolean,
    onImageClassificationToggle: (Boolean) -> Unit,
    level3LockHoursMin: Int,
    level3LockHoursMax: Int,
    onLevel3RangeChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var quranInput by remember { mutableStateOf(quranPackage) }
    var showQuranDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OffWhite)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "الإعدادات",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = OliveGreenDark
        )

        // ── Section: Allowed apps ────────────────────────────────────
        SettingsSection(title = "التطبيقات المسموح بها دائماً") {
            SettingsItemText(
                icon     = Icons.Default.MenuBook,
                title    = "تطبيق القرآن الكريم",
                subtitle = quranPackage,
                onClick  = { showQuranDialog = true }
            )
            HintText("المكالمات والرسائل القصيرة مسموح بها دائماً بصرف النظر عن مستوى القفل.")
        }

        // ── Section: Image analysis ──────────────────────────────────
        SettingsSection(title = "تحليل الصور") {
            SettingsItemToggle(
                icon     = Icons.Default.Image,
                title    = "تفعيل كشف الصور",
                subtitle = "يحلّل الصور الجديدة تلقائياً ويحذف المحتوى غير اللائق",
                checked  = imageClassificationEnabled,
                onToggle = onImageClassificationToggle
            )
        }

        // ── Section: Lock duration ───────────────────────────────────
        SettingsSection(title = "مدة القفل — المستوى 3") {
            Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(
                    "النطاق الحالي: $level3LockHoursMin – $level3LockHoursMax ساعة",
                    fontSize = 13.sp,
                    color    = OliveGreen,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))
                Text("الحد الأدنى (ساعات)", fontSize = 12.sp, color = Color.Gray)
                Slider(
                    value         = level3LockHoursMin.toFloat(),
                    onValueChange = { onLevel3RangeChange(it.toInt(), level3LockHoursMax) },
                    valueRange    = 1f..12f,
                    steps         = 10,
                    colors        = SliderDefaults.colors(thumbColor = OliveGreen, activeTrackColor = OliveGreen)
                )
                Text("الحد الأقصى (ساعات)", fontSize = 12.sp, color = Color.Gray)
                Slider(
                    value         = level3LockHoursMax.toFloat(),
                    onValueChange = { onLevel3RangeChange(level3LockHoursMin, it.toInt()) },
                    valueRange    = 12f..24f,
                    steps         = 11,
                    colors        = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold)
                )
            }
        }

        // ── Section: About ───────────────────────────────────────────
        SettingsSection(title = "حول التطبيق") {
            SettingsItemInfo(Icons.Default.Info,    "الإصدار",        "1.0.0")
            SettingsItemInfo(Icons.Default.Shield,  "نموذج الكشف",   "ML Kit + TFLite")
            SettingsItemInfo(Icons.Default.Security,"أذونات الجهاز", "مفعّلة")
        }

        Spacer(Modifier.height(40.dp))
    }

    // ── Quran package dialog ─────────────────────────────────────────
    if (showQuranDialog) {
        AlertDialog(
            onDismissRequest = { showQuranDialog = false },
            title = { Text("اسم حزمة تطبيق القرآن") },
            text  = {
                Column {
                    Text("أدخل اسم الحزمة الكامل لتطبيق القرآن المفضّل لديك:", fontSize = 13.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = quranInput,
                        onValueChange = { quranInput = it },
                        singleLine = true,
                        placeholder = { Text("com.quran.labs.androidquran") }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("أمثلة:\n• com.quran.labs.androidquran\n• com.globalquran.mobile.app\n• com.ayaat10.quran",
                        fontSize = 11.sp, color = Color.Gray, lineHeight = 18.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onQuranPackageChange(quranInput.trim())
                    showQuranDialog = false
                }) { Text("حفظ", color = OliveGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showQuranDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

// ─── Reusable section wrapper ────────────────────────────────────────────

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            fontSize   = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color      = OliveGreen,
            modifier   = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Card(
            modifier  = Modifier.fillMaxWidth(),
            colors    = CardDefaults.cardColors(containerColor = Color.White),
            shape     = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(modifier = Modifier.padding(4.dp), content = content)
        }
    }
}

@Composable
private fun SettingsItemToggle(
    icon: ImageVector, title: String, subtitle: String,
    checked: Boolean, onToggle: (Boolean) -> Unit
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = OliveGreen, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 11.sp, color = Color.Gray, lineHeight = 16.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onToggle,
            colors  = SwitchDefaults.colors(checkedThumbColor = OliveGreen, checkedTrackColor = Color(0xFFD0E8B8))
        )
    }
}

@Composable
private fun SettingsItemText(
    icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = OliveGreen, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1A1A1A))
                Text(subtitle, fontSize = 11.sp, color = Color.Gray, lineHeight = 16.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SettingsItemInfo(icon: ImageVector, label: String, value: String) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f), color = Color(0xFF1A1A1A))
        Text(value, fontSize = 13.sp, color = Color.Gray)
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text     = "ℹ️  $text",
        fontSize = 11.sp,
        color    = Color.Gray,
        lineHeight = 16.sp,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
    )
}
