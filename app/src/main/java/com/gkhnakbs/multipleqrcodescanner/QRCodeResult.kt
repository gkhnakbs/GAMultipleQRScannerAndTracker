package com.gkhnakbs.multipleqrcodescanner

import android.graphics.PointF

/**
 * Created by Gökhan Akbaş on 10/10/2025.
 */

data class QRCodeResult(
    val value: String,
    val format: Int,
    val corners: List<PointF>
)