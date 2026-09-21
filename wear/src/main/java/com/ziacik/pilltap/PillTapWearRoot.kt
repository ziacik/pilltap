package com.ziacik.pilltap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val WearCoral = Color(0xFFF26B5B)
private val WearApricot = Color(0xFFFFA56E)
private val WearInk = Color(0xFF171215)
private val WearCream = Color(0xFFFFF8F4)
private val WearMuted = Color(0xFFBFAEB6)

@Composable
internal fun PillTapWearRoot(
	taken: Boolean,
	takenAt: Long,
	nfcStatus: NfcStatus,
	nfcError: String?,
	onMarkTaken: () -> Unit,
) {
	MaterialTheme {
		Box(Modifier.fillMaxSize().background(WearInk).padding(12.dp), contentAlignment = Alignment.Center) {
			Column(
				modifier = Modifier.fillMaxWidth(),
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.Center,
			) {
				Text("PillTap", color = WearCream, fontWeight = FontWeight.Bold)
				Spacer(Modifier.height(8.dp))
				if (taken) {
					Box(
						Modifier.size(104.dp).background(Brush.linearGradient(listOf(WearCoral, WearApricot)), CircleShape),
						contentAlignment = Alignment.Center,
					) {
						Column(horizontalAlignment = Alignment.CenterHorizontally) {
							Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
							Text("Užité", color = Color.White, fontWeight = FontWeight.Bold)
							if (takenAt > 0L) Text(formatWearTime(takenAt), color = Color.White)
						}
					}
				} else {
					Box(
						Modifier.fillMaxWidth()
							.background(WearCoral, RoundedCornerShape(50))
							.clickable(onClick = onMarkTaken)
							.padding(vertical = 14.dp),
						contentAlignment = Alignment.Center,
					) {
						Text("Užil som liek", color = Color.White, fontWeight = FontWeight.Bold)
					}
				}
				Spacer(Modifier.height(9.dp))
				Text(nfcLabel(nfcStatus), color = if (nfcStatus == NfcStatus.ERROR) WearCoral else WearMuted)
				if (nfcStatus == NfcStatus.ERROR && nfcError != null) {
					Text(nfcError, color = WearMuted)
				}
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
