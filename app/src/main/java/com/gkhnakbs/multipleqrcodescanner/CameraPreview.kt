package com.gkhnakbs.multipleqrcodescanner

import android.annotation.SuppressLint
import android.graphics.PointF
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.TransformExperimental
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Created by Gökhan Akbaş on 10/10/2025.
 */

@SuppressLint("RestrictedApi")
@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraPreview(
    onQRCodesDetected: (Sequence<QRCodeResult>) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    // Arka plan işlemleri için tek bir thread yeterli ve verimli
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    val scanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_DATA_MATRIX
            )
            .build()
        BarcodeScanning.getClient(options)
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            scanner.close()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                this.scaleType = PreviewView.ScaleType.FILL_CENTER
                this.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            }
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val resolutionSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1280, 720),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->

                    val mediaImage = imageProxy.image ?: run {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            if (barcodes.isNotEmpty()) {
                                val targetWidth = previewView.width.toFloat()
                                val targetHeight = previewView.height.toFloat()

                                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                                val isImageRotated = rotationDegrees == 90 || rotationDegrees == 270
                                val sourceWidth = if (isImageRotated) imageProxy.height.toFloat() else imageProxy.width.toFloat()
                                val sourceHeight = if (isImageRotated) imageProxy.width.toFloat() else imageProxy.height.toFloat()

                                val scaleFactor = maxOf(targetWidth / sourceWidth, targetHeight / sourceHeight)
                                val offsetX = (targetWidth - (sourceWidth * scaleFactor)) / 2f
                                val offsetY = (targetHeight - (sourceHeight * scaleFactor)) / 2f

                                val resultList = ArrayList<QRCodeResult>(barcodes.size)
                                for (barcode in barcodes) {
                                    val value = barcode.rawValue
                                    val corners = barcode.cornerPoints

                                    if (value != null && corners != null) {
                                        // Köşe listesi için de bellek tahsisini optimize et.
                                        val transformedCorners = ArrayList<PointF>(4)
                                        for (point in corners) {
                                            transformedCorners.add(
                                                PointF(
                                                    (point.x * scaleFactor) + offsetX,
                                                    (point.y * scaleFactor) + offsetY
                                                )
                                            )
                                        }
                                        resultList.add(QRCodeResult(value, barcode.format, transformedCorners))
                                    }
                                }

                                if (resultList.isNotEmpty()) {
                                    mainExecutor.execute { onQRCodesDetected(resultList.asSequence()) }
                                }
                            } else {
                                // Boş liste durumunu da ana thread'e gönder.
                                mainExecutor.execute { onQRCodesDetected(emptySequence()) }
                            }
                        }
                        .addOnFailureListener { e ->
                            e.printStackTrace()
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }

            }, mainExecutor)
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}