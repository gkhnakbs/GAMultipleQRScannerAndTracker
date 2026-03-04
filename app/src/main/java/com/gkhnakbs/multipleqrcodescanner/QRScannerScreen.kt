package com.gkhnakbs.multipleqrcodescanner

import android.view.Choreographer
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.dp

/**
 * Created by Gökhan Akbaş on 10/10/2025.
 */

@Composable
fun QRScannerScreen() {
    val currentCorners = remember { mutableMapOf<String, List<Offset>>() }

    val lastSeenTime = remember { mutableMapOf<String, Long>() }

    val retentionNanos = 500_000_000L

    val uniqueQRCodes = remember { mutableStateOf(emptySet<String>()) }
    val verifiedQRCodes = remember { mutableStateOf(emptySet<String>()) }

    val path = remember { Path() }
    val strokeStyle = remember { Stroke(width = 15f) }
    val verifiedColor = remember { Color(0xFF4CAF50) }
    val unverifiedColor = remember { Color(0xFFFF0000) }
    val listState = rememberLazyListState()
    val checkImage = ImageBitmap.imageResource(id = R.drawable.check_sign_icon)

    val checkHalfW = remember(checkImage) { checkImage.width * 0.5f }
    val checkHalfH = remember(checkImage) { checkImage.height * 0.5f }

    val frameTick = remember { mutableLongStateOf(0L) }
    val choreographerCallback = remember {
        object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                frameTick.longValue = frameTimeNanos
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    DisposableEffect(Unit) {
        Choreographer.getInstance().postFrameCallback(choreographerCallback)
        onDispose {
            Choreographer.getInstance().removeFrameCallback(choreographerCallback)
        }
    }

    LaunchedEffect(uniqueQRCodes.value.size) {
        listState.scrollToItem(0)
    }

    val reversedUniqueList = remember(uniqueQRCodes.value) {
        uniqueQRCodes.value.toList().asReversed()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    for ((value, offsets) in currentCorners.entries.reversed()) {
                        if (isPointInPolygon(tapOffset, offsets)) {
                            verifiedQRCodes.value += value
                            break
                        }
                    }
                }
            }
    ) {
        CameraPreview(
            onQRCodesDetected = { codes ->
                val now = System.nanoTime()

                for (i in codes.indices) {
                    val qr = codes[i]
                    val offsets = ArrayList<Offset>(qr.corners.size)
                    for (j in qr.corners.indices) {
                        val c = qr.corners[j]
                        offsets.add(Offset(c.x, c.y))
                    }
                    currentCorners[qr.value] = offsets
                    lastSeenTime[qr.value] = now
                }

                val iterator = lastSeenTime.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (now - entry.value > retentionNanos) {
                        currentCorners.remove(entry.key)
                        iterator.remove()
                    }
                }

                var hasNew = false
                val currentUnique = uniqueQRCodes.value
                for (i in codes.indices) {
                    if (codes[i].value.isNotBlank() && codes[i].value !in currentUnique) {
                        hasNew = true
                        break
                    }
                }

                if (hasNew) {
                    val newSet = LinkedHashSet<String>(currentUnique.size + codes.size)
                    newSet.addAll(currentUnique)
                    for (i in codes.indices) {
                        val v = codes[i].value
                        if (v.isNotBlank()) newSet.add(v)
                    }
                    uniqueQRCodes.value = newSet
                }
            }
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    alpha = 0.99f,
                    compositingStrategy = CompositingStrategy.Offscreen
                )
        ) {
            frameTick.longValue

            val verified = verifiedQRCodes.value

            currentCorners.forEach { (value, offsets) ->
                if (offsets.size != 4) return@forEach

                path.rewind()
                path.moveTo(offsets[0].x, offsets[0].y)
                path.lineTo(offsets[1].x, offsets[1].y)
                path.lineTo(offsets[2].x, offsets[2].y)
                path.lineTo(offsets[3].x, offsets[3].y)
                path.close()

                val isVerified = verified.contains(value)

                drawPath(
                    path = path,
                    color = if (isVerified) verifiedColor else unverifiedColor,
                    style = strokeStyle
                )

                if (isVerified) {
                    drawImage(
                        image = checkImage,
                        topLeft = Offset(
                            (offsets[0].x + offsets[1].x + offsets[2].x + offsets[3].x) * 0.25f - checkHalfW,
                            (offsets[0].y + offsets[1].y + offsets[2].y + offsets[3].y) * 0.25f - checkHalfH
                        )
                    )
                }
            }
        }

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
                        currentCorners.clear()
                        lastSeenTime.clear()
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
                        items = reversedUniqueList,
                        key = { it }
                    ) { qr ->
                        val isVerified = verifiedQRCodes.value.contains(qr)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White.copy(alpha = 0.9f)
                            ),
                            onClick = { verifiedQRCodes.value += qr }
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
                                    text = if (isVerified) "Doğrulandı" else "Doğrulanmadı",
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