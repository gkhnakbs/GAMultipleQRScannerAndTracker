package com.gkhnakbs.multipleqrcodescanner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Created by Gökhan Akbaş on 10/10/2025.
 */

@Composable
fun QRScannerScreen() {
    var detectedQRCodes by remember { mutableStateOf<List<QRCodeResult>>(emptyList()) }

    val uniqueQRCodes = remember { mutableStateOf(emptySet<String>()) }
    val verifiedQRCodes = remember { mutableStateOf(emptySet<String>()) }
    val path = remember { Path() }
    val animatedCorners =
        remember { mutableStateMapOf<String, List<Animatable<Offset, AnimationVector2D>>>() }
    val listState = rememberLazyListState()
    val checkImage = ImageBitmap.imageResource(id = R.drawable.check_sign_icon)


    LaunchedEffect(detectedQRCodes) {
        val detectedValues = detectedQRCodes.map { it.value }

        animatedCorners.keys.removeAll { it !in detectedValues }

        detectedQRCodes.forEach { qr ->
            val targetCorners = qr.corners.map { Offset(it.x, it.y) }
            if (!animatedCorners.containsKey(qr.value)) {
                animatedCorners[qr.value] =
                    targetCorners.map { Animatable(it, Offset.VectorConverter) }
            } else {
                animatedCorners[qr.value]?.forEachIndexed { index, cornerAnimatable ->
                    launch {
                        cornerAnimatable.animateTo(
                            targetValue = targetCorners[index],
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(uniqueQRCodes.value.size) {
        listState.scrollToItem(0)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(animatedCorners) {
                detectTapGestures { tapOffset ->
                    for ((value, cornersAnimatable) in animatedCorners.entries.reversed()) {
                        val currentCorners = cornersAnimatable.map { it.value }
                        if (isPointInPolygon(tapOffset, currentCorners)) {
                            verifiedQRCodes.value = verifiedQRCodes.value + value
                            break
                        }
                    }
                }
            }
    ) {
        CameraPreview(
            onQRCodesDetected = { codesSequence ->
                val codes = codesSequence.toList()
                detectedQRCodes = codes
                var newCodesFound: MutableList<String>? = null
                for (qr in codes) {
                    if (qr.value.isNotBlank() && qr.value !in uniqueQRCodes.value) {
                        if (newCodesFound == null) newCodesFound = mutableListOf()
                        newCodesFound.add(qr.value)
                    }
                }

                if (newCodesFound != null) {
                    uniqueQRCodes.value = uniqueQRCodes.value + newCodesFound
                }
            }
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = 0.99f) // Donanım hızlandırmayı tetikler.
        ) {
            animatedCorners.forEach { (value, animatables) ->
                if (animatables.size != 4) return@forEach
                // Sıfır bellek tahsisi ile Path oluşturma:
                path.apply {
                    rewind()
                    moveTo(animatables[0].value.x, animatables[0].value.y)
                    lineTo(animatables[1].value.x, animatables[1].value.y)
                    lineTo(animatables[2].value.x, animatables[2].value.y)
                    lineTo(animatables[3].value.x, animatables[3].value.y)
                    close()
                }

                drawPath(
                    path = path,
                    color = if (verifiedQRCodes.value.contains(value)) Color(0xFF4CAF50) else Color(
                        0xFFFF0000
                    ),
                    style = Stroke(width = 15f)
                )

                if (verifiedQRCodes.value.contains(value)) {
                    // Sıfır bellek tahsisi ile merkez noktası hesaplama:
                    var sumX = 0f
                    var sumY = 0f
                    for (animatable in animatables) {
                        sumX += animatable.value.x
                        sumY += animatable.value.y
                    }

                    val centerX = sumX / 4
                    val centerY = sumY / 4

                    drawImage(
                        image = checkImage,
                        topLeft = Offset(
                            centerX - (checkImage.width / 2),
                            centerY - (checkImage.height / 2)
                        )
                    )
                }
            }
        }

        // Tespit edilen QR kodlar listesi - Optimize edilmiş
        AnimatedVisibility(
            visible = uniqueQRCodes.value.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(16.dp)
            ) {
                Text(
                    text = "${uniqueQRCodes.value.size} QR Kod Tespit Edildi ${verifiedQRCodes.value.size} QR Kod Doğrulaması Yapıldı",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )

                Button(
                    onClick = {
                        detectedQRCodes = emptyList()
                        uniqueQRCodes.value = emptySet()
                        verifiedQRCodes.value = emptySet()
                    },
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text(text = "Tespitleri Temizle")
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = uniqueQRCodes.value.toList().asReversed(),
                        key = { it }
                    ) { qr ->
                        val text = if (verifiedQRCodes.value.contains(qr)) {
                            "Doğrulandı"
                        } else {
                            "Doğrulanmadı"
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White.copy(alpha = 0.9f)
                            ),
                            onClick = {
                                verifiedQRCodes.value = verifiedQRCodes.value + qr
                            }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = qr,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                Text(
                                    text = text,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bir noktanın, verilen köşe noktalarıyla tanımlanan bir poligonun içinde olup olmadığını kontrol eder.
 * Ray Casting algoritmasını kullanır.
 * @param point Kontrol edilecek nokta (tıklama konumu).
 * @param polygon Poligonun köşe noktaları listesi.
 * @return Nokta poligonun içindeyse true, değilse false döner.
 */
private fun isPointInPolygon(point: Offset, polygon: List<Offset>): Boolean {
    if (polygon.size < 3) return false // Geçerli bir poligon değil.

    var isInside = false
    var i = 0
    var j = polygon.size - 1
    while (i < polygon.size) {
        val pi = polygon[i]
        val pj = polygon[j]

        // Ray-casting algoritmasının temel mantığı:
        // Noktadan sağa doğru çizilen yatay bir ışının poligon kenarlarını
        // tek sayıda kesip kesmediğini kontrol eder.
        if (((pi.y > point.y) != (pj.y > point.y)) &&
            (point.x < (pj.x - pi.x) * (point.y - pi.y) / (pj.y - pi.y) + pi.x)
        ) {
            isInside = !isInside
        }
        j = i++
    }
    return isInside
}