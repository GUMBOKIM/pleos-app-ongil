package kr.ongil.app.gateway.aaos

import kr.ongil.app.gateway.DrivingRestrictions
import kr.ongil.app.gateway.guard

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 주행 제한(CarUxRestrictions). 차가 "주의 분산 최적화 필요"라고 하면 화면 조작을 막는다(주행 안전 설계 가이드).
 * Pleos 에뮬레이터에도 car_service가 있어 실값을 받는다. Car 서비스가 없거나 실패하면 false로 둔다.
 */
class AaosUxRestrictions(private val ctx: Context) : DrivingRestrictions {
    companion object { private const val TAG = "ONGIL_UX" }
    private val _restricted = MutableStateFlow(false)
    override val restricted: StateFlow<Boolean> = _restricted
    private var car: Car? = null
    private var mgr: CarUxRestrictionsManager? = null
    private val listener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { r -> apply(r) }

    private fun apply(r: CarUxRestrictions?) {
        _restricted.value = r?.isRequiresDistractionOptimization == true
    }

    override fun start() {
        if (car != null) return
        guard(TAG, "Car.createCar") {
            val c = Car.createCar(ctx) ?: run { Log.w(TAG, "no car service"); return@guard }
            car = c
            val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager ?: run { Log.w(TAG, "no ux manager"); return@guard }
            mgr = m
            apply(m.currentCarUxRestrictions)
            m.registerListener(listener)
            Log.i(TAG, "CarUxRestrictions 구독: DO=${_restricted.value}")
        }
    }

    override fun stop() {
        guard(TAG, "stop") { mgr?.unregisterListener(); car?.disconnect() }
        mgr = null; car = null
    }
}
