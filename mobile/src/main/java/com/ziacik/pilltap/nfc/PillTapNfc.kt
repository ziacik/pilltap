package com.ziacik.pilltap.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable

object PillTapNfc {
	const val MIME_TYPE = "application/vnd.com.ziacik.pilltap"
	private val payload = "take".toByteArray(Charsets.UTF_8)

	fun write(tag: Tag) {
		val message = NdefMessage(arrayOf(NdefRecord.createMime(MIME_TYPE, payload)))
		val bytes = message.toByteArray().size
		Ndef.get(tag)?.let { ndef ->
			ndef.connect()
			try {
				check(ndef.isWritable) { "Tag nie je zapisovateľný." }
				check(ndef.maxSize >= bytes) { "Tag je príliš malý." }
				ndef.writeNdefMessage(message)
			} finally { ndef.close() }
			return
		}
		NdefFormatable.get(tag)?.let { f ->
			f.connect()
			try { f.format(message) } finally { f.close() }
			return
		}
		error("Tento NFC tag nepodporuje NDEF.")
	}
}
