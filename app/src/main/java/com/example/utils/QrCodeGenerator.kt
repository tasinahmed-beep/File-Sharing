package com.example.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object QrCodeGenerator {
    /**
     * Generates a scannable QR Code bitmap from the given content string.
     */
    fun generateQrBitmap(content: String, width: Int, height: Int): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height)
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Decodes a scannable QR Code text from the given Bitmap using ZXing.
     */
    fun decodeQrFromBitmap(bitmap: Bitmap): String? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val intArray = IntArray(width * height)
            bitmap.getPixels(intArray, 0, width, 0, 0, width, height)
            
            val source = com.google.zxing.RGBLuminanceSource(width, height, intArray)
            val binarizer = com.google.zxing.common.HybridBinarizer(source)
            val binaryBitmap = com.google.zxing.BinaryBitmap(binarizer)
            
            val reader = com.google.zxing.MultiFormatReader()
            val result = reader.decode(binaryBitmap)
            result.text
        } catch (e: Exception) {
            // Decodes fail often if no QR is visible, which is expected during scanning loop
            null
        }
    }
}
