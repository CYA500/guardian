package com.guardian.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardian.app.lock.LockState
import com.guardian.app.ui.theme.*

@Composable
fun HomeScreen(
    lockState: LockState,
    vpnActive: Boolean,
    accessibilityActive: Boolean,
    onToggleVpn: () -> Unit,
    onToggleAccessibility: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gold    = Gold
    val olive   = OliveGreen
    val offWhite = OffWhite

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(offWhite)
            .verticalScroll(scrollState)
            .padding(bottom = 80.dp)
    ) {
        // ── Header banner ────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(OliveGreenDark, OliveGreen))
                )
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                ShieldPulse(isActive = vpnActive || accessibilityActive)
                Spacer(Modifier.height(12.dp))
                Text(
                    "الحارس",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (lockState.isLocked) "⚠️ القفل نشط" else "الحماية جاهزة",
                    color = if (lockState.isLocked) Gold else Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Active lock card ─────────────────────────────────────────
        if (lockState.isLocked) {
            ActiveLockCard(lockState, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(16.dp))
        }

        // ── Status cards ─────────────────────────────────────────────
        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            StatusCard(
                title    = "فلترة DNS / VPN",
                subtitle = "يحجب المواقع المحظورة على مستوى الشبكة",
                icon     = Icons.Default.Wifi,
                active   = vpnActive,
                onToggle = onToggleVpn
            )

            StatusCard(
                title    = "مراقبة لوحة المفاتيح",
                subtitle = "يكتشف الكلمات والروابط المشبوهة فور كتابتها",
                icon     = Icons.Default.Keyboard,
                active   = accessibilityActive,
                onToggle = onToggleAccessibility
            )

            InfoCard(
                title = "حماية الصور",
                subtitle = "يحلّل الصور تلقائياً ويحذف المحتوى غير اللائق",
                icon  = Icons.Default.Image,
                color = olive
            )
        }

        Spacer(Modifier.height(20.dp))

        // ── Quick stats ───────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatMini("0", "كشف اليوم",   Icons.Default.Search,   Modifier.weight(1f))
            StatMini("0", "محجوب اليوم", Icons.Default.Block,    Modifier.weight(1f))
            StatMini("0", "محفوظ",       Icons.Default.Favorite, Modifier.weight(1f))
        }

        Spacer(Modifier.height(24.dp))

        // ── Islamic reminder ──────────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFBF5E0)),
            shape  = RoundedCornerShape(16.dp)
        ) {
            Text(
                text       = "﴿وَمَن يَتَّقِ اللهَ يَجعَل لَهُ مَخرَجاً﴾\n[الطلاق: ٢]",
                modifier   = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                color      = Color(0xFF5A4200),
                fontSize   = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign  = TextAlign.Center,
                lineHeight = 30.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun ShieldPulse(isActive: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "shield")
    val scale by infiniteTransition.animateFloat(
        initialValue   = 1f,
        targetValue    = if (isActive) 1.12f else 1f,
        animationSpec  = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label          = "scale"
    )
    Box(
        modifier = Modifier
            .size(80.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Text("🛡", fontSize = 40.sp)
    }
}

@Composable
private fun ActiveLockCard(lockState: LockState, modifier: Modifier = Modifier) {
    val remainingMs = lockState.remainingMs
    val s = remainingMs / 1000
    val formatted = "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = Color(0xFF1A0A00)),
        shape    = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("🔒 القفل نشط — المستوى ${lockState.level}", color = Gold, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(formatted, color = GoldLight, fontSize = 32.sp, fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            Text("الوقت المتبقي", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun StatusCard(
    title: String, subtitle: String,
    icon: ImageVector, active: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = if (active) Color(0xFFEFF7E8) else Color.White
        ),
        shape    = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = if (active) OliveGreen else Color.Gray, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color(0xFF1A1A1A))
                Text(subtitle, fontSize = 12.sp, color = Color.Gray, lineHeight = 18.sp)
            }
            Switch(
                checked  = active,
                onCheckedChange = { onToggle() },
                colors   = SwitchDefaults.colors(checkedThumbColor = OliveGreen, checkedTrackColor = Color(0xFFD0E8B8))
            )
        }
    }
}

@Composable
private fun InfoCard(title: String, subtitle: String, icon: ImageVector, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = Color(0xFFF0F8E8)),
        shape    = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(subtitle, fontSize = 12.sp, color = Color.Gray, lineHeight = 18.sp)
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.CheckCircle, null, tint = color, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun StatMini(value: String, label: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors   = CardDefaults.cardColors(containerColor = Color.White),
        shape    = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = OliveGreen, modifier = Modifier.size(20.dp))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OliveGreenDark)
            Text(label, fontSize = 10.sp, color = Color.Gray, textAlign = TextAlign.Center, lineHeight = 14.sp)
        }
    }
}
