package kr.ongil.app.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.ongil.app.store.AppStore
import kr.ongil.app.store.Screen
import kr.ongil.app.ui.components.BigButton
import kr.ongil.app.ui.components.ChipRow
import kr.ongil.app.ui.components.FilterChip
import kr.ongil.app.ui.components.Ongil
import kr.ongil.app.ui.components.RailHead
import kr.ongil.app.ui.components.Stage
import kr.ongil.app.ui.components.map.KoreaMap
import kr.ongil.app.ui.components.map.MapDot
import kr.ongil.core.Heat
import kr.ongil.core.Spot

private const val PICKED_LINES = 7   // 무대의 "고른 곳" 쪽지에 보이는 줄 수. 넘치면 "+N곳"

/**
 * M1 입력 = 볼 곳 체크 목록 하나. 언제·누구랑·숙소는 묻지 않는다(11/7 사용자: "어디 갈지 체크만 해 놓고 그걸로 간다").
 * 위 칩은 **필터**(전체 / 지자체 세트 / 가볼만한 곳 / 코스)일 뿐이고 "전부 담기"는 없다(어차피 다 못 간다). 하나씩 누른다.
 * 필터를 바꿔도 고른 곳은 사라지지 않고 무대의 쪽지와 지도 번호로 항상 보인다. 고른 곳은 차가 가까운 순으로 순서를 잡아 내비에 넣는다.
 */
@Composable
fun InputPage(store: AppStore) {
    val d by store.draft.collectAsStateWithLifecycle()
    val saved by store.records.state.collectAsStateWithLifecycle()
    val selected by store.selected.collectAsStateWithLifecycle()
    val region = selected?.let { store.catalog.region(it) }
    val spots = selected?.let { store.catalog.catalog(it) }?.spots ?: emptyList()
    val stamped = remember(saved.stamps, selected) { saved.stampedIn(selected) }
    val spotCounts = remember(saved.stamps) { Heat.spotCounts(saved.stamps) }
    val sets = remember(selected) { selected?.let { store.stamps.setsOf(it) } ?: emptyList() }
    val setIds = remember(sets) { sets.flatMap { it.spotIds }.toSet() }
    val others = remember(spots, setIds) { spots.filter { it.id !in setIds } }
    val courses = remember(selected, saved.sharedCardIds) { selected?.let { store.coursesOf(it, saved) } ?: emptyList() }
    val courseChips = remember(courses) { courses.sortedByDescending { it.official }.take(5) }
    var filter by remember(selected) { mutableStateOf("all") }   // all · set:<id> · others · course:<id>
    val fills = rememberHeat(saved)
    // 지도: 고른 곳은 번호(먹색), 이미 간 곳은 칠해진 점. 인주색은 방문 순간에만 쓴다
    val dots = remember(spots, stamped, d.picked) { spots.map { MapDot(it.lat, it.lng, it.id in stamped, it.name, index = d.picked.indexOf(it.id).takeIf { i -> i >= 0 }?.plus(1)) } }
    val pickedNames = remember(d.picked, spots) { d.picked.mapNotNull { id -> spots.firstOrNull { it.id == id }?.name } }

    val shown: List<Spot> = remember(filter, spots, sets, others, courses) {
        when {
            filter.startsWith("set:") -> sets.firstOrNull { "set:${it.id}" == filter }?.spotIds?.mapNotNull { id -> spots.firstOrNull { it.id == id } } ?: spots
            filter == "others" -> others
            filter.startsWith("course:") -> courses.firstOrNull { "course:${it.id}" == filter }?.spotIds?.mapNotNull { id -> spots.firstOrNull { it.id == id } } ?: spots
            else -> spots
        }
    }

    Stage(stage = {
        KoreaMap(store.catalog.geometry, fills, selected, null, selected, Modifier.fillMaxSize(), dots = dots, zoomFactor = 1.7f, labels = true)
        Column(Modifier.stageTop()) {
            Text(region?.name ?: "", color = Ongil.muted, fontSize = 26.sp)
            Text(if (d.picked.isEmpty()) "볼 곳을 골라요" else "${d.picked.size}곳 골랐어요", color = Ongil.ink, fontSize = 44.sp, lineHeight = 50.sp, fontWeight = FontWeight.ExtraBold)
        }
        PickedSlip(pickedNames, spots.size, stamped.size)
    }) {
        RailHead("볼 곳 고르기", over = region?.name, onBack = { store.go(Screen.Home) }, size = 40)
        Spacer(Modifier.height(16.dp))
        ChipRow {
            if (d.picked.isNotEmpty()) FilterChip("비우기", false) { store.clearPicks() }   // 고른 게 있을 때만
            FilterChip("전체 ${spots.size}", filter == "all") { filter = "all" }
            sets.forEach { set -> FilterChip("${set.title} ${set.spotIds.count { it in stamped }}/${set.spotIds.size}", filter == "set:${set.id}", official = true) { filter = "set:${set.id}" } }
            if (sets.isNotEmpty() && others.isNotEmpty()) FilterChip("가볼만한 곳 ${others.size}", filter == "others") { filter = "others" }
            courseChips.forEach { c -> FilterChip(if (c.official) c.title else "${c.title} · ${c.by}", filter == "course:${c.id}", official = c.official) { filter = "course:${c.id}" } }
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { sp ->
                val on = sp.id in d.picked; val order = d.picked.indexOf(sp.id) + 1
                val visited = spotCounts[sp.id] ?: 0
                Row(Modifier.fillMaxWidth().height(Ongil.buttonSmall.dp).background(if (on) Ongil.paint.copy(alpha = 0.16f) else Ongil.card, RoundedCornerShape(Ongil.rSmall)).clickable { store.togglePick(sp.id) }.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).background(if (on) Ongil.paint else Ongil.card2, CircleShape), contentAlignment = Alignment.Center) { if (on) Text("$order", color = Ongil.onFilled, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(14.dp))
                    Text(sp.name, color = Ongil.ink, fontSize = 26.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(if (visited > 0) "${visited}번 방문 · ${sp.stayMin}분" else "${sp.stayMin}분", color = if (visited > 0) Ongil.done else Ongil.muted, fontSize = 20.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        BigButton(if (d.picked.isEmpty()) "볼 곳을 골라 주세요" else "코스 만들기 · ${d.picked.size}곳", Modifier.fillMaxWidth(), enabled = d.picked.isNotEmpty(), sub = if (d.picked.isEmpty()) "위 목록에서 가고 싶은 곳을 누르세요" else "가까운 곳부터 차가 순서를 잡아요") { store.recommend() }
    }
}

/**
 * 무대 왼쪽 아래 "고른 곳" 쪽지. 지도 번호와 같은 먹색 번호를 실 하나로 꿰어 둔다(순서는 출발할 때 차가 다시 잡는다는 뜻으로 실은 옅게).
 * 필터를 바꿔도 여기엔 항상 보인다. 비어 있으면 안내 한 줄만.
 */
@Composable
private fun BoxScope.PickedSlip(names: List<String>, total: Int, visited: Int) {
    Column(stageBottomStart().width(380.dp).background(Ongil.bg2.copy(alpha = 0.92f), RoundedCornerShape(Ongil.rLarge)).padding(horizontal = 24.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("고른 곳", color = Ongil.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(if (visited > 0) "${total}곳 중 ${visited}곳은 가 봤어요" else "${total}곳 중에서", color = Ongil.muted, fontSize = 22.sp)
        }
        Spacer(Modifier.height(10.dp))
        if (names.isEmpty()) Text("오른쪽에서 누르면 여기에 쌓여요", color = Ongil.muted, fontSize = 22.sp, modifier = Modifier.padding(vertical = 6.dp))
        val shown = names.take(PICKED_LINES)
        shown.forEachIndexed { i, n ->
            Row(Modifier.height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                // 번호 + 다음 줄로 이어지는 실
                Box(Modifier.width(32.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    if (i < shown.size - 1 || names.size > PICKED_LINES) Box(Modifier.width(2.dp).fillMaxHeight().padding(top = 22.dp).background(Ongil.paintSoft))
                    Box(Modifier.size(30.dp).background(Ongil.ink, CircleShape), contentAlignment = Alignment.Center) { Text("${i + 1}", color = Ongil.bg2, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.width(12.dp))
                Text(n, color = Ongil.ink, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (names.size > PICKED_LINES) Row(Modifier.height(36.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) { Text("⋯", color = Ongil.muted, fontSize = 22.sp) }
            Spacer(Modifier.width(12.dp))
            Text("+${names.size - PICKED_LINES}곳", color = Ongil.muted, fontSize = 22.sp)
        }
    }
}
