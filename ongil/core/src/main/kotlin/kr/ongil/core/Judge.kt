package kr.ongil.core

/** 스탬프 판정 리듀서. 반경 안 + 기어 P + 연속 dwell. 화면·SDK 의존 없음 */
object VisitJudge {
    const val EXIT_HYSTERESIS_M = 120.0   // 반경 밖 120m까지는 GPS 흔들림으로 보고 PASSED로 넘기지 않음

    data class Obs(val distanceM: Double?, val radiusM: Int, val parked: Boolean, val nowMs: Long, val dwellMs: Long)

    fun step(v: VisitState, o: Obs): VisitState {
        if (v.stage == VisitStage.STAMPED) return v
        val d = o.distanceM ?: return v
        val inside = d <= o.radiusM
        if (v.stage == VisitStage.PASSED && !inside) return v   // 지나친 곳은 다시 반경 안에 들어와야 되살아난다
        val outside = d > o.radiusM + EXIT_HYSTERESIS_M
        val base = v.copy(lastDistanceM = d)
        val dwellStart = base.dwellStartedAt
        return when {
            inside -> when {
                !o.parked -> base.copy(stage = VisitStage.IN_RADIUS, dwellStartedAt = null)   // 움직이면 체류 타이머 리셋
                dwellStart == null -> base.copy(stage = VisitStage.DWELLING, dwellStartedAt = o.nowMs)
                o.nowMs - dwellStart >= o.dwellMs -> base.copy(stage = VisitStage.STAMPED, stampedAt = o.nowMs)
                else -> base.copy(stage = VisitStage.DWELLING)
            }
            outside && (v.stage == VisitStage.IN_RADIUS || v.stage == VisitStage.DWELLING) -> base.copy(stage = VisitStage.PASSED, dwellStartedAt = null)
            else -> base
        }
    }
}
