package kr.ongil.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kr.ongil.core.StampSet

/**
 * 지역 스탬프. 인주를 찍은 둥근 스탬프 꼴에 지역 특색 모티프(탑·파도·첨성대·한옥·등대·대나무·폭포…)가 들어간다.
 * 색은 시·도마다 다르고, 아직 못 받은 스탬프는 회색 점선 테두리에 진행도만 보인다.
 */
enum class Motif { PAGODA, WAVE_SUN, PEAKS_SEA, THREE_ROCKS, OBSERVATORY, HANOK, LIGHTHOUSE, BAMBOO, ISLAND_BOAT, VOLCANO, WATERFALL, LAKE, PINE, MOUNTAIN_ROAD, CITY_ROAD }

object StampStyle {
    /** 지역 특색 모티프. 지자체 검수 지역은 명소에 맞춰 고정, 나머지는 시·도와 시/군으로 */
    fun motif(regionName: String, sido: String): Motif = when (regionName) {
        "충주시" -> Motif.PAGODA
        "강릉시", "태안군" -> Motif.WAVE_SUN
        "속초시" -> Motif.PEAKS_SEA
        "단양군" -> Motif.THREE_ROCKS
        "경주시" -> Motif.OBSERVATORY
        "안동시", "전주시" -> Motif.HANOK
        "여수시" -> Motif.LIGHTHOUSE
        "담양군" -> Motif.BAMBOO
        "통영시" -> Motif.ISLAND_BOAT
        "제주시" -> Motif.VOLCANO
        "서귀포시" -> Motif.WATERFALL
        "가평군" -> Motif.LAKE
        else -> when {
            sido.startsWith("제주") -> Motif.VOLCANO
            sido.startsWith("부산") || sido.startsWith("인천") || sido.startsWith("울산") -> Motif.WAVE_SUN
            regionName.endsWith("군") -> Motif.MOUNTAIN_ROAD
            else -> Motif.CITY_ROAD
        }
    }

    /** 인주 색: 시·도마다 하나 */
    fun ink(sido: String): Color = when {
        sido.startsWith("강원") -> Color(0xFF2E8BC0)
        sido.startsWith("경기") -> Color(0xFFD96C3A)
        sido.startsWith("서울") -> Color(0xFFC74B50)
        sido.startsWith("경상북") -> Color(0xFFB5533C)
        sido.startsWith("경상남") -> Color(0xFF2F8F83)
        sido.startsWith("전남") -> Color(0xFF3C8D5A)
        sido.startsWith("전북") -> Color(0xFFB8762F)
        sido.startsWith("충청남") -> Color(0xFFD9A441)
        sido.startsWith("부산") -> Color(0xFF2E6FB5)
        sido.startsWith("인천") -> Color(0xFF3A7CA5)
        sido.startsWith("대구") -> Color(0xFFC2452D)
        sido.startsWith("대전") -> Color(0xFF8A5CA8)
        sido.startsWith("울산") -> Color(0xFF2A7F9E)
        sido.startsWith("제주") -> Color(0xFFE07A3F)
        sido.startsWith("세종") -> Color(0xFF5B7F3B)
        else -> Ongil.stamp   // 충북 등: 온길 인주
    }
}

/**
 * 스탬프 한 장. 일본 디자인 맨홀 뚜껑처럼: 두꺼운 테두리에 둘레 글자, 볼트 무늬 링, 안쪽엔 하늘·먼 산·명소·물결이 꽉 찬 풍경.
 * 색은 시·도 인주색의 3톤(진함·중간·연함) + 크림 하늘. earned=false면 회색에 점선, fraction(0~1)만큼 아래부터 인주색이 차오른다. mini=true면 글자 없이.
 */
@Composable
fun StampArt(set: StampSet, regionName: String, sido: String, earned: Boolean, modifier: Modifier = Modifier, size: Dp = 150.dp, date: String? = null, progress: String? = null, mini: Boolean = false, tilt: Boolean = true, fraction: Float = if (earned) 1f else 0f, onClick: (() -> Unit)? = null) {
    // 진행 중인 스탬프는 아래부터 fraction(0~1)만큼 인주색이 차오른다. 받으면 전부 색, 아직이면 전부 회색
    val fill = if (earned) 1f else fraction.coerceIn(0f, 1f)
    val inkBase = StampStyle.ink(sido); val greyBase = Color(0xFF8A8C92)
    val motif = set.motif?.let { m -> Motif.entries.firstOrNull { it.name == m } } ?: StampStyle.motif(regionName, sido)
    // 지자체 마스코트·로고: assets 그림이 있으면 풍경 대신 가운데 원 안에 넣는다(지자체가 주는 PNG로 교체하는 자리)
    val ctx = LocalContext.current
    val mascot = remember(set.image) { set.image?.let { path -> runCatching { ctx.assets.open(path).use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull() } }
    val rimTop = set.title
    val rimBottom = date ?: progress ?: shortSido(sido)
    Box(
        modifier.size(size).let { if (tilt) it.rotate(-6f) else it }.let { if (onClick != null) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val s = this.size.minDimension; val cx = s / 2; val cy = s / 2; val R = s / 2; val c = Offset(cx, cy)
            fun DrawScope.stamp(base: Color, cream: Color, earned: Boolean) {
                val dark = lerp(base, Color.Black, 0.42f); val light = lerp(base, Color.White, 0.55f)
                // 1) 바깥 테두리(글자 띠) — 받기 전엔 점선
                drawCircle(base, R, c)
                if (!earned) drawCircle(cream, R - s * 0.012f, c, style = Stroke(s * 0.012f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(s * 0.05f, s * 0.035f))))
                // 2) 볼트 링: 작은 사각 36개가 둥글게
                val tickR = if (mini) R * 0.86f else R * 0.78f
                drawCircle(dark, tickR + s * 0.02f, c, style = Stroke(s * 0.012f))
                for (k in 0 until 36) {
                    val a = Math.toRadians(k * 10.0); val px = cx + (tickR * Math.cos(a)).toFloat(); val py = cy + (tickR * Math.sin(a)).toFloat()
                    drawCircle(if (k % 2 == 0) cream else light, s * 0.012f, Offset(px, py))
                }
                // 3) 안쪽 풍경 원
                val r = tickR - s * 0.04f
                drawCircle(cream, r, c)
                clipPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r)) }) {
                    // 하늘 띠 + 해
                    drawRect(light.copy(alpha = 0.35f), Offset(cx - r, cy - r), Size(r * 2, r * 0.55f))
                    drawCircle(base, r * 0.14f, Offset(cx + r * 0.5f, cy - r * 0.5f))
                    // 먼 산(연한 톤) 두 겹
                    val far = Path(); far.moveTo(cx - r, cy + r * 0.05f); far.quadraticBezierTo(cx - r * 0.55f, cy - r * 0.55f, cx - r * 0.15f, cy - r * 0.02f); far.quadraticBezierTo(cx + r * 0.2f, cy - r * 0.5f, cx + r * 0.55f, cy - r * 0.05f); far.quadraticBezierTo(cx + r * 0.8f, cy - r * 0.3f, cx + r, cy + r * 0.05f); far.lineTo(cx + r, cy + r * 0.3f); far.lineTo(cx - r, cy + r * 0.3f); far.close()
                    drawPath(far, light)
                    // 땅/물 띠(진한 톤) + 물결(크림)
                    drawRect(base, Offset(cx - r, cy + r * 0.3f), Size(r * 2, r * 0.7f))
                    for (row in 0 until 3) {
                        val y = cy + r * (0.45f + 0.18f * row); val p = Path(); val seg = r * 0.5f; var x = cx - r - seg * (row % 2) * 0.5f
                        p.moveTo(x, y)
                        while (x < cx + r) { p.quadraticBezierTo(x + seg / 4, y - r * 0.06f, x + seg / 2, y); p.quadraticBezierTo(x + seg * 3 / 4, y + r * 0.06f, x + seg, y); x += seg }
                        drawPath(p, cream.copy(alpha = 0.8f), style = Stroke(s * 0.01f, cap = StrokeCap.Round))
                    }
                    if (mascot != null && s >= 64.dp.toPx()) {   // 26dp 배지처럼 작으면 그림이 안 읽혀 모티프로
                        // 마스코트: 땅 띠 위에 서 있게, 못 받았으면 흐리게
                        val side = (r * 1.25f).toInt(); val left = (cx - side / 2).toInt(); val top = (cy + r * 0.42f - side).toInt()
                        drawImage(mascot, dstOffset = IntOffset(left, top), dstSize = IntSize(side, side), alpha = if (earned) 1f else 0.45f)
                    } else {
                        // 명소 모티프(진한 톤), 땅 띠 위에 올라앉게
                        val mw = r * 1.1f; val mh = r * 0.62f
                        translate(cx - mw / 2, cy + r * 0.32f - mh) { drawMotif(motif, mw, mh, dark) }
                    }
                }
                drawCircle(dark, r, c, style = Stroke(s * 0.014f))
            }
            if (fill < 1f) stamp(greyBase, Color(0xFFD6D7DA), false)
            if (fill > 0f) clipRect(top = this.size.height * (1f - fill)) { stamp(inkBase, Color(0xFFF4EBD6), true) }
            // 4) 둘레 글자: 위 호에 세트 이름, 아래 호에 날짜·진행도
            if (!mini) drawIntoCanvas { cv ->
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = (if (fill > 0f) Color(0xFFF4EBD6) else Color(0xFFD6D7DA)).toArgb(); textSize = s * 0.095f; textAlign = android.graphics.Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD; letterSpacing = 0.06f }
                val tr = R * 0.885f
                val top = android.graphics.Path().apply { addArc(android.graphics.RectF(cx - tr, cy - tr, cx + tr, cy + tr), 180f, 180f) }
                cv.nativeCanvas.drawTextOnPath(rimTop, top, 0f, paint.textSize * 0.36f, paint)
                val br = R * 0.835f
                val bottom = android.graphics.Path().apply { addArc(android.graphics.RectF(cx - br, cy - br, cx + br, cy + br), 180f, -180f) }
                paint.textSize = s * 0.075f
                cv.nativeCanvas.drawTextOnPath(rimBottom, bottom, 0f, 0f, paint)
            }
        }
    }
}

private fun lerp(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)
private fun shortSido(s: String) = s.replace("특별자치도", "").replace("특별자치시", "").replace("특별시", "").replace("광역시", "").replace("통합특별시", "").take(4)

/** 모티프 그리기: (0,0)~(w,h) 상자 안. 전부 단색 선·면 */
private fun DrawScope.drawMotif(m: Motif, w: Float, h: Float, ink: Color) {
    val st = Stroke(w * 0.055f, cap = StrokeCap.Round)
    fun wave(y: Float, amp: Float = h * 0.08f, n: Int = 3) {
        val p = Path(); val seg = w / n; p.moveTo(0f, y)
        for (i in 0 until n) { p.quadraticBezierTo(i * seg + seg / 4, y - amp, i * seg + seg / 2, y); p.quadraticBezierTo(i * seg + seg * 3 / 4, y + amp, (i + 1) * seg, y) }
        drawPath(p, ink, style = st)
    }
    fun peak(x0: Float, x1: Float, top: Float, base: Float) { val p = Path(); p.moveTo(x0, base); p.lineTo((x0 + x1) / 2, top); p.lineTo(x1, base); drawPath(p, ink, style = st) }
    fun road(y0: Float, y1: Float) { val p = Path(); p.moveTo(w * 0.15f, y1); p.cubicTo(w * 0.9f, y1, w * 0.1f, y0 + (y1 - y0) * 0.5f, w * 0.85f, y0); drawPath(p, ink, style = Stroke(w * 0.04f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.09f, w * 0.06f)))) }
    when (m) {
        Motif.PAGODA -> {   // 중앙탑: 기단 + 7층 처마
            drawRect(ink, Offset(w * 0.2f, h * 0.9f), Size(w * 0.6f, h * 0.1f))
            val tiers = 6
            for (i in 0 until tiers) {
                val t = i.toFloat() / tiers; val tw = w * (0.5f - 0.3f * t); val y = h * 0.88f - h * 0.12f * i
                drawRect(ink, Offset(w / 2 - tw / 2, y - h * 0.045f), Size(tw, h * 0.045f))
                drawRect(ink, Offset(w / 2 - tw * 0.3f, y - h * 0.12f), Size(tw * 0.6f, h * 0.075f))
            }
            drawLine(ink, Offset(w / 2, h * 0.14f), Offset(w / 2, 0f), st.width * 0.8f, StrokeCap.Round)
        }
        Motif.WAVE_SUN -> { drawCircle(ink, w * 0.13f, Offset(w * 0.72f, h * 0.28f)); wave(h * 0.65f); wave(h * 0.88f, n = 4) }
        Motif.PEAKS_SEA -> { peak(0f, w * 0.55f, h * 0.05f, h * 0.62f); peak(w * 0.4f, w, h * 0.2f, h * 0.62f); wave(h * 0.85f, n = 4) }
        Motif.THREE_ROCKS -> {   // 도담삼봉: 물 위 봉우리 셋
            peak(w * 0.05f, w * 0.38f, h * 0.3f, h * 0.72f); peak(w * 0.3f, w * 0.72f, h * 0.02f, h * 0.72f); peak(w * 0.62f, w * 0.97f, h * 0.35f, h * 0.72f)
            wave(h * 0.88f, n = 4)
        }
        Motif.OBSERVATORY -> {   // 첨성대: 병 모양 + 창
            val p = Path(); p.moveTo(w * 0.25f, h); p.lineTo(w * 0.3f, h * 0.55f); p.quadraticBezierTo(w * 0.33f, h * 0.25f, w * 0.36f, h * 0.08f); p.lineTo(w * 0.64f, h * 0.08f)
            p.quadraticBezierTo(w * 0.67f, h * 0.25f, w * 0.7f, h * 0.55f); p.lineTo(w * 0.75f, h); p.close(); drawPath(p, ink)
            drawRect(ink, Offset(w * 0.3f, h * 0.02f), Size(w * 0.4f, h * 0.07f)); drawRect(Color.Black.copy(alpha = 0.55f), Offset(w * 0.44f, h * 0.55f), Size(w * 0.12f, h * 0.14f))
        }
        Motif.HANOK -> {   // 한옥 지붕: 처마 곡선 + 용마루 + 기둥
            val p = Path(); p.moveTo(0f, h * 0.5f); p.quadraticBezierTo(w * 0.5f, h * 0.72f, w, h * 0.5f); p.lineTo(w * 0.86f, h * 0.2f); p.quadraticBezierTo(w * 0.5f, h * 0.05f, w * 0.14f, h * 0.2f); p.close(); drawPath(p, ink)
            drawLine(ink, Offset(w * 0.22f, h * 0.65f), Offset(w * 0.22f, h), st.width); drawLine(ink, Offset(w * 0.78f, h * 0.65f), Offset(w * 0.78f, h), st.width); drawLine(ink, Offset(w * 0.1f, h), Offset(w * 0.9f, h), st.width * 0.8f)
        }
        Motif.LIGHTHOUSE -> {
            val p = Path(); p.moveTo(w * 0.36f, h); p.lineTo(w * 0.42f, h * 0.28f); p.lineTo(w * 0.58f, h * 0.28f); p.lineTo(w * 0.64f, h); p.close(); drawPath(p, ink)
            drawRect(ink, Offset(w * 0.38f, h * 0.14f), Size(w * 0.24f, h * 0.14f)); drawCircle(ink, w * 0.06f, Offset(w * 0.5f, h * 0.06f))
            for (dx in listOf(-1f, 1f)) { drawLine(ink, Offset(w * 0.5f + dx * w * 0.17f, h * 0.12f), Offset(w * 0.5f + dx * w * 0.46f, h * 0.02f), st.width * 0.7f, StrokeCap.Round); drawLine(ink, Offset(w * 0.5f + dx * w * 0.17f, h * 0.2f), Offset(w * 0.5f + dx * w * 0.48f, h * 0.22f), st.width * 0.7f, StrokeCap.Round) }
            wave(h * 0.9f, n = 4)
        }
        Motif.BAMBOO -> {
            for ((i, x) in listOf(w * 0.3f, w * 0.52f, w * 0.74f).withIndex()) {
                drawLine(ink, Offset(x, h), Offset(x, h * (0.05f + 0.12f * i)), st.width * 1.4f)
                for (k in 1..3) drawLine(ink, Offset(x - w * 0.05f, h * (0.25f * k + 0.05f * i)), Offset(x + w * 0.05f, h * (0.25f * k + 0.05f * i)), st.width * 0.8f)
                val leaf = Path(); leaf.moveTo(x, h * (0.3f + 0.12f * i)); leaf.quadraticBezierTo(x - w * 0.2f, h * (0.2f + 0.12f * i), x - w * 0.28f, h * (0.3f + 0.12f * i)); leaf.quadraticBezierTo(x - w * 0.15f, h * (0.34f + 0.12f * i), x, h * (0.3f + 0.12f * i)); drawPath(leaf, ink)
            }
        }
        Motif.ISLAND_BOAT -> {
            val isl = Path(); isl.moveTo(w * 0.5f, h * 0.7f); isl.quadraticBezierTo(w * 0.75f, h * 0.2f, w, h * 0.7f); isl.close(); drawPath(isl, ink)
            val hull = Path(); hull.moveTo(w * 0.05f, h * 0.68f); hull.lineTo(w * 0.42f, h * 0.68f); hull.lineTo(w * 0.36f, h * 0.82f); hull.lineTo(w * 0.1f, h * 0.82f); hull.close(); drawPath(hull, ink)
            val sail = Path(); sail.moveTo(w * 0.24f, h * 0.64f); sail.lineTo(w * 0.24f, h * 0.12f); sail.lineTo(w * 0.42f, h * 0.6f); sail.close(); drawPath(sail, ink)
            wave(h * 0.92f, n = 4)
        }
        Motif.VOLCANO -> {   // 한라산: 완만한 산 + 분화구 + 구름
            val p = Path(); p.moveTo(0f, h); p.quadraticBezierTo(w * 0.3f, h * 0.3f, w * 0.42f, h * 0.28f); p.lineTo(w * 0.58f, h * 0.28f); p.quadraticBezierTo(w * 0.7f, h * 0.3f, w, h); p.close(); drawPath(p, ink)
            drawCircle(ink, w * 0.09f, Offset(w * 0.8f, h * 0.14f)); drawLine(ink, Offset(w * 0.05f, h * 0.18f), Offset(w * 0.3f, h * 0.18f), st.width * 0.9f, StrokeCap.Round)
        }
        Motif.WATERFALL -> {   // 정방폭포: 절벽 + 물줄기 + 물보라
            drawRect(ink, Offset(0f, 0f), Size(w * 0.32f, h)); drawRect(ink, Offset(w * 0.32f, 0f), Size(w * 0.68f, h * 0.12f))
            for (x in listOf(0.45f, 0.58f, 0.71f, 0.84f)) drawLine(ink, Offset(w * x, h * 0.12f), Offset(w * x, h * 0.78f), st.width * 0.75f, StrokeCap.Round)
            for (x in listOf(0.4f, 0.52f, 0.64f, 0.76f, 0.9f)) drawCircle(ink, w * 0.028f, Offset(w * x, h * 0.9f))
        }
        Motif.LAKE -> {   // 산 + 호수 + 물결 반사
            peak(w * 0.05f, w * 0.55f, h * 0.05f, h * 0.55f); peak(w * 0.45f, w * 0.95f, h * 0.18f, h * 0.55f)
            drawOval(ink, Offset(w * 0.1f, h * 0.6f), Size(w * 0.8f, h * 0.32f), style = Stroke(st.width))
            drawLine(ink, Offset(w * 0.3f, h * 0.76f), Offset(w * 0.7f, h * 0.76f), st.width * 0.7f, StrokeCap.Round)
        }
        Motif.PINE -> {   // 소나무 셋: 층층 가지 + 굽은 줄기
            for ((i, x) in listOf(w * 0.22f, w * 0.5f, w * 0.78f).withIndex()) {
                val top = h * (0.08f + 0.1f * (i % 2)); val base = h
                drawLine(ink, Offset(x, base), Offset(x + w * 0.03f, top + h * 0.25f), st.width * 1.3f, StrokeCap.Round)
                for (k in 0..2) { val y = top + h * 0.22f * k; val hw = w * (0.08f + 0.05f * k); val p = Path(); p.moveTo(x - hw, y + h * 0.16f); p.lineTo(x, y); p.lineTo(x + hw, y + h * 0.16f); p.close(); drawPath(p, ink) }
            }
        }
        Motif.MOUNTAIN_ROAD -> { peak(0f, w * 0.6f, h * 0.05f, h * 0.5f); peak(w * 0.45f, w, h * 0.22f, h * 0.5f); road(h * 0.55f, h * 0.98f) }
        Motif.CITY_ROAD -> {
            drawRect(ink, Offset(w * 0.08f, h * 0.3f), Size(w * 0.2f, h * 0.3f)); drawRect(ink, Offset(w * 0.36f, h * 0.05f), Size(w * 0.24f, h * 0.55f)); drawRect(ink, Offset(w * 0.68f, h * 0.22f), Size(w * 0.24f, h * 0.38f))
            road(h * 0.62f, h * 0.98f)
        }
    }
}

/** 한 지역의 지자체 스탬프 전부(칩이 넘칠 때 "+N"에서). 누르면 그 스탬프를 크게 */
@Composable
fun StampListDialog(items: List<Triple<StampSet, Int, String?>>, sido: String, regionName: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(640.dp).background(Ongil.bg, RoundedCornerShape(Ongil.rLarge)).padding(32.dp)) {
            Text("$regionName 스탬프 ${items.size}", color = Ongil.ink, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                items.forEach { (set, n, date) ->
                    val earned = n >= set.spotIds.size
                    Row(Modifier.fillMaxWidth().clickable { onPick(set.id) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        StampArt(set, regionName, sido, earned, size = 72.dp, mini = true, tilt = false, fraction = n.toFloat() / set.spotIds.size.coerceAtLeast(1))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(set.title, color = Ongil.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text(set.by, color = Ongil.official, fontSize = 18.sp)
                        }
                        Text(if (earned) (date ?: "받음") else "$n / ${set.spotIds.size}", color = if (earned) Ongil.done else Ongil.muted, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    Hairline()
                }
            }
            Spacer(Modifier.height(16.dp))
            BigButton("닫기", Modifier.fillMaxWidth(), primary = false, height = Ongil.buttonMedium) { onDismiss() }
        }
    }
}

/**
 * 스탬프 자세히 보기: 큰 스탬프 + 세트 설명 + 명소별 방문 여부. 지역 카드의 작은 스탬프 아이콘과 스탬프에서 연다.
 */
@Composable
fun StampDialog(set: StampSet, sido: String, regionName: String, earned: Boolean, date: String?, progress: Int, spotNames: List<Pair<String, Int>>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Row(Modifier.width(900.dp).background(Ongil.bg, RoundedCornerShape(Ongil.rLarge)).padding(36.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StampArt(set, regionName, sido, earned, size = 320.dp, date = date, progress = if (earned) null else "$progress / ${set.spotIds.size}", tilt = false, fraction = progress.toFloat() / set.spotIds.size.coerceAtLeast(1))
                Spacer(Modifier.height(18.dp))
                Text(if (earned) "받은 스탬프" else "${set.spotIds.size - progress}곳 더 가면 받아요", color = if (earned) Ongil.paint else Ongil.muted, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(36.dp))
            Column(Modifier.weight(1f)) {
                Text("$sido $regionName", color = Ongil.muted, fontSize = 22.sp)
                Text(set.title, color = Ongil.ink, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
                Text("${set.by}가 발행한 스탬프", color = Ongil.official, fontSize = 20.sp, modifier = Modifier.padding(top = 2.dp))
                if (set.note.isNotBlank()) Text(set.note, color = Ongil.muted, fontSize = 20.sp, lineHeight = 28.sp, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(14.dp))
                Hairline()
                Column(Modifier.height(260.dp).verticalScroll(rememberScrollState())) {
                    spotNames.forEach { (name, n) ->
                        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).background(if (n > 0) Ongil.paint else Ongil.card2, CircleShape))
                            Spacer(Modifier.width(14.dp))
                            Text(name, color = if (n > 0) Ongil.muted else Ongil.ink, fontSize = 24.sp, modifier = Modifier.weight(1f))
                            Text(if (n > 0) "${n}번 방문" else "아직", color = if (n > 0) Ongil.done else Ongil.muted, fontSize = 20.sp)
                        }
                        Hairline()
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) { BigButton("닫기", Modifier.width(200.dp), primary = false, height = 72) { onDismiss() } }
            }
        }
    }
}
