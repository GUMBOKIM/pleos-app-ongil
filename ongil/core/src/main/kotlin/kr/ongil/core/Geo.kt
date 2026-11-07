package kr.ongil.core

import kotlin.math.*

object Geo {
    private const val R = 6371000.0
    fun distanceM(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
        val dLat = Math.toRadians(bLat - aLat); val dLng = Math.toRadians(bLng - aLng)
        val s = sin(dLat / 2).pow(2) + cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLng / 2).pow(2)
        return 2 * R * asin(sqrt(s))
    }
    fun distanceM(a: LatLng, b: LatLng) = distanceM(a.lat, a.lng, b.lat, b.lng)
    fun distanceKm(a: LatLng, b: LatLng) = distanceM(a, b) / 1000.0
}

/** 시군구 폴리곤을 [0,1]×[0,1/aspect] 정규화 좌표로 투영하고 터치 판정을 한다. 화면 변환은 UI 몫 */
class MapGeometry(val regions: List<Region>) {
    private val cosLat = cos(Math.toRadians(KOREA_MID_LAT))
    val minLat: Double; val maxLat: Double; val minLng: Double; val maxLng: Double
    init {
        var mnLa = 90.0; var mxLa = -90.0; var mnLn = 180.0; var mxLn = -180.0
        regions.forEach { r -> r.rings.forEach { ring -> ring.forEach { p -> val lng = p[0]; val lat = p[1]
            if (lat < mnLa) mnLa = lat; if (lat > mxLa) mxLa = lat; if (lng < mnLn) mnLn = lng; if (lng > mxLn) mxLn = lng } } }
        minLat = mnLa; maxLat = mxLa; minLng = mnLn; maxLng = mxLn
    }
    private val widthDeg = (maxLng - minLng) * cosLat
    private val heightDeg = maxLat - minLat
    /** 정규화 너비 1.0, 높이 = aspect */
    val aspect: Float = (heightDeg / widthDeg).toFloat()

    fun project(lat: Double, lng: Double): Pair<Float, Float> =
        Pair((((lng - minLng) * cosLat) / widthDeg).toFloat(), ((maxLat - lat) / heightDeg * aspect).toFloat())

    class Poly(val code: String, val rings: List<FloatArray>, val minX: Float, val minY: Float, val maxX: Float, val maxY: Float, val cx: Float, val cy: Float)

    val polys: List<Poly> = regions.map { r ->
        var mnX = Float.POSITIVE_INFINITY; var mnY = Float.POSITIVE_INFINITY; var mxX = Float.NEGATIVE_INFINITY; var mxY = Float.NEGATIVE_INFINITY
        var sx = 0.0; var sy = 0.0; var n = 0
        val rings = r.rings.map { ring ->
            val arr = FloatArray(ring.size * 2)
            ring.forEachIndexed { i, p -> val (x, y) = project(p[1], p[0]); arr[i * 2] = x; arr[i * 2 + 1] = y
                if (x < mnX) mnX = x; if (x > mxX) mxX = x; if (y < mnY) mnY = y; if (y > mxY) mxY = y }
            arr
        }
        // 중심은 가장 큰 링(첫 링)의 평균
        rings.firstOrNull()?.let { a -> var i = 0; while (i < a.size) { sx += a[i]; sy += a[i + 1]; n++; i += 2 } }
        Poly(r.code, rings, mnX, mnY, mxX, mxY, if (n > 0) (sx / n).toFloat() else 0f, if (n > 0) (sy / n).toFloat() else 0f)
    }
    val byCode: Map<String, Poly> = polys.associateBy { it.code }
    val regionByCode: Map<String, Region> = regions.associateBy { it.code }

    /** 정규화 좌표로 지역 찾기. 안 맞으면 tol 안의 최근접 중심 */
    fun hitTest(x: Float, y: Float, tol: Float = 0.02f): String? {
        for (p in polys) {
            if (x < p.minX || x > p.maxX || y < p.minY || y > p.maxY) continue
            if (p.rings.any { inRing(x, y, it) }) return p.code
        }
        var best: Poly? = null; var bd = tol * tol
        for (p in polys) { val dx = p.cx - x; val dy = p.cy - y; val d = dx * dx + dy * dy; if (d < bd) { bd = d; best = p } }
        return best?.code
    }
    fun regionAt(lat: Double, lng: Double): String? { val (x, y) = project(lat, lng); return hitTest(x, y, 0.0f) }

    private companion object {
        /** 경도 축을 이 위도의 cos로 줄여 한반도 중부가 찌그러지지 않게 투영 */
        const val KOREA_MID_LAT = 36.3
    }

    private fun inRing(x: Float, y: Float, ring: FloatArray): Boolean {
        var inside = false; val n = ring.size / 2; var j = n - 1
        for (i in 0 until n) {
            val xi = ring[i * 2]; val yi = ring[i * 2 + 1]; val xj = ring[j * 2]; val yj = ring[j * 2 + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi + 1e-9f) + xi) inside = !inside
            j = i
        }
        return inside
    }
}
