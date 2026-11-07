package kr.ongil.app.gateway.pleos

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.RecentDestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import kr.ongil.app.gateway.NavigationGateway
import kr.ongil.app.gateway.TripEstimate
import kr.ongil.app.gateway.guard
import kr.ongil.core.LatLng
import kr.ongil.core.RoutePoint

/** NaviHelper 3.0.5 래퍼. 요청→콜백을 CompletableDeferred로 이어 suspend API로 만든다 (장소 검색은 SDK에 없음) */
class PleosNavigation(private val ctx: Context) : NavigationGateway {
    private companion object {
        const val TAG = "ONGIL_NAVI"
        const val TRIP_TIMEOUT_MS = 4000L
        const val ROUTE_TIMEOUT_MS = 8000L
        const val NAVI_PKG = "ai.umos.maps.android.navigation.app"
    }

    private val helper = NaviHelper(ctx)
    private val _loc = MutableStateFlow<LatLng?>(null)
    override val currentLocation: StateFlow<LatLng?> = _loc
    @Volatile private var ready = false

    // 콜백이 어느 요청 것인지 SDK가 안 알려주므로 종류별로 마지막 요청 하나만 기다린다
    @Volatile private var tripD: CompletableDeferred<TripInfo>? = null
    @Volatile private var routeD: CompletableDeferred<Result<String>>? = null

    private val listener = object : NaviHelperEventListener {
        override fun onCurrentLocationInfo(info: CurrentLocationInfo) { _loc.value = LatLng(info.latitude, info.longitude) }
        override fun onRecentDestinationInfo(list: List<RecentDestinationInfo>) {}
        override fun onBookmarkInfo(list: List<BookmarkInfo>) {}
        override fun onTripInfo(info: TripInfo) { tripD?.complete(info) }
        override fun onRouteStarted(info: RouteStartInfo) { Log.i(TAG, "route started ${info.name} ${info.distance}m ${info.duration}s"); routeD?.complete(Result.success("${info.name} · ${info.distance / 1000}km · ${info.duration / 60}분")) }
        override fun onRoutingCompleted() { Log.i(TAG, "routing completed"); routeD?.complete(Result.success("경로 계산 완료")) }
        override fun onError(code: NaviErrorCode) { Log.w(TAG, "navi error $code"); routeD?.complete(Result.failure(IllegalStateException("$code"))) }
    }

    init {
        guard(TAG, "NaviHelper init") {
            helper.initialize(
                { ready = true; helper.addListener(listener); helper.getCurrentLocationInfo(); Log.i(TAG, "NaviHelper ready") },
                { e -> Log.w(TAG, "NaviHelper init fail", e) },
            )
        }
    }

    override fun refreshCurrentLocation() { if (ready) guard(TAG, "getCurrentLocationInfo") { helper.getCurrentLocationInfo() } }

    /** 요청을 보내고 콜백을 기다린다. 요청 자체가 던지면 기다리지 않고 null */
    private suspend fun <T> request(what: String, timeoutMs: Long, d: CompletableDeferred<T>, send: () -> Unit): T? {
        guard(TAG, what, send) ?: return null
        return withTimeoutOrNull(timeoutMs) { d.await() }
    }

    override suspend fun estimate(dest: LatLng, name: String): TripEstimate? {
        if (!ready) return null
        val d = CompletableDeferred<TripInfo>().also { tripD = it }
        val t = request("requestTrip", TRIP_TIMEOUT_MS, d) { helper.requestTrip(TripRequest(dest.lat, dest.lng, "", name, "")) } ?: return null
        val etaMin = if (t.eta > 24 * 60) t.eta / 60 else t.eta   // 초로 오면 분으로
        return TripEstimate(etaMin, t.distance / 1000.0)
    }

    override suspend fun registerRoute(points: List<RoutePoint>): Result<String> {
        if (!ready) return Result.failure(IllegalStateException("NaviHelper not ready"))
        if (points.isEmpty()) return Result.failure(IllegalArgumentException("no points"))
        val dest = points.last()
        val wps = points.dropLast(1).take(2).mapIndexed { i, p ->
            RequestWaypointInfo(p.lat, p.lng, p.name, if (i == 0) WaypointIndex.FIRST else WaypointIndex.SECOND, "", "", "", "")
        }
        val route = RouteInfo(dest.lat, dest.lng, null, null, dest.name, "", "", "", RouteOption.RECOMMENDED, "", false)
        val d = CompletableDeferred<Result<String>>().also { routeD = it }
        val sent = runCatching { if (wps.isEmpty()) helper.requestRoute(route) else helper.requestRouteWithWaypoints(RouteWithWaypointsRequest(route, wps)) }
        sent.exceptionOrNull()?.let { Log.w(TAG, "requestRoute fail", it); return Result.failure(it) }
        return withTimeoutOrNull(ROUTE_TIMEOUT_MS) { d.await() } ?: Result.success("요청 보냄 (응답 대기 초과)")
    }

    override fun openNavigation() {
        guard(TAG, "open navi") {
            ctx.startActivity(Intent().setComponent(ComponentName(NAVI_PKG, "$NAVI_PKG.MainActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
