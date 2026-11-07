package kr.ongil.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val OngilJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false; isLenient = true; explicitNulls = false; coerceInputValues = true }

@Serializable data class LatLng(val lat: Double, val lng: Double)

/** 시군구 폴리곤. rings = 외곽 링들(섬 포함), 각 링은 [lng, lat] 쌍 */
@Serializable data class Region(val code: String, val name: String, val sido: String, val rings: List<List<List<Double>>>)
@Serializable data class RegionFile(val version: String = "", val count: Int = 0, val regions: List<Region>)

@Serializable data class StampRule(val radiusM: Int = 300, val dwellSec: Int = 180)
@Serializable data class TimeWindow(val from: String = "", val to: String = "")

@Serializable data class Spot(
    val id: String,
    val regionId: String,
    val regionName: String = "",
    val name: String,
    val famous: Boolean = false,
    val stayMin: Int = 30,
    val getOff: Boolean = true,
    val walkM: Int = 0,
    val restroom: Boolean = true,
    val stamp: StampRule = StampRule(),
    val story: String = "",
    val storySource: String = "",
    val chargerNearby: Boolean = false,
    val timeWindow: TimeWindow? = null,
    val source: String = "",
    val tourApiContentId: Long? = null,
    val lat: Double,
    val lng: Double,
    val addr1: String = "",
    val tel: String = "",
    val imageUrl: String = "",
    val overview: String = "",
    val usetime: String = "",
    val restdate: String = "",
    val parking: String = "",
) {
    val latLng get() = LatLng(lat, lng)
    val hint: String get() = buildString {
        append(if (getOff) "주차 후 ${walkM}m" else "차에서 보임")
        append(" · ${stayMin}분")
        if (parking.isNotBlank() && parking != "가능") append(" · 주차 $parking") else if (parking == "가능") append(" · 주차 가능")
        if (!restroom) append(" · 화장실 없음")
    }
}

@Serializable data class SpotCatalog(
    val regionId: String,
    val regionName: String,
    val center: LatLng,
    val provider: String = "public",
    val providerLabel: String = "",
    val generatedAt: String = "",
    val restAnchorSpotId: String? = null,
    val spots: List<Spot>,
)

/** 구 입력(반나절/하루/1박2일). 지금은 저장 상태 호환용으로 Plan에만 남는다 */
enum class Budget { HALF, DAY, TWO_DAYS }

/** 구 여행 기간(에포크 일 + 그날 0시부터 분). 저장 상태 호환용 */
@Serializable data class TripWindow(val startEpochDay: Long, val startMin: Int, val endEpochDay: Long, val endMin: Int)

/** 구 동반 입력. 저장 상태 호환용 */
enum class Companions { SOLO, COUPLE, KIDS, SENIOR, GROUP }

@Serializable data class Place(val name: String, val lat: Double, val lng: Double, val source: String, val sample: Boolean = false)

@Serializable data class Stop(val spotId: String, val arriveMin: Int, val departMin: Int, val travelKm: Double)
@Serializable data class DayPlan(
    val day: Int,
    val stops: List<Stop>,
    val endLabel: String,
    val endArriveMin: Int?,
    val endKm: Double,
    val startMin: Int,
    val note: String = "",
) {
    val spotIds get() = stops.map { it.spotId }
}

@Serializable data class Plan(
    val id: String,
    val regionId: String,
    val budget: Budget,
    val companions: Companions,
    val groupSize: Int = 2,
    val lodging: Place? = null,
    val lodgingUndecided: Boolean = false,
    val origin: LatLng? = null,
    val days: List<DayPlan>,
    val pickedVia: String = "car",
    val createdAt: Long,
    val window: TripWindow? = null,
)

/** 내비 세그먼트 한 점. spotId가 null이면 숙소 같은 비(非)명소 지점 */
@Serializable data class RoutePoint(val name: String, val lat: Double, val lng: Double, val spotId: String? = null)

@Serializable data class Stamp(
    val spotId: String,
    val regionId: String,
    val planId: String? = null,
    val at: Long,
    val lat: Double,
    val lng: Double,
    val dwellSec: Int,
    val vinHash: String? = null,
    val certainty: String = "HIGH",
    val isSimulation: Boolean = true,
    val achievements: List<String> = emptyList(),
)

enum class VisitStage { PENDING, APPROACH_NOTIFIED, IN_RADIUS, DWELLING, STAMPED, PASSED }

@Serializable data class VisitState(
    val spotId: String,
    val stage: VisitStage = VisitStage.PENDING,
    val approachNotified: Boolean = false,
    val arrivedNotified: Boolean = false,
    val storyPlayed: Boolean = false,
    val dwellStartedAt: Long? = null,
    val stampedAt: Long? = null,
    val lastDistanceM: Double? = null,
)

enum class TourStatus { ACTIVE, DAY_DONE, COMPLETED }

@Serializable data class TourSession(
    val planId: String,
    val regionId: String,
    val day: Int,
    val status: TourStatus = TourStatus.ACTIVE,
    val visits: List<VisitState>,
    val segmentIndex: Int = 0,
    val startedAt: Long,
    val socStart: Int? = null,
    val socLast: Int? = null,
    val distanceM: Double = 0.0,
    val lastLat: Double? = null,
    val lastLng: Double? = null,
    val lastFixAt: Long? = null,
    val isSimulation: Boolean = true,
    val lastStampedSpotId: String? = null,
) {
    val currentVisit: VisitState? get() = visits.firstOrNull { it.stage != VisitStage.STAMPED && it.stage != VisitStage.PASSED }
    val done: Boolean get() = visits.all { it.stage == VisitStage.STAMPED || it.stage == VisitStage.PASSED }
}

@Serializable data class DayCard(
    val id: String,
    val planId: String,
    val day: Int,
    val date: String,
    val regionId: String,
    val stampedSpotIds: List<String>,
    val skippedSpotIds: List<String>,
    val distanceKm: Double? = null,
    val durationMin: Int? = null,
    val socStart: Int? = null,
    val socEnd: Int? = null,
    val achievements: List<String> = emptyList(),
    val isSimulation: Boolean = true,
    val regionCompleted: Boolean = false,
    val progressBefore: Int = 0,
    val progressAfter: Int = 0,
    val progressTotal: Int = 0,
    val title: String = "",
)

/** 온길을 켜고 여행하다 지나간 시군구 1회 기록(여행·날짜당 한 번). 지도 히트맵의 재료 */
@Serializable data class RegionVisit(val regionId: String, val at: Long, val planId: String? = null, val day: Int = 1)

/** 공개 코스: 차가 검증한 경로("직접 갔다왔다"). 1차는 서버 없이 assets 샘플 + 내가 올린 것(표시만) */
@Serializable data class Course(
    val id: String,
    val title: String,
    val regionId: String,
    val spotIds: List<String>,
    val by: String = "",
    val date: String = "",
    val distanceKm: Double? = null,
    val cars: Int = 0,              // 이 코스로 간 차 수(샘플)
    val verified: Boolean = true,   // 실제로 다녀온 차의 기록으로 만들어진 코스
    val note: String = "",
    val sample: Boolean = true,
    val official: Boolean = false,  // 지자체가 정한 코스(초록). 나머지는 다른 운전자가 올린 것
)

@Serializable data class SavedState(
    val plan: Plan? = null,
    val session: TourSession? = null,
    val stamps: List<Stamp> = emptyList(),
    val cards: List<DayCard> = emptyList(),
    val lastCompanions: Companions? = null,
    val demoFastDwell: Boolean = false,
    val visits: List<RegionVisit> = emptyList(),
    val sharedCardIds: Set<String> = emptySet(),   // '코스 올리기'를 누른 카드(서버 없이 표시만)
)

/**
 * 지자체가 걸어 둔 스탬프 세트("충주 10경"). 세트 안 명소에 모두 서면 스탬프 하나.
 * official=false 는 지자체 검수 전 기본 세트(그 지역 TourAPI 명소 전부)
 */
@Serializable data class StampSet(
    val id: String,
    val regionId: String,
    val title: String,
    val spotIds: List<String>,
    val by: String = "",
    val note: String = "",
    val official: Boolean = false,
    val motif: String? = null,      // 스탬프 그림 모티프 이름(앱 StampStyle.Motif). 없으면 지역으로 정함
    val image: String? = null,      // 지자체 마스코트·로고 그림(assets 경로). 있으면 풍경 대신 가운데에 넣는다
)

/** 스탬프 세트 진행도 계산. 방문(Stamp)은 명소별로 여러 번 쌓이고, 스탬프는 세트 명소를 전부 한 번 이상 가면 받는다 */
object Stamps {
    fun visitedIn(set: StampSet, stamps: List<Stamp>): Set<String> = stamps.mapNotNullTo(HashSet()) { if (it.spotId in set.spotIds) it.spotId else null }
    fun progress(set: StampSet, stamps: List<Stamp>): Int = visitedIn(set, stamps).size
    fun earned(set: StampSet, stamps: List<Stamp>): Boolean = set.spotIds.isNotEmpty() && progress(set, stamps) >= set.spotIds.size
    /** 세트의 마지막 명소에 처음 선 시각 = 스탬프 받은 시각 */
    fun earnedAt(set: StampSet, stamps: List<Stamp>): Long? {
        if (!earned(set, stamps)) return null
        return set.spotIds.mapNotNull { id -> stamps.filter { it.spotId == id }.minOfOrNull { it.at } }.maxOrNull()
    }
}

object Achievements {
    const val REGION_COMPLETE = "region-complete"   // 구 데이터 호환: 그 지역 기본 세트 스탬프
    const val STAMP_PREFIX = "stamp:"               // "stamp:<setId>" = 그 세트 스탬프를 받은 방문
    fun stampOf(setId: String) = STAMP_PREFIX + setId
    fun isStamp(id: String) = id == REGION_COMPLETE || id.startsWith(STAMP_PREFIX)
}

/** "비내섬이에요" / "중앙탑이에요" / "수안보온천이에요" / "충주나루예요": 끝 글자 받침으로 고른다. 한글이 아니면 "예요" */
fun String.iyeyo(): String {
    val c = trimEnd().lastOrNull() ?: return this
    val hasFinal = c in '가'..'힣' && (c - '가') % 28 != 0
    return this + if (hasFinal) "이에요" else "예요"
}
