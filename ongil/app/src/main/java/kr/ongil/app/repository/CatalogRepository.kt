package kr.ongil.app.repository

import android.content.Context
import android.util.Log
import kr.ongil.core.MapGeometry
import kr.ongil.core.OngilJson
import kr.ongil.core.Region
import kr.ongil.core.RegionFile
import kr.ongil.core.Spot
import kr.ongil.core.SpotCatalog
import java.util.concurrent.FutureTask

/**
 * assets의 시군구 폴리곤(sgg-simple.json 247KB) + 지역별 명소(spots-*.json 230개 3.3MB).
 * 파싱은 생성 즉시 뒷 스레드 둘에서 시작하고, 첫 접근 때 남은 만큼만 기다린다. API는 동기 그대로.
 */
class CatalogRepository(ctx: Context) {
    companion object {
        private const val TAG = "ONGIL_CATALOG"
        /** 시연 기본 지역 = 충주. 손으로 고른 유일한 지역(spots-chungju.json) */
        const val DEMO_REGION = "43130"
    }

    private val assets = ctx.assets
    private val geometryTask = background("ongil-regions") { MapGeometry(decode(RegionFile.serializer(), "sgg-simple.json").regions) }
    private val spotsTask = background("ongil-spots") { loadSpots() }

    val geometry: MapGeometry by lazy { geometryTask.get() }
    val regions: List<Region> get() = geometry.regions
    val catalogs: Map<String, SpotCatalog> by lazy { spotsTask.get() }

    private fun <T> background(name: String, f: () -> T) = FutureTask(f).also { Thread(it, name).start() }
    private fun <T> decode(ser: kotlinx.serialization.KSerializer<T>, file: String): T =
        OngilJson.decodeFromString(ser, assets.open(file).bufferedReader().use { it.readText() })

    // assets/spots-*.json 전부 (충주는 손으로 고른 것, 나머지는 TourAPI 자동 샘플). 깨진 파일은 로그 남기고 건너뜀
    private fun loadSpots(): Map<String, SpotCatalog> =
        (assets.list("")?.filter { it.startsWith("spots-") && it.endsWith(".json") } ?: emptyList())
            .mapNotNull { f -> runCatching { decode(SpotCatalog.serializer(), f) }.onFailure { Log.w(TAG, "$f 파싱 실패, 건너뜀", it) }.getOrNull() }
            .associateBy { it.regionId }

    fun region(code: String): Region? = geometry.regionByCode[code]
    fun catalog(code: String): SpotCatalog? = catalogs[code]
    private val spotIndex: Map<String, Spot> by lazy { catalogs.values.flatMap { it.spots }.associateBy { it.id } }   // 1,377곳 선형 탐색 대신
    fun spot(id: String): Spot? = spotIndex[id]
    fun spotsOf(code: String): List<Spot> = catalogs[code]?.spots ?: emptyList()
    val demoRegionId: String get() = if (DEMO_REGION in catalogs) DEMO_REGION else catalogs.keys.first()
}
