package com.gkhnakbs.multipleqrcodescanner

import android.annotation.SuppressLint
import android.graphics.PointF
import android.hardware.camera2.CaptureRequest
import android.util.Range
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Created by Gökhan Akbaş on 10/10/2025.
 */

@SuppressLint("RestrictedApi")
@OptIn(ExperimentalCamera2Interop::class, ExperimentalGetImage::class)
@Composable
fun CameraPreview(
    onQRCodesDetected: (List<QRCodeResult>) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    val cameraExecutor = remember { Executors.newFixedThreadPool(2) }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    val scanners = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_DATA_MATRIX)
            .build()
        Array(2) { BarcodeScanning.getClient(options) }
    }

    val scannerIndex = remember { AtomicInteger(0) }
    val processingCount = remember { AtomicInteger(0) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            scanners.forEach { it.close() }
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            }

            var scaleFactor = 0f
            var offsetX = 0f
            var offsetY = 0f
            var lastViewWidth = 0
            var lastViewHeight = 0

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }

                val resolutionSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(960, 720),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()

                val imageAnalysisBuilder = ImageAnalysis.Builder()
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)

                Camera2Interop.Extender(imageAnalysisBuilder)
                    .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                        Range(60, 60)
                    )
                    .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                    )
                    .setCaptureRequestOption(
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF
                    )
                    .setCaptureRequestOption(
                        CaptureRequest.NOISE_REDUCTION_MODE,
                        CaptureRequest.NOISE_REDUCTION_MODE_FAST
                    )
                    .setCaptureRequestOption(
                        CaptureRequest.EDGE_MODE,
                        CaptureRequest.EDGE_MODE_FAST
                    )

                val imageAnalysis = imageAnalysisBuilder.build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    if (processingCount.get() >= 2) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    processingCount.incrementAndGet()

                    val mediaImage = imageProxy.image
                    if (mediaImage == null) {
                        processingCount.decrementAndGet()
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees

                    val vw = previewView.width
                    val vh = previewView.height
                    if (vw != lastViewWidth || vh != lastViewHeight) {
                        lastViewWidth = vw
                        lastViewHeight = vh

                        val isRotated = rotationDegrees == 90 || rotationDegrees == 270
                        val sw = (if (isRotated) imageProxy.height else imageProxy.width).toFloat()
                        val sh = (if (isRotated) imageProxy.width else imageProxy.height).toFloat()
                        val tw = vw.toFloat()
                        val th = vh.toFloat()

                        val sf = maxOf(tw / sw, th / sh)
                        scaleFactor = sf
                        offsetX = (tw - sw * sf) * 0.5f
                        offsetY = (th - sh * sf) * 0.5f
                    }

                    val sf = scaleFactor
                    val ox = offsetX
                    val oy = offsetY

                    val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)
                    val scanner =
                        scanners[scannerIndex.getAndIncrement() and 1]

                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            if (barcodes.isEmpty()) {
                                mainExecutor.execute { onQRCodesDetected(emptyList()) }
                                return@addOnSuccessListener
                            }

                            val results = ArrayList<QRCodeResult>(barcodes.size)

                            for (i in barcodes.indices) {
                                val barcode = barcodes[i]
                                val value = barcode.rawValue ?: continue
                                val corners = barcode.cornerPoints ?: continue

                                val transformed = ArrayList<PointF>(4)
                                for (j in corners.indices) {
                                    val p = corners[j]
                                    transformed.add(PointF(p.x * sf + ox, p.y * sf + oy))
                                }
                                results.add(QRCodeResult(value, barcode.format, transformed))
                            }

                            if (results.isNotEmpty()) {
                                mainExecutor.execute { onQRCodesDetected(results) }
                            }
                        }
                        .addOnFailureListener {  }
                        .addOnCompleteListener {
                            processingCount.decrementAndGet()
                            imageProxy.close()
                        }
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (_: Exception) {
                }

            }, mainExecutor)
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}