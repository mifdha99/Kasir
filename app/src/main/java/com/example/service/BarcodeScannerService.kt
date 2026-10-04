package com.example.service

import android.graphics.Bitmap
import android.graphics.Color
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.EnumMap

object BarcodeScannerService {

    fun generateRandomBarcode(): String {
        val prefix = "899"
        val body = (100000000L..999999999L).random().toString()
        return prefix + body
    }

    fun generateBarcodeBitmap(
        content: String,
        width: Int = 600,
        height: Int = 180
    ): Bitmap? {
        val clean = content.trim()
        if (clean.isEmpty()) return null
        return try {
            val bitMatrix = MultiFormatWriter().encode(
                clean,
                BarcodeFormat.CODE_128,
                width,
                height
            )
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        } catch (_: Exception) {
            null
        }
    }

    class ZxingBarcodeAnalyzer(
        private val onBarcodeDetected: (String) -> Unit
    ) : ImageAnalysis.Analyzer {

        private val reader = MultiFormatReader().apply {
            val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java)
            hints[DecodeHintType.POSSIBLE_FORMATS] = listOf(
                BarcodeFormat.EAN_13,
                BarcodeFormat.EAN_8,
                BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E,
                BarcodeFormat.CODE_128,
                BarcodeFormat.CODE_39,
                BarcodeFormat.ITF,
                BarcodeFormat.QR_CODE
            )
            hints[DecodeHintType.TRY_HARDER] = true
            setHints(hints)
        }

        @Volatile
        private var lastScannedTime = 0L

        override fun analyze(image: ImageProxy) {
            val now = System.currentTimeMillis()
            if (now - lastScannedTime < 900L) {
                image.close()
                return
            }

            try {
                val plane = image.planes.firstOrNull()
                if (plane == null) {
                    image.close()
                    return
                }
                val buffer = plane.buffer
                val data = ByteArray(buffer.remaining())
                buffer.get(data)

                val width = image.width
                val height = image.height

                // Try normal orientation first
                val source = PlanarYUVLuminanceSource(
                    data,
                    width,
                    height,
                    0,
                    0,
                    width,
                    height,
                    false
                )
                val bitmap = BinaryBitmap(HybridBinarizer(source))
                val result = try {
                    reader.decodeWithState(bitmap)
                } catch (_: Exception) {
                    // Try rotated 90 degrees for portrait 1D barcodes
                    try {
                        val rotatedData = rotateYUV90(data, width, height)
                        val rotatedSource = PlanarYUVLuminanceSource(
                            rotatedData,
                            height,
                            width,
                            0,
                            0,
                            height,
                            width,
                            false
                        )
                        reader.decodeWithState(BinaryBitmap(HybridBinarizer(rotatedSource)))
                    } catch (_: Exception) {
                        null
                    }
                } finally {
                    reader.reset()
                }

                if (result != null && result.text.isNotBlank()) {
                    lastScannedTime = now
                    onBarcodeDetected(result.text.trim())
                }
            } catch (_: Exception) {
            } finally {
                image.close()
            }
        }

        private fun rotateYUV90(data: ByteArray, width: Int, height: Int): ByteArray {
            val rotated = ByteArray(width * height)
            var i = 0
            for (x in 0 until width) {
                for (y in height - 1 downTo 0) {
                    val idx = y * width + x
                    if (idx < data.size && i < rotated.size) {
                        rotated[i] = data[idx]
                        i++
                    }
                }
            }
            return rotated
        }
    }
}
