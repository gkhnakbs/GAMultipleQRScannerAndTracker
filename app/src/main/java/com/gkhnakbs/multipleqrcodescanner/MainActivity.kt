package com.gkhnakbs.multipleqrcodescanner

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.gkhnakbs.multipleqrcodescanner.ui.theme.MultipleQrCodeScannerTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            setContent {
                MultipleQrCodeScannerTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        QRScannerScreen()
                    }
                }
            }
        }
        else {
            // Kullanıcı izin vermezse uygulamayı kapat
            Toast.makeText(this.applicationContext,"Kamera izni verilmediği için uygulamayı kapatıyoruz", Toast.LENGTH_SHORT).show()
            finishAffinity()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
}
