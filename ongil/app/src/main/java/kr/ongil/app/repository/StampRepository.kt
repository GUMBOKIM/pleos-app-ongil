package kr.ongil.app.repository

import kotlinx.coroutines.runBlocking
import kr.ongil.app.api.OngilApi
import kr.ongil.core.Stamp
import kr.ongil.core.StampSet
import kr.ongil.core.Stamps

/**
 * 스탬프 세트 저장소. 스탬프는 지자체가 발행하는 것만 있고(API), 한 지역에 여러 개일 수 있다("충주 10경"과 "충주 호반 3경").
 * 세트가 없는 지역은 방문 기록·히트맵만 남고 스탬프는 없다. 첫 세트의 id는 지역 코드와 같아 구 데이터의 "region-complete"와 이어진다.
 */
class StampRepository(private val api: OngilApi) {
    private val official: List<StampSet> by lazy { runBlocking { api.stampSets() } }

    val all: List<StampSet> get() = official
    fun setsOf(regionId: String): List<StampSet> = official.filter { it.regionId == regionId }
    /** 그 지역의 대표(첫) 세트. 구 데이터 호환·한 줄 표시용 */
    fun setOf(regionId: String): StampSet? = setsOf(regionId).firstOrNull()
    fun set(id: String): StampSet? = official.firstOrNull { it.id == id }
    /** 그 명소가 들어 있는 세트들 */
    fun setsWith(spotId: String): List<StampSet> = official.filter { spotId in it.spotIds }

    fun earnedSets(regionId: String, stamps: List<Stamp>): List<StampSet> = setsOf(regionId).filter { Stamps.earned(it, stamps) }
    /** 스탬프를 하나라도 받은 지역 코드 */
    fun earnedRegions(stamps: List<Stamp>): Set<String> = official.filter { Stamps.earned(it, stamps) }.mapTo(HashSet()) { it.regionId }
    /** 지자체가 발행한 세트 전부를 진행도 순으로: (세트, 방문 수, 받은 시각) */
    fun progressList(stamps: List<Stamp>): List<Triple<StampSet, Int, Long?>> =
        all.map { Triple(it, Stamps.progress(it, stamps), Stamps.earnedAt(it, stamps)) }
            .sortedWith(compareByDescending<Triple<StampSet, Int, Long?>> { it.third != null }.thenByDescending { it.second.toFloat() / it.first.spotIds.size.coerceAtLeast(1) })
}
