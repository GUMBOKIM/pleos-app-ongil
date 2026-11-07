package kr.ongil.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatTest {
    private fun visit(code: String, plan: String = "p", day: Int = 1) = RegionVisit(code, 0L, plan, day)
    private fun stamp(spot: String, code: String, plan: String = "p") = Stamp(spot, code, plan, 0L, 0.0, 0.0, 180)

    @Test fun `한 번 지나가면 바닥값, 자주 가면 포화`() {
        assertEquals(0f, Heat.level(0.0), 0.0001f)
        assertEquals(0.3f, Heat.level(1.0), 0.001f)
        assertTrue(Heat.level(3.0) > Heat.level(1.0))
        assertEquals(1f, Heat.level(7.0), 0.001f)
        assertEquals(1f, Heat.level(50.0), 0.001f)
    }

    @Test fun `지역 점수는 방문한 명소 수, 같은 명소는 한 번`() {
        val lv = Heat.regionLevels(listOf(stamp("s1", "A"), stamp("s1", "A"), stamp("s3", "A"), stamp("s2", "B")))
        assertEquals(Heat.level(2.0), lv["A"]!!, 0.0001f)
        assertEquals(Heat.level(1.0), lv["B"]!!, 0.0001f)
        assertTrue("C" !in lv)
    }

    @Test fun `같은 명소를 다른 여행에서 또 서면 둘로 센다`() {
        val c = Heat.spotCounts(listOf(stamp("s1", "A", "p"), stamp("s1", "A", "q"), stamp("s2", "A")))
        assertEquals(2, c["s1"]!!.toInt()); assertEquals(1, c["s2"]!!.toInt())
    }
}
