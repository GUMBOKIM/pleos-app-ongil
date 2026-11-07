package kr.ongil.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

fun qrBitmap(text: String, px: Int = 320): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, px, px, mapOf(EncodeHintType.MARGIN to 1))
    // setPixel 10만 번 대신 배열 한 번 (컴포지션 안 remember에서 돌아가므로 메인 스레드 몫을 줄인다)
    val pixels = IntArray(px * px) { i -> if (m.get(i % px, i / px)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(pixels, px, px, Bitmap.Config.RGB_565)
}

@Composable
fun QrImage(text: String, sizeDp: Int = 320, modifier: Modifier = Modifier) {
    val bmp = remember(text, sizeDp) { qrBitmap(text, sizeDp) }
    Box(modifier.background(Color.White, RoundedCornerShape(12.dp)).padding(10.dp)) {
        Image(bmp.asImageBitmap(), contentDescription = "QR", modifier = Modifier.size(sizeDp.dp), contentScale = ContentScale.FillBounds)
    }
}
