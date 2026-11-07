package kr.ongil.core

import kotlin.math.ln

/**
 * 지도 색 = 그 시군구에서 방문한(차가 선) 명소 수. 지나가기만 한 건 안 센다(11/6 사용자: "지난 건 상관없음, 어디어디 방문했는지").
 * 한 곳 = 0.3, 세 곳쯤 = 0.6, 일곱 곳 이상 = 1.0 (로그 스케일)
 */
object Heat {
    private const val FLOOR = 0.3f
    private const val SATURATE_AT = 7.0

    fun level(score: Double): Float = if (score <= 0.0) 0f else (FLOOR + (1f - FLOOR) * (ln(score) / ln(SATURATE_AT)).toFloat()).coerceIn(FLOOR, 1f)

    /** 지역 코드 → 0..1. 방문한 명소가 없는 지역은 빠짐 */
    fun regionLevels(stamps: List<Stamp>): Map<String, Float> =
        stamps.groupBy { it.regionId }.mapValues { (_, ss) -> level(ss.map { it.spotId }.toSet().size.toDouble()) }

    /** 명소 id → 선 횟수(같은 곳을 다른 여행에서 또 서면 2) */
    fun spotCounts(stamps: List<Stamp>): Map<String, Int> = stamps.groupingBy { it.spotId }.eachCount()
}
