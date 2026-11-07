package kr.ongil.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PlannerTest {
    private val center = LatLng(36.99, 127.93)
    /** 중심에서 북쪽으로 약 10km 간격 일렬. k=1,2,3… */
    private fun spot(id: String, k: Int) = Spot(id = id, regionId = "r", name = id, stayMin = 30, lat = center.lat + 0.09 * k, lng = center.lng)

    @Test fun checklist_nearestFirst_chainsTravelAndStay() {
        val near = spot("near", 1)
        val cat = SpotCatalog("r", "테스트", center, spots = listOf(spot("far", 3), near, spot("mid", 2)))
        val day = Planner.checklist(cat, listOf("far", "near", "mid", "near"), center, 9 * 60)
        assertEquals(listOf("near", "mid", "far"), day.spotIds)
        assertEquals(9 * 60 + Planner.travelMin(center, near.latLng), day.stops[0].arriveMin)
        assertEquals(day.stops[0].arriveMin + 30, day.stops[0].departMin)
        assertEquals("마지막 명소에서 끝나요", day.endLabel)
        assertEquals("마지막 명소에서 끝나요 · 집까지는 내비가 안내해요", Planner.checklist(cat, listOf("mid"), center, 9 * 60, day = 2).endLabel)
    }

    @Test fun hhmm_wrapsAroundMidnight() {
        assertEquals("09:05", Planner.hhmm(9 * 60 + 5))
        assertEquals("00:30", Planner.hhmm(24 * 60 + 30))
        assertEquals("23:00", Planner.hhmm(-60))
    }
}
