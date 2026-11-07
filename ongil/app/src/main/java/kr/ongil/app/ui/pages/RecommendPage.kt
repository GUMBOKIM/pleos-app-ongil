package kr.ongil.app.ui.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.ongil.app.gateway.TripEstimate
import kr.ongil.app.store.AppStore
import kr.ongil.app.store.RecommendUi
import kr.ongil.app.ui.components.BigButton
import kr.ongil.app.ui.components.Hairline
import kr.ongil.app.ui.components.Ongil
import kr.ongil.app.ui.components.RailHead
import kr.ongil.app.store.Screen
import kr.ongil.app.ui.components.Stage
import kr.ongil.app.ui.components.map.KoreaMap
import kr.ongil.app.ui.components.map.MapDot
import kr.ongil.core.Plan
import kr.ongil.core.Planner
import kr.ongil.core.Spot
import kr.ongil.core.Stop

/** M2 추천: 왼쪽 지도에 그날 경로, 오른쪽에 순서대로 볼 곳. 누르면 펼쳐지고 뺄 수 있다 */
@Composable
fun RecommendPage(store: AppStore) {
    val rec by store.rec.collectAsStateWithLifecycle()
    val saved by store.records.state.collectAsStateWithLifecycle()
    val expanded by store.expanded.collectAsStateWithLifecycle()
    val plan = saved.plan
    val region = plan?.let { store.catalog.region(it.regionId) }
    val cat = plan?.let { store.catalog.catalog(it.regionId) }
    val stamped = remember(saved.stamps, plan?.regionId) { saved.stampedIn(plan?.regionId) }
    val chosen = remember(rec.days) { rec.days.flatMap { it.spotIds }.toSet() }
    val firstSeg = remember(plan, cat, rec.days) { plan?.let { p -> cat?.let { c -> Planner.segments(rec.days.firstOrNull() ?: p.days.first(), c, p.lodging).firstOrNull() } } ?: emptyList() }
    val fills = rememberHeat(saved)
    // 모든 날을 한 목록에: (일차, 정차, 명소). 지도 번호는 전체 통번호
    val allStops = remember(rec.days, cat) { rec.days.flatMap { dpl -> dpl.stops.mapNotNull { st -> cat?.spots?.firstOrNull { it.id == st.spotId }?.let { Triple(dpl, st, it) } } } }
    val indexOf = remember(allStops) { allStops.mapIndexed { i, (_, _, sp) -> sp.id to i + 1 }.toMap() }
    val dots = remember(allStops, stamped, expanded) { allStops.mapIndexed { i, (_, _, sp) -> MapDot(sp.lat, sp.lng, sp.id in stamped, sp.name, index = i + 1, active = expanded == sp.id) } }
    val route = remember(allStops, plan?.origin) { buildList { plan?.origin?.let { add(it) }; allStops.forEach { (_, _, sp) -> add(sp.latLng) } } }

    Box(Modifier.fillMaxSize()) {
        Stage(stage = {
            KoreaMap(store.catalog.geometry, fills, plan?.regionId, plan?.origin, plan?.regionId, Modifier.fillMaxSize(), dots = dots, zoomFactor = 1.7f, route = route, labels = true)
            RecommendOverlay(region?.name ?: "", plan, chosen.size, rec)
        }) {
            RailHead("볼 곳 ${chosen.size}곳", over = if ((rec.days.firstOrNull()?.day ?: 1) > 1) "지난번에 못 간 곳" else region?.name, onBack = { store.go(Screen.Input) }, size = 36)
            Spacer(Modifier.height(14.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                rec.days.forEach { dpl ->
                    if (dpl.note.isNotBlank()) Text(dpl.note, color = Ongil.done, fontSize = 20.sp, modifier = Modifier.padding(bottom = 6.dp))
                    Hairline()
                    dpl.stops.forEach { st ->
                        val sp = cat?.spots?.firstOrNull { it.id == st.spotId } ?: return@forEach
                        StopRow(store, indexOf[sp.id] ?: 0, st, sp, sp.id in stamped, expanded == sp.id)
                        Hairline()
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(rec.registerResult ?: "내비가 ${firstSeg.size}번까지 먼저 안내해요. ${firstSeg.size}번에 서면 다음 곳이 이어져요.", color = if (rec.registerResult?.startsWith("내비 연결 실패") == true) Ongil.red else Ongil.muted, fontSize = 20.sp, modifier = Modifier.padding(bottom = 10.dp))
            // 버튼 둘: 다시 짜기(입력으로, 고른 건 그대로) / 이 코스로 출발
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton("다시 짜기", Modifier.width(220.dp), primary = false) { store.editCourse() }
            if (rec.registerResult?.startsWith("내비에 들어갔어요") == true) BigButton("내비로 가기", Modifier.weight(1f)) { store.openNavi() }
            else BigButton(if (rec.registering) "내비에 넣는 중" else "이 코스로 출발", Modifier.weight(1f), sub = if (rec.registering) null else "내비가 길을 안내해요", enabled = !rec.registering && chosen.isNotEmpty()) { store.registerAndGo() }
            }
        }
    }
}

/** 무대 위: 지역·기간·동반·개수 (왼쪽 위), 첫 목적지까지 거리·시간 (왼쪽 아래). 배터리·충전은 차가 알아서 하므로 안 보여 준다 */
@Composable
private fun BoxScope.RecommendOverlay(regionName: String, plan: Plan?, count: Int, rec: RecommendUi) {
    Column(Modifier.stageTop()) {
        Text(regionName, color = Ongil.ink, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
        // 총 걸리는 시간(운전 + 머무는 시간). 3차 평가: "몇 시에 끝나는지 모르겠다"
        val total = (rec.days.firstOrNull() ?: plan?.days?.firstOrNull())?.let { d -> d.stops.lastOrNull()?.let { it.departMin - d.startMin } }
        val totalText = total?.takeIf { it > 0 }?.let { m -> " · 모두 ${if (m >= 60) "${m / 60}시간 " else ""}${if (m % 60 > 0) "${m % 60}분" else ""}쯤".replace("시간 쯤", "시간쯤") } ?: ""
        Text("${count}곳$totalText · 가까운 곳부터", color = Ongil.muted, fontSize = 24.sp)
    }
    // 아래: 거리·시간(내비 실측)
    Column(stageBottomStart()) {
        val e = rec.estimate
        if (e != null) EstimateBlock(e)
        else Text(rec.estimateState.ifBlank { "출발하면 내비가 거리와 시간을 알려줘요" }, color = Ongil.muted, fontSize = 22.sp)
    }
}

@Composable
private fun EstimateBlock(e: TripEstimate) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(e.distanceKm?.let { "%.0f".format(it) } ?: "—", color = Ongil.ink, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
        Text("km", color = Ongil.muted, fontSize = 22.sp, modifier = Modifier.padding(bottom = 6.dp, end = 18.dp))
        Text(e.etaMin?.toString() ?: "—", color = Ongil.ink, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
        Text("분 · 첫 목적지까지", color = Ongil.muted, fontSize = 22.sp, modifier = Modifier.padding(bottom = 6.dp))
    }
}

/** 볼 곳 한 줄: 번호 · 이름 · 도착/머물기/걷기 · 빼기. 누르면 이야기가 펼쳐진다 */
@Composable
private fun StopRow(store: AppStore, idx: Int, st: Stop, sp: Spot, stamped: Boolean, open: Boolean) {
    Column(Modifier.fillMaxWidth().animateContentSize(tween(220)).padding(vertical = 14.dp)) {
        Row(Modifier.clickable { store.toggleExpanded(sp.id) }, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(if (stamped) Ongil.paint else if (open) Ongil.stamp else Ongil.card2, CircleShape), contentAlignment = Alignment.Center) { Text("$idx", color = if (stamped || open) Ongil.bg2 else Ongil.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(sp.name, color = Ongil.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("${st.departMin - st.arriveMin}분 머물기 · " + (if (sp.getOff) "주차 후 ${sp.walkM}m 걸어요" else "차에서 보여요"), color = Ongil.muted, fontSize = 22.sp)
            }
            Box(Modifier.size(width = 84.dp, height = 72.dp).background(Ongil.basic, RoundedCornerShape(20.dp)).clickable { store.removeSpot(sp.id) }, contentAlignment = Alignment.Center) { Text("빼기", color = Ongil.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        }
        AnimatedVisibility(open) {
            Text(sp.story, color = Ongil.ink2, fontSize = 24.sp, lineHeight = 34.sp, modifier = Modifier.padding(start = 54.dp, top = 10.dp))
        }
    }
}

