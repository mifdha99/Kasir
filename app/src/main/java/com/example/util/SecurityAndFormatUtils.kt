package com.example.util

import java.security.MessageDigest
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object SecurityAndFormatUtils {

    private val localeId = Locale("in", "ID")
    private val currencyFormat = NumberFormat.getCurrencyInstance(localeId).apply {
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }
    private val numberFormat = NumberFormat.getNumberInstance(localeId).apply {
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }

    private val dateTimeFormatter = SimpleDateFormat("dd MMM yyyy, HH:mm", localeId)
    private val dateOnlyFormatter = SimpleDateFormat("dd MMM yyyy", localeId)
    private val receiptDateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", localeId)
    private val invoiceDateFormatter = SimpleDateFormat("yyMMdd", localeId)
    private val dateKeyFormatter = SimpleDateFormat("yyyy-MM-dd", localeId)

    private const val PORTION_NOTE_DELIM = "||P||"

    fun formatDateKey(timestamp: Long = System.currentTimeMillis()): String {
        return synchronized(dateKeyFormatter) {
            dateKeyFormatter.format(Date(timestamp))
        }
    }

    fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.trim().toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun formatRupiah(amount: Double, prefix: String = "Rp"): String {
        val rounded = kotlin.math.round(amount).toLong()
        val formatted = synchronized(numberFormat) {
            numberFormat.format(kotlin.math.abs(rounded))
        }
        return if (rounded < 0) "-$prefix $formatted" else "$prefix $formatted"
    }

    fun formatNumber(amount: Double): String {
        val rounded = kotlin.math.round(amount).toLong()
        return synchronized(numberFormat) {
            numberFormat.format(rounded)
        }
    }

    fun parseDoubleInput(input: String): Double {
        val cleaned = input.replace(".", "").replace(",", ".").replace(Regex("[^0-9.-]"), "")
        return cleaned.toDoubleOrNull() ?: 0.0
    }

    fun formatDateTime(timestamp: Long): String {
        return synchronized(dateTimeFormatter) {
            dateTimeFormatter.format(Date(timestamp))
        }
    }

    fun formatDateOnly(timestamp: Long): String {
        return synchronized(dateOnlyFormatter) {
            dateOnlyFormatter.format(Date(timestamp))
        }
    }

    fun formatReceiptDateTime(timestamp: Long): String {
        return synchronized(receiptDateFormatter) {
            receiptDateFormatter.format(Date(timestamp))
        }
    }

    fun generateInvoiceNumber(prefix: String, billingNumber: Int, timestamp: Long = System.currentTimeMillis()): String {
        val dateStr = synchronized(invoiceDateFormatter) {
            invoiceDateFormatter.format(Date(timestamp))
        }
        val cleanPrefix = prefix.trim().ifEmpty { "BIL" }.uppercase()
        val seqStr = billingNumber.coerceAtLeast(1).toString().padStart(3, '0')
        return "$cleanPrefix-$dateStr-$seqStr"
    }

    fun roundToNearestMultiple(value: Double, multiple: Int): Double {
        if (multiple <= 1) return value
        return kotlin.math.round(value / multiple) * multiple
    }

    fun getStartOfDay(timestamp: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun getEndOfDay(timestamp: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }

    fun getStartOfMonth(timestamp: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // --- PER-PORTION NOTE ENCODING & DECODING ---
    fun normalizePortionNotes(notes: List<String>, quantity: Int): List<String> {
        val safeQty = quantity.coerceAtLeast(1)
        return List(safeQty) { idx ->
            notes.getOrNull(idx)?.trim().orEmpty()
        }
    }

    fun encodePortionNotes(notes: List<String>, quantity: Int): String {
        val normalized = normalizePortionNotes(notes, quantity)
        if (normalized.all { it.isBlank() }) return ""
        return normalized.joinToString(PORTION_NOTE_DELIM) { it.replace(PORTION_NOTE_DELIM, " ") }
    }

    fun decodePortionNotes(encoded: String, quantity: Int): List<String> {
        val safeQty = quantity.coerceAtLeast(1)
        if (encoded.isBlank()) return List(safeQty) { "" }
        return if (encoded.contains(PORTION_NOTE_DELIM)) {
            val parts = encoded.split(PORTION_NOTE_DELIM)
            List(safeQty) { idx -> parts.getOrNull(idx)?.trim().orEmpty() }
        } else {
            List(safeQty) { idx -> if (idx == 0) encoded.trim() else "" }
        }
    }

    fun formatPortionNotesSummary(portionNotes: List<String>): List<String> {
        val nonBlankCount = portionNotes.count { it.isNotBlank() }
        if (nonBlankCount == 0) return emptyList()
        if (portionNotes.size == 1) {
            val first = portionNotes.first().trim()
            return if (first.isNotBlank()) listOf("Catatan: $first") else emptyList()
        }
        return portionNotes.mapIndexedNotNull { idx, note ->
            val clean = note.trim()
            if (clean.isNotBlank()) "Porsi ${idx + 1}: $clean" else null
        }
    }
}
