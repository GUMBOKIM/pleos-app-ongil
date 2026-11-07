package kr.ongil.app.gateway

import android.util.Log
import kotlinx.coroutines.flow.StateFlow
import kr.ongil.core.LatLng
import kr.ongil.core.RoutePoint

data class Fix(val lat: Double, val lng: Double, val atMs: Long)

data class VehicleSnapshot(
    val gear: String? = null,       // "P","D","R","N" 또는 null(미수신)
    val vinHash: String? = null,
) {
    val parked: Boolean get() = gear == "P"
}

data class TripEstimate(val etaMin: Int?, val distanceKm: Double?)

interface NavigationGateway {
    val currentLocation: StateFlow<LatLng?>
    fun refreshCurrentLocation()
    suspend fun estimate(dest: LatLng, name: String): TripEstimate?
    /** points: 마지막이 목적지, 앞은 경유지(최대 2). 성공 여부는 콜백 기준 */
    suspend fun registerRoute(points: List<RoutePoint>): Result<String>
    fun openNavigation()
}

interface PositionSource {
    val fix: StateFlow<Fix?>
    fun start()
    fun stop()
    fun restart() { stop(); start() }
    fun addProximity(key: String, lat: Double, lng: Double, radiusM: Float, onEnter: (String) -> Unit)
    fun clearProximity()
}

interface VehicleSource {
    val snapshot: StateFlow<VehicleSnapshot>
    fun start()
    fun stop()
}

interface Narrator {
    val speaking: StateFlow<Boolean>
    fun speak(text: String)
    fun stop()
}

/** 주행 제한(차가 "지금 조작 금지"라고 알려 주는 것). AAOS CarUxRestrictions 구현 */
interface DrivingRestrictions {
    val restricted: StateFlow<Boolean>
    fun start()
    fun stop()
}

/**
 * 외부 SDK는 전부 이 인터페이스들 뒤에만 있다. 구현은 gateway/pleos(Pleos Playground SDK)·gateway/aaos(android.car)이고
 * 그 밖의 코드(store·service·ui·repository)는 Pleos·AAOS 타입을 모른다. 에뮬레이터·테스트에선 가짜 구현으로 바꿔 끼운다.
 */
class Gateways(
    val navigation: NavigationGateway,
    val position: PositionSource,
    val vehicle: VehicleSource,
    val narrator: Narrator,
    val ux: DrivingRestrictions,
)

/** SDK 호출 보호. 실패해도 앱은 살되 조용히 삼키지 않고 로그는 남긴다. 실패면 null */
internal inline fun <T> guard(tag: String, what: String, f: () -> T): T? =
    try { f() } catch (e: Exception) { Log.w(tag, "$what fail", e); null }
