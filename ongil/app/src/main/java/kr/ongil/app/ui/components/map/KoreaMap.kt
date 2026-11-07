package kr.ongil.app.ui.components.map

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kr.ongil.app.ui.components.Ongil
import kr.ongil.core.LatLng
import kr.ongil.core.MapGeometry

/** 지도 위 점: 명소. index가 있으면 번호, active면 크게. heat = 선 횟수에 따른 진하기(0..1, stamped일 때) */
data class MapDot(val lat: Double, val lng: Double, val stamped: Boolean, val label: String = "", val index: Int? = null, val active: Boolean = false, val heat: Float = 1f)

// 그릴 때마다 만들지 않도록 색·점선은 한 번만
private val focusFill get() = Ongil.ink.copy(alpha = 0.08f)
private val focusLine get() = Ongil.ink.copy(alpha = 0.45f)
private val selectedFill get() = Ongil.ink.copy(alpha = 0.06f)
private val selectedLine get() = Ongil.ink.copy(alpha = 0.85f)
private val routeLine get() = Ongil.ink.copy(alpha = 0.55f)
private val dotShadow = Color.Black.copy(alpha = 0.35f)
private val currentHalo = Ongil.blue.copy(alpha = 0.25f)
private val routeDash = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))
private val numStyle get() = TextStyle(fontFamily = Ongil.asta, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ongil.bg2)
private val labelHalo get() = Ongil.bg.copy(alpha = 0.72f)
/** 라벨 사각형(화면 좌표, 4px 여유)에 테두리 점이 들어오면 테두리를 가로지르는 것으로 본다 */
private fun crossesEdge(rc: Rect, pts: FloatArray, sx: (Float) -> Float, sy: (Float) -> Float): Boolean {
    val l = rc.left - 4f; val t = rc.top - 4f; val rr = rc.right + 4f; val b = rc.bottom + 4f
    var i = 0
    while (i < pts.size) { val x = sx(pts[i]); val y = sy(pts[i + 1]); if (x in l..rr && y in t..b) return true; i += 2 }
    return false
}
private val labelStyle get() = TextStyle(fontFamily = Ongil.asta, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Ongil.ink)

/** 히트맵 색: 0 = 안 간 땅, 0.3(한 번) = 옅은 칠, 1.0(자주) = 진한 칠 */
private fun heatColor(level: Float): Color = if (level <= 0f) Ongil.none else lerp(Ongil.paintSoft, Ongil.paint, ((level - 0.3f) / 0.7f).coerceIn(0f, 1f))
private val doneLine get() = Ongil.paint

/** 점 하나를 그리기 위해 카메라와 무관하게 미리 계산해 둔 것: 정규화 좌표·반지름·글자 레이아웃 */
private class DotPrep(val dot: MapDot, val nx: Float, val ny: Float, val r: Float, val num: TextLayoutResult?, val label: TextLayoutResult?)

/**
 * 230개 시군구 폴리곤을 Canvas에 직접 그린다. 외부 지도 SDK 없음.
 * 땅은 정규화 좌표를 scale·translate 한 번으로, 점·경로·글자는 화면 좌표로 그려 크기가 줌에 안 흔들린다.
 * 카메라(줌·중심)만 매 프레임 바뀌므로, 카메라에 안 걸리는 계산(Path·합집합·투영·글자 측정)은 전부 remember.
 */
@Composable
fun KoreaMap(
    geo: MapGeometry,
    heat: Map<String, Float>,   // 시군구 코드 → 0..1 방문 진하기(없으면 안 간 땅)
    selected: String?,
    current: LatLng?,
    zoomTo: String?,            // null = 전국, 코드 = 그 지역 주변 확대
    modifier: Modifier = Modifier,
    dots: List<MapDot> = emptyList(),
    zoomFactor: Float = 5f,
    route: List<LatLng> = emptyList(),   // 순서대로 잇는 경로(점선)
    pulse: Float = 0f,          // 0..1, active 점 주변 파동
    labels: Boolean = false,    // 점 옆 이름
    onActivePos: ((Offset) -> Unit)? = null,   // active 점의 화면 좌표(스탬프 위치용)
    focusCodes: Set<String> = emptySet(),      // 비어 있지 않으면 이 묶음(시·도)을 화면에 맞추고 강조
    done: Set<String> = emptySet(),            // 완주한 지역: 칠 색 테두리
    onTap: (String?) -> Unit = {},
) {
    val paths = remember(geo) {
        geo.polys.associate { p -> p.code to p.rings.map { r -> Path().apply { var i = 0; while (i < r.size) { if (i == 0) moveTo(r[0], r[1]) else lineTo(r[i], r[i + 1]); i += 2 }; close() } } }
    }
    // 선택 지역(또는 묶음) 테두리 점들: 라벨이 테두리를 가로지르지 않게 자리 고를 때 쓴다(정규화 좌표)
    val edgePts: FloatArray = remember(geo, selected, focusCodes) {
        val codes = if (selected != null) setOf(selected) else focusCodes
        val out = ArrayList<Float>()
        for (p in geo.polys) if (p.code in codes) for (r in p.rings) { var i = 0; while (i < r.size) { out += r[i]; out += r[i + 1]; i += 2 } }
        out.toFloatArray()
    }
    // 시·도 묶음의 바깥 테두리: 시군구 폴리곤 합집합 (안쪽 경계는 사라지고 외곽만 남음)
    val focusOutline: Path? = remember(focusCodes, paths) {
        if (focusCodes.isEmpty()) null else {
            var acc: Path? = null
            for (code in focusCodes) paths[code]?.forEach { path ->
                acc = if (acc == null) Path().apply { addPath(path) } else Path().apply { op(acc!!, path, PathOperation.Union) }
            }
            acc
        }
    }
    // 카메라 대상: 시·도 묶음 > 지역 하나 > 전국. [minX, minY, maxX, maxY, 여유 배율]
    val box: FloatArray? = remember(focusCodes, zoomTo, geo, zoomFactor) {
        if (focusCodes.isNotEmpty()) {
            val ps = geo.polys.filter { it.code in focusCodes }
            if (ps.isEmpty()) null else floatArrayOf(ps.minOf { it.minX }, ps.minOf { it.minY }, ps.maxOf { it.maxX }, ps.maxOf { it.maxY }, 1.25f)
        } else zoomTo?.let { geo.byCode[it] }?.let { floatArrayOf(it.minX, it.minY, it.maxX, it.maxY, zoomFactor) }
    }
    val camScale: State<Float> = animateFloatAsState(if (box == null) 1f else run {
        val w = (box[2] - box[0]) * box[4]; val h = (box[3] - box[1]) * box[4]
        (1f / maxOf(w, h / geo.aspect)).coerceIn(1f, 12f)
    }, tween(450), label = "scale")
    val camCx: State<Float> = animateFloatAsState(if (box == null) 0.5f else (box[0] + box[2]) / 2f, tween(450), label = "cx")
    val camCy: State<Float> = animateFloatAsState(if (box == null) geo.aspect / 2f else (box[1] + box[3]) / 2f, tween(450), label = "cy")

    // 카메라와 무관한 준비: 점은 북쪽부터(남쪽 점이 위에 덮이도록), 투영·반지름·글자 측정 한 번
    val measurer = rememberTextMeasurer()
    val preps: List<DotPrep> = remember(dots, geo, labels, measurer) {
        dots.sortedByDescending { it.lat }.map { d ->
            val (nx, ny) = geo.project(d.lat, d.lng)
            val r = if (d.active) 20f else if (d.index != null) 17f else if (d.stamped) 10f + 6f * d.heat else 10f
            DotPrep(d, nx, ny, r, d.index?.let { measurer.measure("$it", numStyle) }, if (labels && d.label.isNotBlank()) measurer.measure(d.label, labelStyle) else null)
        }
    }
    val routeN: List<Pair<Float, Float>> = remember(route, geo) { route.map { geo.project(it.lat, it.lng) } }
    val currentN: Pair<Float, Float>? = remember(current, geo) { current?.let { geo.project(it.lat, it.lng) } }

    // 탭 판정은 geo에만 묶고 카메라 값은 State로 읽는다 (애니메이션 프레임마다 제스처가 다시 시작되지 않게)
    Canvas(modifier.pointerInput(geo) {
        detectTapGestures { pos ->
            val base = minOf(size.width / 1f, size.height / geo.aspect) * 0.9f
            val s = base * camScale.value
            val nx = (pos.x - size.width / 2f) / s + camCx.value
            val ny = (pos.y - size.height / 2f) / s + camCy.value
            onTap(geo.hitTest(nx, ny, 0.02f / camScale.value))
        }
    }) {
        val base = minOf(size.width / 1f, size.height / geo.aspect) * 0.9f
        val s = base * camScale.value
        val tx = size.width / 2f - camCx.value * s
        val ty = size.height / 2f - camCy.value * s
        fun sx(x: Float) = x * s + tx
        fun sy(y: Float) = y * s + ty
        translate(tx, ty) {
            scale(s, s, pivot = Offset.Zero) {
                val border = Stroke(1.2f / s)
                for (p in geo.polys) {
                    val fill = heatColor(heat[p.code] ?: 0f)
                    paths[p.code]?.forEach { path -> drawPath(path, fill); drawPath(path, Ongil.line, style = border) }
                }
                if (done.isNotEmpty()) { val st = Stroke(2.2f / s); for (code in done) paths[code]?.forEach { path -> drawPath(path, doneLine, style = st) } }
                if (focusCodes.isNotEmpty()) { val st = Stroke(1.6f / s); for (code in focusCodes) paths[code]?.forEach { path -> drawPath(path, focusFill); drawPath(path, focusLine, style = st) } }
                focusOutline?.let { drawPath(it, Ongil.paint, style = Stroke(4f / s, join = StrokeJoin.Round)) }
                selected?.let { code -> paths[code]?.let { ps -> val st = Stroke(3f / s); ps.forEach { path -> drawPath(path, selectedFill); drawPath(path, selectedLine, style = st) } } }
            }
        }
        // 경로: 점선
        if (routeN.size >= 2) {
            val p = Path()
            routeN.forEachIndexed { i, (x, y) -> if (i == 0) p.moveTo(sx(x), sy(y)) else p.lineTo(sx(x), sy(y)) }
            drawPath(p, routeLine, style = Stroke(4f, cap = StrokeCap.Round, pathEffect = routeDash))
        }
        // 점 (라벨은 이미 놓인 라벨과 겹치면 왼쪽·아래로 피한다)
        val placed = ArrayList<Rect>(preps.size * 2)
        // 점 자리를 먼저 막아두면 라벨이 남의 점을 덮지 않는다
        for (d in preps) { val x = sx(d.nx); val y = sy(d.ny); placed += Rect(Offset(x - d.r - 4f, y - d.r - 4f), Size(2 * d.r + 8f, 2 * d.r + 8f)) }
        for (d in preps) {
            val x = sx(d.nx); val y = sy(d.ny); val r = d.r; val c = Offset(x, y)
            if (d.dot.active) onActivePos?.invoke(c)
            if (d.dot.active && pulse > 0f) drawCircle(Ongil.stamp.copy(alpha = (1f - pulse) * 0.5f), r + 40f * pulse, c)
            drawCircle(dotShadow, r + 2f, Offset(x, y + 2f))
            drawCircle(if (d.dot.stamped) lerp(Ongil.paintSoft, Ongil.paint, d.dot.heat) else if (d.dot.active) Ongil.stamp else Ongil.ink, r, c)
            if (d.num != null) drawText(d.num, topLeft = Offset(x - d.num.size.width / 2f, y - d.num.size.height / 2f))
            else if (!d.dot.stamped && !d.dot.active) drawCircle(Ongil.card2, r * 0.5f, c)
            val t = d.label ?: continue
            val w = t.size.width.toFloat(); val h = t.size.height.toFloat()
            // 후보 순서: 오른쪽 → 왼쪽 → 아래 → 위 → 네 대각선. 다 겹치면 번호·active만 오른쪽에 그리고 나머지는 생략(빽빽한 곳은 점만)
            var pos: Offset? = null
            val cands = arrayOf(Offset(x + r + 10f, y - h / 2f), Offset(x - r - 10f - w, y - h / 2f), Offset(x - w / 2f, y + r + 6f), Offset(x - w / 2f, y - r - 6f - h),
                                Offset(x + r + 6f, y + r + 2f), Offset(x + r + 6f, y - r - 2f - h), Offset(x - r - 6f - w, y + r + 2f), Offset(x - r - 6f - w, y - r - 2f - h))
            // 1차: 남의 라벨·점과 안 겹치고 지역 테두리도 안 가로지르는 자리. 2차: 겹침만 피한 자리
            for (pass in 0..1) {
                for (cand in cands) {
                    val rc = Rect(cand, Size(w, h))
                    if (placed.none { it.overlaps(rc) } && (pass == 1 || !crossesEdge(rc, edgePts, ::sx, ::sy))) { pos = cand; break }
                }
                if (pos != null) break
            }
            if (pos == null) { if (d.dot.active || d.dot.index != null) pos = Offset(x + r + 10f, y - h / 2f) else continue }
            placed += Rect(pos, Size(w, h))
            // 글자 뒤 옅은 바탕: 테두리·점선이 글자를 가로질러도 읽히게
            drawRoundRect(labelHalo, Offset(pos.x - 6f, pos.y - 2f), Size(w + 12f, h + 4f), CornerRadius(8f, 8f))
            drawText(t, topLeft = pos)
        }
        currentN?.let { (x0, y0) ->
            val c = Offset(sx(x0), sy(y0))
            drawCircle(currentHalo, 26f, c)
            drawCircle(Color.White, 11f, c)
            drawCircle(Ongil.blue, 7f, c)
        }
    }
}
