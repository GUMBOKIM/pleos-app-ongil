package kr.ongil.core

import kotlin.math.roundToInt

/** 코스 짜기(가까운 순)·내비 세그먼트. 순수 함수. 시간 단위는 '그날 0시부터 분' */
object Planner {
    // ---- 이동 모델 ----
    const val SPEED_KMH = 50.0                 // 국도·시내 평균 속도
    const val DETOUR = 1.3                     // 직선거리 → 실제 도로거리 보정(국도)
    private const val HIGHWAY_FROM_KM = 60.0   // 직선 60km 넘는 구간은 고속도로를 탄다고 본다(서울→충주 같은 진입 구간)
    private const val HIGHWAY_SPEED_KMH = 85.0
    private const val HIGHWAY_DETOUR = 1.2
    private const val MIN_PER_DAY = 24 * 60

    private const val END_AT_LAST_SPOT = "마지막 명소에서 끝나요"
    private const val END_AT_LAST_SPOT_GO_HOME = "마지막 명소에서 끝나요 · 집까지는 내비가 안내해요"

    /** 직선거리 60km 넘는 구간은 고속도로 가정(85km/h, 우회 1.2), 그 안은 국도(50km/h, 1.3) */
    private fun legParams(km: Double) = if (km > HIGHWAY_FROM_KM) HIGHWAY_SPEED_KMH to HIGHWAY_DETOUR else SPEED_KMH to DETOUR
    fun travelMin(a: LatLng, b: LatLng): Int { val d = Geo.distanceKm(a, b); val (v, k) = legParams(d); return (d * k / v * 60).roundToInt() }
    fun travelKm(a: LatLng, b: LatLng): Double { val d = Geo.distanceKm(a, b); val (_, k) = legParams(d); return (d * k * 10).roundToInt() / 10.0 }

    /** 지금 자리에서 가장 가까운 곳부터 차례로 */
    private fun greedyOrder(start: LatLng, spots: List<Spot>): List<Spot> {
        val rest = spots.toMutableList(); val out = ArrayList<Spot>(); var cur = start
        while (rest.isNotEmpty()) {
            val next = rest.minByOrNull { Geo.distanceM(cur, it.latLng) }!!
            out += next; rest.remove(next); cur = next.latLng
        }
        return out
    }

    /**
     * 체크리스트 코스(11/7): 사용자가 고른 곳 전부를 출발지에서 가까운 순으로. 시간 제한·날짜 나누기·동반 보정 없음.
     * day는 이어가기 회차(처음 1, 그날 못 간 곳을 다음에 이어가면 2…). 시각은 참고용으로만 계산한다
     */
    fun checklist(catalog: SpotCatalog, spotIds: List<String>, origin: LatLng, startMin: Int, day: Int = 1): DayPlan {
        val spots = spotIds.distinct().mapNotNull { id -> catalog.spots.firstOrNull { it.id == id } }
        return schedule(day, origin, greedyOrder(origin, spots), if (day == 1) END_AT_LAST_SPOT else END_AT_LAST_SPOT_GO_HOME, startMin)
    }

    /** 출발 시각부터 이동·체류를 이어 붙여 도착·출발 시각을 매긴다 */
    private fun schedule(day: Int, start: LatLng, ordered: List<Spot>, endLabel: String, startMin: Int): DayPlan {
        var t = startMin
        var cur = start
        val stops = ordered.map { s ->
            val km = travelKm(cur, s.latLng); t += travelMin(cur, s.latLng)
            val arrive = t; t += s.stayMin
            cur = s.latLng
            Stop(s.id, arrive, t, km)
        }
        return DayPlan(day, stops, endLabel, null, 0.0, startMin)
    }

    /** 내비 세그먼트: 경유지 2 + 목적지 1. 숙소가 있으면 그날 마지막 목적지로 */
    fun segments(day: DayPlan, catalog: SpotCatalog, lodging: Place?): List<List<RoutePoint>> {
        val pts = day.stops.mapNotNull { st -> catalog.spots.firstOrNull { it.id == st.spotId }?.let { RoutePoint(it.name, it.lat, it.lng, it.id) } }.toMutableList()
        if (day.day == 1 && lodging != null) pts += RoutePoint(lodging.name, lodging.lat, lodging.lng, null)
        return pts.chunked(3)
    }

    fun hhmm(min: Int): String { val m = ((min % MIN_PER_DAY) + MIN_PER_DAY) % MIN_PER_DAY; return "%02d:%02d".format(m / 60, m % 60) }
}
