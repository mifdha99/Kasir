package com.example.util

import org.json.JSONArray
import java.security.MessageDigest
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object SecurityAndFormatUtils {

    private val indonesiaLocale = Locale("id", "ID")

    fun hashPin(pin: String): String {
        val cleanPin = pin.trim()
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(("kasirku_salt_v1_$cleanPin").toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(inputPin: String, storedHash: String): Boolean {
        if (storedHash.isBlank()) return true
        return hashPin(inputPin) == storedHash
    }

    fun formatRupiah(amount: Double): String {
        val format = NumberFormat.getCurrencyInstance(indonesiaLocale)
        format.maximumFractionDigits = 0
        format.minimumFractionDigits = 0
        return format.format(amount)
    }

    fun formatNumber(amount: Double): String {
        val format = NumberFormat.getNumberInstance(indonesiaLocale)
        format.maximumFractionDigits = 0
        return format.format(amount)
    }

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("d MMMM yyyy", indonesiaLocale)
        return sdf.format(Date(timestamp))
    }

    fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm", indonesiaLocale)
        return sdf.format(Date(timestamp))
    }

    fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("d MMMM yyyy - HH:mm", indonesiaLocale)
        return sdf.format(Date(timestamp))
    }

    fun formatReceiptDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", indonesiaLocale)
        return sdf.format(Date(timestamp))
    }

    fun formatInvoiceDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyyMMdd", indonesiaLocale)
        return sdf.format(Date(timestamp))
    }

    fun formatBillingLabel(billingNumber: Int): String {
        val safeNum = if (billingNumber > 0) billingNumber else 1
        return "Billing $safeNum"
    }

    fun encodePortionNotes(notes: List<String>): String {
        val arr = JSONArray()
        notes.forEach { arr.put(it.trim()) }
        return arr.toString()
    }

    fun decodePortionNotes(json: String, fallbackNote: String = "", quantity: Int = 1): List<String> {
        if (json.isNotBlank()) {
            try {
                val arr = JSONArray(json)
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    list.add(arr.optString(i, "").trim())
                }
                if (list.isNotEmpty()) {
                    return normalizePortionNotes(list, quantity)
                }
            } catch (_: Exception) {
            }
        }
        val initial = if (fallbackNote.isNotBlank()) listOf(fallbackNote.trim()) else emptyList()
        return normalizePortionNotes(initial, quantity)
    }

    fun normalizePortionNotes(existing: List<String>, quantity: Int): List<String> {
        val count = quantity.coerceAtLeast(1)
        return List(count) { idx ->
            existing.getOrNull(idx)?.trim().orEmpty()
        }
    }

    fun summarizePortionNotes(notes: List<String>): String {
        val nonEmpty = notes.mapIndexedNotNull { idx, note ->
            val clean = note.trim()
            if (clean.isEmpty()) null
            else if (notes.size == 1) clean
            else "Porsi ${idx + 1}: $clean"
        }
        return nonEmpty.joinToString("; ")
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

    fun getStartOfYesterday(): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        return getStartOfDay(cal.timeInMillis)
    }

    fun getEndOfYesterday(): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        return getEndOfDay(cal.timeInMillis)
    }

    fun getStartOfDaysAgo(days: Int): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        return getStartOfDay(cal.timeInMillis)
    }

    fun getStartOfCurrentWeek(): Long {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        return getStartOfDay(cal.timeInMillis)
    }

    fun getStartOfCurrentMonth(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        return getStartOfDay(cal.timeInMillis)
    }

    fun getStartOfLastMonth(): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -1)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        return getStartOfDay(cal.timeInMillis)
    }

    fun getEndOfLastMonth(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.add(Calendar.DAY_OF_YEAR, -1)
        return getEndOfDay(cal.timeInMillis)
    }
}
