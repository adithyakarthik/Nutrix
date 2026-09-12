package com.nutrix.app.ui.lookup

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Reads product barcodes from the camera preview, entirely on the device.
 *
 * ML Kit's scanner ships inside the app, so this works offline, costs nothing, and sends no
 * image anywhere. Formats are restricted to the retail symbologies — a narrower set is
 * measurably faster to lock onto than scanning for everything.
 */
class BarcodeAnalyzer(
    private val onBarcode: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()
    private var handled = false

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || handled) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                val value = barcodes.firstOrNull { it.format in RETAIL_FORMATS }?.rawValue
                if (!value.isNullOrBlank() && !handled) {
                    handled = true
                    onBarcode(value)
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    /** Lets the screen scan again after the user dismisses a result. */
    fun reset() {
        handled = false
    }

    private companion object {
        val RETAIL_FORMATS = setOf(
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_ITF,
        )
    }
}
