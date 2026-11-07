package kr.ongil.app.gateway.pleos

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kr.ongil.app.gateway.Fix
import kr.ongil.app.gateway.PositionSource
import kr.ongil.app.gateway.guard
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** FusedLocation 1.1.3 래퍼. 위치 1초 주기 + 근접 알림(ENTER). 에뮬레이터는 `adb emu geo fix`를 그대로 따라감(10/28 실측) */
class PleosPosition(ctx: Context) : PositionSource {
    private companion object { const val TAG = "ONGIL_POS" }
    private val fused = FusedLocation(ctx)
    private val _fix = MutableStateFlow<Fix?>(null)
    override val fix: StateFlow<Fix?> = _fix
    private var started = false
    // onRegistered는 SDK 스레드에서 늦게 오므로 세대 번호로 묶는다. clearProximity 뒤에 등록 완료된 알림은 바로 해제
    private val alertIds = CopyOnWriteArrayList<String>()
    private val generation = AtomicInteger()

    private val cb = object : FusedLocationListener {
        override fun onChanged(l: LocationData?) {
            l ?: return
            _fix.value = Fix(l.latitude, l.longitude, if (l.timestampMillis > 0) l.timestampMillis else System.currentTimeMillis())
        }
        override fun onError(e: Exception) { Log.w(TAG, "fused error", e) }
    }

    init { guard(TAG, "init") { fused.initialize() } }

    override fun start() {
        if (started) return; started = true
        guard(TAG, "start") { fused.getLastKnownLocation(cb); fused.registerFusedLocationCallback(cb) }
    }
    override fun stop() { if (!started) return; started = false; guard(TAG, "stop") { fused.unregisterFusedLocationCallback(cb) } }

    override fun addProximity(key: String, lat: Double, lng: Double, radiusM: Float, onEnter: (String) -> Unit) {
        val gen = generation.get()
        guard(TAG, "addProximity $key") {
            fused.addProximityAlert(ProximityAlertRequest(lat, lng, radiusM, setOf(ProximityTransition.ENTER)), object : ProximityAlertListener {
                override fun onRegistered(id: String) {
                    if (gen == generation.get()) { alertIds += id; Log.i(TAG, "prox registered $key=$id") }
                    else { Log.i(TAG, "prox $key=$id registered after clear, removing"); guard(TAG, "removeProximityAlert") { fused.removeProximityAlert(id) } }
                }
                override fun onTransition(e: ProximityAlertEvent) { if (e.transition == ProximityTransition.ENTER) onEnter(key) }
                override fun onError(e: Exception) { Log.w(TAG, "prox $key error", e) }
            })
        }
    }
    override fun clearProximity() {
        generation.incrementAndGet()
        val ids = alertIds.toList(); alertIds.clear()
        ids.forEach { id -> guard(TAG, "removeProximityAlert") { fused.removeProximityAlert(id) } }
    }
}
