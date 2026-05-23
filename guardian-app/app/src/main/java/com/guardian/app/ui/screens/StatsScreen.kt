package com.guardian.app.ui.screens

// ── StatsScreen ───────────────────────────────────────────────────────────

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardian.app.ui.theme.*

@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OffWhite)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "الإحصائيات",
            fontSize   = 22.sp,
            fontWeight = FontWeight.Bold,
            color      = OliveGreenDark,
            modifier   = Modifier.padding(bottom = 4.dp)
        )

        // Summary row
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigStat("٠",  "كشف الأسبوع",  OliveGreen,  Modifier.weight(1f))
            BigStat("٠",  "أيام بلا انتهاك", Gold,      Modifier.weight(1f))
        }

        // Weekly bar chart placeholder
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(containerColor = Color.White),
            shape    = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("نشاط الأسبوع", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    val days = listOf("س", "أ", "ن", "ث", "ر", "خ", "ج")
                    days.forEach { day ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .width(28.dp)
                                    .height(8.dp)
                                    .background(OliveLight.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(day, fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "لا توجد بيانات بعد — ابدأ رحلتك اليوم",
                    color    = Color.Gray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Achievement card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(containerColor = Color(0xFFFBF5E0)),
            shape    = RoundedCornerShape(16.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🏆", fontSize = 32.sp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("ابدأ رحلتك", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("حافظ على يوم واحد بلا انتهاك لفتح أول إنجاز", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun BigStat(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors   = CardDefaults.cardColors(containerColor = Color.White),
        shape    = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier            = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
        }
    }
}
