package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.StoreSettingsEntity
import com.example.data.TransactionWithItems
import com.example.util.SecurityAndFormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.UUID

data class DiscoveredBluetoothDevice(
    val name: String,
    val address: String,
    val isPaired: Boolean = true
)

object BluetoothPrinterService {

    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun getRequiredBluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
    }

    fun hasBluetoothPermissions(context: Context): Boolean {
        return getRequiredBluetoothPermissions().all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        val adapter = getBluetoothAdapter(context) ?: return false
        return try {
            adapter.isEnabled
        } catch (_: SecurityException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(context: Context): Result<List<DiscoveredBluetoothDevice>> = runCatching {
        if (!hasBluetoothPermissions(context)) {
            throw SecurityException("Izin Bluetooth belum diberikan. Silakan izinkan akses Bluetooth terlebih dahulu.")
        }
        val adapter = getBluetoothAdapter(context)
            ?: throw IllegalStateException("Perangkat HP ini tidak mendukung fitur Bluetooth.")
        if (!adapter.isEnabled) {
            throw IllegalStateException("Bluetooth sedang mati. Silakan aktifkan Bluetooth terlebih dahulu.")
        }

        val bonded: Set<BluetoothDevice> = adapter.bondedDevices ?: emptySet()
        bonded.map { device ->
            DiscoveredBluetoothDevice(
                name = device.name?.ifBlank { "Printer Bluetooth" } ?: "Printer Bluetooth",
                address = device.address,
                isPaired = true
            )
        }.sortedBy { it.name }
    }

    // --- FORMAT TIKET PESANAN DAPUR (TANPA METODE PEMBAYARAN DAN KEMBALIAN) ---
    fun formatKitchenTicketText(
        txWithItems: TransactionWithItems,
        paperSizeMm: Int = 58
    ): String {
        val lineWidth = if (paperSizeMm >= 80) 48 else 32
        val doubleDivider = "=".repeat(lineWidth)
        val tx = txWithItems.transaction
        val billingUpper = tx.billingDisplay.uppercase()

        val sb = StringBuilder()
        sb.appendLine(doubleDivider)
        sb.appendLine("STRUK PESANAN DAPUR")
        sb.appendLine(billingUpper)
        sb.appendLine("Waktu: ${SecurityAndFormatUtils.formatReceiptDateTime(tx.timestamp)}")
        if (tx.customerName.isNotBlank() && tx.customerName != "Pelanggan Umum") {
            sb.appendLine("Pelanggan: ${tx.customerName}")
        }
        sb.appendLine()

        for (item in txWithItems.items) {
            sb.appendLine("${item.productName} x${item.quantity}")
            val portionNotes = item.portionNotes
            val hasAnyPortionNote = portionNotes.any { it.isNotBlank() }
            if (hasAnyPortionNote) {
                portionNotes.forEachIndexed { idx, note ->
                    if (note.isNotBlank()) {
                        sb.appendLine("- Porsi ${idx + 1}: $note")
                    }
                }
            } else if (item.itemNote.isNotBlank()) {
                sb.appendLine("- Catatan: ${item.itemNote}")
            }
            sb.appendLine()
        }

        if (tx.notes.isNotBlank()) {
            sb.appendLine("Catatan Pesanan: ${tx.notes}")
            sb.appendLine()
        }
        sb.appendLine(doubleDivider)
        return sb.toString()
    }

    // --- FORMAT STRUK PEMBAYARAN SEDERHANA ---
    fun formatReceiptText(
        txWithItems: TransactionWithItems,
        settings: StoreSettingsEntity,
        paperSizeMm: Int = settings.paperSizeMm
    ): String {
        val lineWidth = if (paperSizeMm >= 80) 48 else 32
        val sb = StringBuilder()
        val tx = txWithItems.transaction

        fun center(text: String): String {
            return text.lines().joinToString("\n") { line ->
                val trimmed = line.trim()
                if (trimmed.length >= lineWidth) trimmed.take(lineWidth)
                else {
                    val leftPad = (lineWidth - trimmed.length) / 2
                    " ".repeat(leftPad) + trimmed
                }
            }
        }

        fun leftRight(left: String, right: String): String {
            val space = lineWidth - left.length - right.length
            return if (space >= 1) {
                left + " ".repeat(space) + right
            } else {
                val maxLeft = (lineWidth - right.length - 1).coerceAtLeast(4)
                left.take(maxLeft) + " " + right
            }
        }

        val divider = "-".repeat(lineWidth)
        val doubleDivider = "=".repeat(lineWidth)

        sb.appendLine(doubleDivider)
        sb.appendLine(center(settings.storeName.uppercase()))
        if (settings.storeAddress.isNotBlank()) {
            sb.appendLine(center(settings.storeAddress))
        }
        if (settings.storePhone.isNotBlank()) {
            sb.appendLine(center("Telp: ${settings.storePhone}"))
        }
        sb.appendLine()
        if (tx.status == "CANCELLED") {
            sb.appendLine(center("*** DIBATALKAN ***"))
        } else if (tx.status == "UNPAID") {
            sb.appendLine(center("*** BELUM BAYAR ***"))
        }
        sb.appendLine(tx.billingDisplay.uppercase())
        sb.appendLine("Waktu: ${SecurityAndFormatUtils.formatReceiptDateTime(tx.timestamp)}")
        if (tx.customerName.isNotBlank() && tx.customerName != "Pelanggan Umum") {
            sb.appendLine("Pelanggan: ${tx.customerName}")
        }
        sb.appendLine()

        for (item in txWithItems.items) {
            sb.appendLine(item.productName)
            val qtyPrice = "${item.quantity} x ${SecurityAndFormatUtils.formatRupiah(item.sellPrice)}"
            val itemSub = SecurityAndFormatUtils.formatRupiah(item.subtotal)
            sb.appendLine(leftRight(qtyPrice, itemSub))
            val portionNotes = item.portionNotes
            portionNotes.forEachIndexed { idx, note ->
                if (note.isNotBlank()) {
                    if (portionNotes.size == 1) {
                        sb.appendLine("  Catatan: $note")
                    } else {
                        sb.appendLine("  - Porsi ${idx + 1}: $note")
                    }
                }
            }
            sb.appendLine()
        }

        sb.appendLine(divider)
        if (tx.discountAmount > 0 || tx.taxAmount > 0 || tx.serviceFee > 0 || tx.roundingAmount != 0.0) {
            sb.appendLine(leftRight("SUBTOTAL", SecurityAndFormatUtils.formatRupiah(tx.subtotal)))
            if (tx.discountAmount > 0) {
                sb.appendLine(leftRight("DISKON", "-${SecurityAndFormatUtils.formatRupiah(tx.discountAmount)}"))
            }
            if (tx.taxAmount > 0) {
                sb.appendLine(
                    leftRight(
                        "PAJAK (${SecurityAndFormatUtils.formatNumber(tx.taxPercentage)}%)",
                        SecurityAndFormatUtils.formatRupiah(tx.taxAmount)
                    )
                )
            }
            if (tx.serviceFee > 0) {
                sb.appendLine(leftRight("BIAYA TAMBAHAN", SecurityAndFormatUtils.formatRupiah(tx.serviceFee)))
            }
            if (tx.roundingAmount != 0.0) {
                sb.appendLine(leftRight("PEMBULATAN", SecurityAndFormatUtils.formatRupiah(tx.roundingAmount)))
            }
        }
        sb.appendLine(leftRight("TOTAL", SecurityAndFormatUtils.formatRupiah(tx.totalAmount)))
        if (tx.status == "PAID" || tx.status == "COMPLETED") {
            val methodUpper = tx.paymentMethod.uppercase()
            sb.appendLine(leftRight("METODE BAYAR", methodUpper))
            sb.appendLine(leftRight(methodUpper, SecurityAndFormatUtils.formatRupiah(tx.amountPaid)))
            sb.appendLine(leftRight("KEMBALI", SecurityAndFormatUtils.formatRupiah(tx.changeAmount)))
        } else {
            sb.appendLine(leftRight("STATUS", if (tx.status == "UNPAID") "BELUM BAYAR" else "DIBATALKAN"))
        }
        sb.appendLine()
        if (settings.receiptFooter.isNotBlank()) {
            sb.appendLine(center(settings.receiptFooter))
        } else {
            sb.appendLine(center("TERIMA KASIH"))
        }
        sb.appendLine(doubleDivider)
        return sb.toString()
    }

    @SuppressLint("MissingPermission")
    suspend fun printTextToBluetooth(
        context: Context,
        rawText: String,
        settings: StoreSettingsEntity,
        printerAddressOverride: String? = null,
        copies: Int = 1,
        successLabel: String = "Berhasil dicetak"
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!hasBluetoothPermissions(context)) {
                throw SecurityException("Izin Bluetooth belum diberikan. Silakan berikan izin Bluetooth terlebih dahulu.")
            }
            val adapter = getBluetoothAdapter(context)
                ?: throw IllegalStateException("Perangkat tidak memiliki modul Bluetooth.")
            if (!adapter.isEnabled) {
                throw IllegalStateException("Bluetooth mati. Silakan nyalakan Bluetooth HP Anda.")
            }

            val address = (printerAddressOverride ?: settings.defaultPrinterAddress).trim()
            if (address.isEmpty()) {
                throw IllegalArgumentException("Pilih printer Bluetooth terlebih dahulu di Pengaturan Printer.")
            }

            val device = adapter.getRemoteDevice(address)
            var socket: BluetoothSocket? = null
            try {
                if (adapter.isDiscovering) {
                    adapter.cancelDiscovery()
                }
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
                val out: OutputStream = socket.outputStream

                val initPrinter = byteArrayOf(0x1B, 0x40)
                val alignLeft = byteArrayOf(0x1B, 0x61, 0x00)
                val feedAndCut = byteArrayOf(0x0A, 0x0A, 0x0A, 0x1D, 0x56, 0x41, 0x10)
                val textBytes = rawText.toByteArray(Charset.forName("GBK"))

                val totalCopies = copies.coerceIn(1, 5)
                repeat(totalCopies) {
                    out.write(initPrinter)
                    out.write(alignLeft)
                    out.write(textBytes)
                    out.write(feedAndCut)
                    out.flush()
                }
                "$successLabel ke ${device.name ?: address}"
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun printReceiptToBluetooth(
        context: Context,
        txWithItems: TransactionWithItems,
        settings: StoreSettingsEntity,
        printerAddressOverride: String? = null
    ): Result<String> {
        val text = formatReceiptText(txWithItems, settings, settings.paperSizeMm)
        return printTextToBluetooth(
            context = context,
            rawText = text,
            settings = settings,
            printerAddressOverride = printerAddressOverride,
            copies = settings.printCopies,
            successLabel = "Struk berhasil dicetak"
        )
    }

    @SuppressLint("MissingPermission")
    suspend fun printKitchenTicketToBluetooth(
        context: Context,
        txWithItems: TransactionWithItems,
        settings: StoreSettingsEntity,
        printerAddressOverride: String? = null
    ): Result<String> {
        val text = formatKitchenTicketText(txWithItems, settings.paperSizeMm)
        return printTextToBluetooth(
            context = context,
            rawText = text,
            settings = settings,
            printerAddressOverride = printerAddressOverride,
            copies = 1,
            successLabel = "Tiket pesanan dapur berhasil dicetak"
        )
    }

    @SuppressLint("MissingPermission")
    suspend fun printTestPage(
        context: Context,
        printerAddress: String,
        storeName: String,
        paperSizeMm: Int
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!hasBluetoothPermissions(context)) {
                throw SecurityException("Izin Bluetooth belum diberikan.")
            }
            val adapter = getBluetoothAdapter(context)
                ?: throw IllegalStateException("Bluetooth tidak tersedia di perangkat ini.")
            if (!adapter.isEnabled) {
                throw IllegalStateException("Bluetooth belum diaktifkan.")
            }

            val device = adapter.getRemoteDevice(printerAddress)
            var socket: BluetoothSocket? = null
            try {
                if (adapter.isDiscovering) adapter.cancelDiscovery()
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
                val out = socket.outputStream
                val width = if (paperSizeMm >= 80) 48 else 32
                val line = "=".repeat(width)
                val testContent = buildString {
                    appendLine(line)
                    appendLine("TES PRINTER KASIRKU RESTO")
                    appendLine(storeName)
                    appendLine("Ukuran Kertas: ${paperSizeMm}mm")
                    appendLine("Waktu: ${SecurityAndFormatUtils.formatDateTime(System.currentTimeMillis())}")
                    appendLine("Status: TERHUBUNG & SIAP CETAK")
                    appendLine(line)
                }
                out.write(byteArrayOf(0x1B, 0x40))
                out.write(testContent.toByteArray(Charset.forName("UTF-8")))
                out.write(byteArrayOf(0x0A, 0x0A, 0x0A))
                out.flush()
                "Tes cetak berhasil dikirim ke ${device.name ?: printerAddress}"
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
        }
    }
}
