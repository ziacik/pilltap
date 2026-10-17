package com.ziacik.pilltap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val WearCoral = Color(0xFFF26B5B)
private val WearInk = Color(0xFF171215)
private val WearCream = Color(0xFFFFF8F4)
private val WearMuted = Color(0xFFBFAEB6)

@Composable
internal fun PillTapWearRoot(
    doses: List<WearDose>,
    nfcStatus: NfcStatus,
    nfcError: String?,
    onMarkTaken: (Long) -> Unit,
) {
    MaterialTheme {
        Box(Modifier.fillMaxSize().background(WearInk), contentAlignment = Alignment.Center) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text("PillTap", color = WearCream, fontWeight = FontWeight.Bold)
                if (doses.isEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Žiadne aktívne dávky", color = WearMuted)
                    Text("Pridaj liek v telefóne", color = WearMuted)
                } else {
                    Text("${doses.count { it.takenAt > 0L }} / ${doses.size} dnes", color = WearMuted)
                    doses.forEach { dose ->
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (dose.takenAt > 0) Color(0xFF263E34) else Color(0xFF38282C), RoundedCornerShape(18.dp))
                                .clickable(enabled = dose.takenAt == 0L) { onMarkTaken(dose.slotId) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(dose.medicineName, color = WearCream, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (dose.takenAt > 0L) "Užité ${formatWearTime(dose.takenAt)}" else dose.time,
                                    color = WearMuted,
                                )
                            }
                            Text(if (dose.takenAt > 0L) "✓" else "+", color = WearCoral, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(nfcLabel(nfcStatus), color = if (nfcStatus == NfcStatus.ERROR) WearCoral else WearMuted)
                if (nfcStatus == NfcStatus.ERROR && nfcError != null) Text(nfcError, color = WearMuted)
            }
        }
    }
}

private fun nfcLabel(status: NfcStatus): String = when (status) {
    NfcStatus.READY -> "NFC pripravené"
    NfcStatus.DISABLED -> "NFC vypnuté"
    NfcStatus.UNAVAILABLE -> "NFC nedostupné"
    NfcStatus.ERROR -> "NFC reader zlyhal"
}

private fun formatWearTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
