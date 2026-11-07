package kr.ongil.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 방문 판정: 반경 안 + 기어 P + 3분 연속 정차일 때만 기록된다 */
class VisitJudgeTest {
    private val dwell = 180_000L
    private fun obs(d: Double?, parked: Boolean, t: Long, r: Int = 300) = VisitJudge.Obs(d, r, parked, t, dwell)
    private fun run(vararg os: VisitJudge.Obs) = os.fold(VisitState("s")) { v, o -> VisitJudge.step(v, o) }

    @Test fun `반경 안에서 P로 3분 서면 방문`() {
        val v = run(obs(100.0, true, 0), obs(100.0, true, 179_000), obs(100.0, true, 180_000))
        assertEquals(VisitStage.STAMPED, v.stage); assertEquals(180_000L, v.stampedAt)
    }

    @Test fun `3분 전에 기어를 떼면 타이머가 처음부터`() {
        val v = run(obs(100.0, true, 0), obs(100.0, false, 120_000), obs(100.0, true, 130_000), obs(100.0, true, 250_000))
        assertEquals(VisitStage.DWELLING, v.stage); assertEquals(130_000L, v.dwellStartedAt)
    }

    @Test fun `반경 밖에서는 아무리 서 있어도 기록되지 않는다`() {
        val v = run(obs(500.0, true, 0), obs(500.0, true, 600_000))
        assertEquals(VisitStage.PENDING, v.stage); assertNull(v.stampedAt)
    }

    @Test fun `반경 밖 120m까지는 GPS 흔들림으로 보고 지나침이 아니다`() {
        val v = run(obs(100.0, false, 0), obs(400.0, false, 10_000))
        assertEquals(VisitStage.IN_RADIUS, v.stage)
        val far = VisitJudge.step(v, obs(430.0, false, 20_000))
        assertEquals(VisitStage.PASSED, far.stage)
    }

    @Test fun `지나친 곳도 다시 들어와 서면 기록된다`() {
        val v = run(obs(100.0, false, 0), obs(500.0, false, 10_000), obs(50.0, true, 20_000), obs(50.0, true, 200_000))
        assertEquals(VisitStage.STAMPED, v.stage)
    }

    @Test fun `명소마다 반경이 다르다`() {
        val v = run(obs(350.0, true, 0, r = 400), obs(350.0, true, 180_000, r = 400))
        assertEquals(VisitStage.STAMPED, v.stage)
    }
}
