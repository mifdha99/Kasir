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

        sb.appendLine(center(settings.storeName.uppercase()))
        if (settings.storeAddress.isNotBlank()) {
            sb.appendLine(center(settings.storeAddress))
        }
        if (settings.storePhone.isNotBlank()) {
            sb.appendLine(center("Telp: ${settings.storePhone}"))
        }
        if (settings.storeNpwp.isNotBlank()) {
            sb.appendLine(center("NPWP: ${settings.storeNpwp}"))
        }
        sb.appendLine(doubleDivider)
        if (tx.status == "CANCELLED") {
            sb.appendLine(center("*** TRANSAKSI DIBATALKAN ***"))
            sb.appendLine(divider)
        }
        sb.appendLine(leftRight("No. Struk", tx.invoiceNumber))
        sb.appendLine(leftRight("Tanggal", SecurityAndFormatUtils.formatDate(tx.timestamp)))
        sb.appendLine(leftRight("Jam", SecurityAndFormatUtils.formatTime(tx.timestamp)))
        sb.appendLine(leftRight("Kasir", tx.cashierName))
        sb.appendLine(leftRight("Pelanggan", tx.customerName))
        sb.appendLine(divider)

        for (item in txWithItems.items) {
            sb.appendLine(item.productName)
            val qtyPrice = "${item.quantity} ${item.unit} x ${SecurityAndFormatUtils.formatNumber(item.sellPrice)}"
            val itemSub = SecurityAndFormatUtils.formatRupiah(item.subtotal)
            sb.appendLine(leftRight("  $qtyPrice", itemSub))
            if (item.itemNote.isNotBlank()) {
                sb.appendLine("  *Catatan: ${item.itemNote}")
            }
        }

        sb.appendLine(divider)
        sb.appendLine(leftRight("Subtotal", SecurityAndFormatUtils.formatRupiah(tx.subtotal)))
        if (tx.discountAmount > 0) {
            sb.appendLine(leftRight("Diskon", "-${SecurityAndFormatUtils.formatRupiah(tx.discountAmount)}"))
        }
        if (tx.taxAmount > 0) {
            sb.appendLine(
                leftRight(
                    "Pajak (${SecurityAndFormatUtils.formatNumber(tx.taxPercentage)}%)",
                    SecurityAndFormatUtils.formatRupiah(tx.taxAmount)
                )
            )
        }
        if (tx.serviceFee > 0) {
            sb.appendLine(leftRight("Biaya Tambahan", SecurityAndFormatUtils.formatRupiah(tx.serviceFee)))
        }
        if (tx.roundingAmount != 0.0) {
            sb.appendLine(leftRight("Pembulatan", SecurityAndFormatUtils.formatRupiah(tx.roundingAmount)))
        }
        sb.appendLine(doubleDivider)
        sb.appendLine(leftRight("TOTAL AKHIR", SecurityAndFormatUtils.formatRupiah(tx.totalAmount)))
        sb.appendLine(leftRight("Metode Bayar", tx.paymentMethod))
        sb.appendLine(leftRight("Uang Diterima", SecurityAndFormatUtils.formatRupiah(tx.amountPaid)))
        sb.appendLine(leftRight("Kembalian", SecurityAndFormatUtils.formatRupiah(tx.changeAmount)))
        if (tx.notes.isNotBlank()) {
            sb.appendLine(divider)
            sb.appendLine("Catatan: ${tx.notes}")
        }
        if (tx.status == "CANCELLED" && tx.cancelReason.isNotBlank()) {
            sb.appendLine(divider)
            sb.appendLine("Alasan Batal: ${tx.cancelReason}")
        }
        sb.appendLine(divider)
        if (settings.receiptFooter.isNotBlank()) {
            sb.appendLine(center(settings.receiptFooter))
        }
        return sb.toString()
    }

    @SuppressLint("MissingPermission")
    suspend fun printReceiptToBluetooth(
        context: Context,
        txWithItems: TransactionWithItems,
        settings: StoreSettingsEntity,
        printerAddressOverride: String? = null
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
                throw IllegalStateException("Belum ada printer Bluetooth default yang dipilih di Pengaturan Printer.")
            }

            val device = try {
                adapter.getRemoteDevice(address)
            } catch (e: IllegalArgumentException) {
                throw IllegalStateException("Alamat MAC Printer Bluetooth tidak valid: $address")
            }

            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }

            val formattedText = formatReceiptText(
                txWithItems = txWithItems,
                settings = settings,
                paperSizeMm = settings.paperSizeMm
            )
            val copies = settings.printCopies.coerceIn(1, 5)

            var socket: BluetoothSocket? = null
            try {
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
                val out: OutputStream = socket.outputStream

                // ESC/POS Initialize printer
                val escInit = byteArrayOf(0x1B, 0x40)
                val escAlignLeft = byteArrayOf(0x1B, 0x61, 0x00)
                val escFeedAndCut = byteArrayOf(0x0A, 0x0A, 0x0A, 0x1D, 0x56, 0x42, 0x00)

                for (copy in 1..copies) {
                    out.write(escInit)
                    out.write(escAlignLeft)
                    out.write(formattedText.toByteArray(Charset.forName("GBK")))
                    out.write(escFeedAndCut)
                    out.flush()
                }
                Thread.sleep(350)
                "Struk berhasil dicetak ke ${device.name ?: address} ($copies salinan)."
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Gagal terhubung ke printer '${device.name ?: address}'. Pastikan printer thermal menyala, kertas tersedia, dan berada dalam jangkauan Bluetooth."
                )
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun testPrintBluetooth(
        context: Context,
        settings: StoreSettingsEntity,
        printerAddress: String,
        printerName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!hasBluetoothPermissions(context)) {
                throw SecurityException("Izin Bluetooth belum diberikan.")
            }
            val adapter = getBluetoothAdapter(context)
                ?: throw IllegalStateException("Bluetooth tidak tersedia di perangkat ini.")
            if (!adapter.isEnabled) {
                throw IllegalStateException("Bluetooth masih mati. Aktifkan Bluetooth terlebih dahulu.")
            }
            if (printerAddress.isBlank()) {
                throw IllegalStateException("Pilih perangkat printer terlebih dahulu.")
            }

            val device = adapter.getRemoteDevice(printerAddress)
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }

            val lineWidth = if (settings.paperSizeMm >= 80) 48 else 32
            val divider = "=".repeat(lineWidth)
            val testReceipt = buildString {
                appendLine(divider)
                appendLine("TES CETAK PRINTER KASIRKU")
                appendLine(settings.storeName)
                appendLine("Printer : $printerName")
                appendLine("MAC     : $printerAddress")
                appendLine("Kertas  : ${settings.paperSizeMm}mm ($lineWidth karakter)")
                appendLine("Waktu   : ${SecurityAndFormatUtils.formatDateTime(System.currentTimeMillis())}")
                appendLine(divider)
                appendLine("Printer siap digunakan!")
                appendLine()
            }

            var socket: BluetoothSocket? = null
            try {
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
                val out = socket.outputStream
                out.write(byteArrayOf(0x1B, 0x40))
                out.write(testReceipt.toByteArray(Charset.forName("GBK")))
                out.write(byteArrayOf(0x0A, 0x0A, 0x0A))
                out.flush()
                Thread.sleep(300)
                "Tes cetak ke $printerName berhasil!"
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Koneksi tes cetak ke $printerName gagal. Pastikan printer menyala dan sudah dipasangkan (paired)."
                )
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
        }
    }
}
