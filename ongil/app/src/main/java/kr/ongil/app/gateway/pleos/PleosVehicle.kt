package kr.ongil.app.gateway.pleos

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kr.ongil.app.gateway.VehicleSnapshot
import kr.ongil.app.gateway.VehicleSource
import kr.ongil.app.gateway.guard
import java.security.MessageDigest

/** Vehicle 2.3.2 래퍼. 기어 구독 + VIN 해시(방문 보증용, 원문은 저장 안 함). 배터리는 보지 않는다(11/6 사용자 결정) */
class PleosVehicle(ctx: Context) : VehicleSource {
    private companion object { const val TAG = "ONGIL_VEH" }
    private val vehicle = Vehicle(ctx)
    private val _snap = MutableStateFlow(VehicleSnapshot())
    override val snapshot: StateFlow<VehicleSnapshot> = _snap
    private var started = false

    private fun gearName(g: VehicleGear?) = when (g) {
        VehicleGear.GEAR_PARK -> "P"; VehicleGear.GEAR_REVERSE -> "R"; VehicleGear.GEAR_NEUTRAL -> "N"
        null, VehicleGear.GEAR_UNKNOWN -> null; else -> "D"
    }

    private val gearL = object : GearSelectionListener {
        override fun onSelected(g: VehicleGear?) { _snap.update { it.copy(gear = gearName(g)) } }
        override fun onFailed(e: Exception) { Log.w(TAG, "gear fail", e) }
    }

    init { guard(TAG, "init") { vehicle.initialize() } }

    override fun start() {
        if (started) return; started = true
        fun safe(what: String, f: () -> Unit) { guard(TAG, what, f) }
        safe("gear get") { vehicle.getGear().getCurrentGearSelectionState({ g -> _snap.update { it.copy(gear = gearName(g)) } }, { e -> Log.w(TAG, "gear get fail $e") }) }
        safe("gear reg") { vehicle.getGear().registerGearSelection(gearL) }
        safe("vin") { vehicle.getCarInfo().getVINInfo({ vin -> if (!vin.isNullOrBlank()) { _snap.update { it.copy(vinHash = sha(vin)) }; Log.i(TAG, "VIN len=${vin.length}") } }, { e -> Log.w(TAG, "vin fail $e") }) }
    }

    override fun stop() {
        if (!started) return; started = false
        // 하나가 던져도 나머지는 해제
        guard(TAG, "gear unreg") { vehicle.getGear().unregisterGearSelection(gearL) }
    }

    private fun sha(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
}
