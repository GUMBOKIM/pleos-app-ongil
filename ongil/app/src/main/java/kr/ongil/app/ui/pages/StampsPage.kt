package kr.ongil.app.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kr.ongil.app.ui.components.ChipRow
import kr.ongil.app.ui.components.FilterChip
import kr.ongil.app.ui.components.StampDialog
import kr.ongil.app.ui.components.StampArt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.ongil.app.store.AppStore
import kr.ongil.app.store.Screen
import kr.ongil.app.ui.components.BigButton
import kr.ongil.app.ui.components.DotRow
import kr.ongil.app.ui.components.Hairline
import kr.ongil.app.ui.components.Ongil
import kr.ongil.app.ui.components.RailHead
import kr.ongil.app.ui.components.Stage
import kr.ongil.app.ui.components.Stat
import kr.ongil.app.ui.components.map.KoreaMap
import kr.ongil.app.ui.components.map.MapDot
import kr.ongil.core.Heat
import kr.ongil.core.StampSet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 스탬프: 지자체가 걸어 둔 세트("충주 10경")를 다 돌면 스탬프 하나.
 * 왼쪽 지도는 스탬프 받은 지역 테두리 + 고른 세트의 명소 점. 오른쪽은 상태 칩(전체·받은 스탬프·진행 중·미진행) → 지역 칩 → 세 구역.
 * 진행 중인 스탬프는 방문한 비율만큼 아래부터 인주색이 차오른다(StampArt.fraction).
 * 운전 중엔 점수·랭킹을 띄우지 않는다(심사 가이드). 여기 숫자는 내 기록만.
 */
@Composable
fun StampsPage(store: AppStore) {
    val saved by store.records.state.collectAsStateWithLifecycle()
    val heat = rememberHeat(saved)
    val done = rememberDone(store, saved)
    val allList = remember(saved.stamps) { store.stamps.progressList(saved.stamps) }
    // 범위: 전체 > 시·도 > 시·군·구. "총 몇 개 중 몇 개 받았다"를 범위마다 센다
    var sido by remember { mutableStateOf<String?>(null) }
    var regionId by remember { mutableStateOf<String?>(null) }
    val regionOf = { id: String -> store.catalog.region(id) }
    val sidos = remember(allList) { allList.map { regionOf(it.first.regionId)?.sido ?: "" }.distinct().filter { it.isNotBlank() } }
    val regionsInSido = remember(allList, sido) { allList.map { it.first.regionId }.distinct().filter { regionOf(it)?.sido == sido } }
    val list = remember(allList, sido, regionId) { allList.filter { (regionId == null || it.first.regionId == regionId) && (sido == null || regionOf(it.first.regionId)?.sido == sido) } }
    fun countIn(pred: (StampSet) -> Boolean): Pair<Int, Int> = allList.filter { pred(it.first) }.let { l -> l.count { it.third != null } to l.size }
    val earned = remember(list) { list.filter { it.third != null } }
    val going = remember(list) { list.filter { it.third == null && it.second > 0 } }
    val notYet = remember(list) { list.filter { it.third == null && it.second == 0 } }
    val spotCounts = remember(saved.stamps) { Heat.spotCounts(saved.stamps) }
    var status by remember { mutableStateOf(0) }             // 0 전체 · 1 받은 스탬프 · 2 진행 중 · 3 미진행
    var open by remember { mutableStateOf<String?>(null) }   // 펼친 세트 id
    var dialog by remember { mutableStateOf<String?>(null) } // 크게 보는 세트 id
    val openSet = remember(open, list) { list.firstOrNull { it.first.id == open }?.first }
    val dots = remember(openSet, spotCounts) { openSet?.spotIds?.mapNotNull { id -> store.catalog.spot(id)?.let { MapDot(it.lat, it.lng, (spotCounts[id] ?: 0) > 0, it.name, heat = 0.8f) } } ?: emptyList() }

    dialog?.let { id -> list.firstOrNull { it.first.id == id } }?.let { (set, n, at) ->
        val reg = store.catalog.region(set.regionId)
        StampDialog(set, reg?.sido ?: "", reg?.name ?: "", at != null, at?.let { SimpleDateFormat("yy.MM.dd", Locale.KOREA).format(Date(it)) }, n, set.spotIds.map { (store.catalog.spot(it)?.name ?: it) to (spotCounts[it] ?: 0) }) { dialog = null }
    }
    Stage(stage = {
        KoreaMap(store.catalog.geometry, heat, openSet?.regionId, null, openSet?.regionId, Modifier.fillMaxSize(), dots = dots, zoomFactor = 2.2f, labels = openSet != null, done = done)
        StampsOverlay(earned.size, list.size, openSet, listOfNotNull(sido?.let { shortSido(it) }, regionId?.let { regionOf(it)?.name }).joinToString(" · ").ifBlank { "전국" })
    }) {
        RailHead("스탬프", onBack = { store.go(Screen.Home) })
        Spacer(Modifier.height(12.dp))
        // 1줄: 상태 칩(지금 범위 안에서 센다)
        ChipRow {
            FilterChip("전체 ${list.size}", status == 0) { status = 0 }
            FilterChip("받은 스탬프 ${earned.size}", status == 1) { status = 1 }
            FilterChip("진행 중 ${going.size}", status == 2) { status = 2 }
            FilterChip("미진행 ${notYet.size}", status == 3) { status = 3 }
        }
        Spacer(Modifier.height(8.dp))
        // 2줄: 지역 칩 = 전국 + 시·도(스탬프 세트가 있는 곳만, 받은/총). 시·도를 고르면 3줄째에 시·군·구
        ChipRow {
            val (ea, ta) = countIn { true }
            FilterChip("전국 $ea/$ta", sido == null) { sido = null; regionId = null }
            sidos.forEach { sd -> val (e, t) = countIn { regionOf(it.regionId)?.sido == sd }; FilterChip("${shortSido(sd)} $e/$t", sido == sd) { sido = sd; regionId = null } }
        }
        if (sido != null && regionsInSido.size > 1) {
            Spacer(Modifier.height(8.dp))
            ChipRow {
                val (e0, t0) = countIn { regionOf(it.regionId)?.sido == sido }
                FilterChip("${shortSido(sido!!)} 전체 $e0/$t0", regionId == null) { regionId = null }
                regionsInSido.forEach { rid -> val (e, t) = countIn { it.regionId == rid }; FilterChip("${regionOf(rid)?.name ?: rid} $e/$t", regionId == rid) { regionId = rid } }
            }
        }
        Spacer(Modifier.height(12.dp))
        Hairline()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (status == 0 || status == 1) {
            Text("받은 스탬프 ${earned.size}", color = Ongil.muted, fontSize = 22.sp, modifier = Modifier.padding(top = 16.dp, bottom = 10.dp))
            if (earned.isEmpty()) Text("지자체가 발행한 세트의 명소에 모두 서면 여기에 스탬프가 찍혀요", color = Ongil.muted, fontSize = 24.sp, modifier = Modifier.padding(vertical = 12.dp))
            earned.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (set, _, at) ->
                        val reg = store.catalog.region(set.regionId)
                        Column(Modifier.weight(1f).clickable { open = set.id; dialog = set.id }, horizontalAlignment = Alignment.CenterHorizontally) {
                            StampArt(set, reg?.name ?: "", reg?.sido ?: "", true, size = 156.dp, date = at?.let { SimpleDateFormat("yy.MM.dd", Locale.KOREA).format(Date(it)) })
                            Spacer(Modifier.height(6.dp))
                            Text(set.title, color = Ongil.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(set.by, color = Ongil.official, fontSize = 17.sp)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(6.dp)); Hairline()
            }
            if (status == 0 || status == 2) {
                Text("진행 중 ${going.size}", color = Ongil.muted, fontSize = 22.sp, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                if (going.isEmpty()) Text("가기 시작한 세트가 아직 없어요", color = Ongil.muted, fontSize = 24.sp, modifier = Modifier.padding(vertical = 12.dp))
                going.forEach { (set, n, _) ->
                    val on = set.id == open
                    Column(Modifier.fillMaxWidth().clickable { open = if (on) null else set.id }.padding(vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val reg = store.catalog.region(set.regionId)
                            StampArt(set, reg?.name ?: "", reg?.sido ?: "", false, size = 84.dp, mini = true, tilt = false, fraction = n.toFloat() / set.spotIds.size.coerceAtLeast(1)) { dialog = set.id }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(set.title, color = if (on) Ongil.done else Ongil.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                                Text("${set.spotIds.size}곳 · ${set.by}", color = Ongil.official, fontSize = 19.sp)
                            }
                            Text("$n / ${set.spotIds.size}", color = Ongil.done, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        DotRow(set.spotIds.size, n, dot = 16)
                        if (on) {
                            Spacer(Modifier.height(10.dp))
                            set.spotIds.forEach { id ->
                                val sp = store.catalog.spot(id); val c = spotCounts[id] ?: 0
                                SpotLine(sp?.name ?: id, height = 54, dot = 10, dotColor = if (c > 0) Ongil.paint else Ongil.card2, nameColor = if (c > 0) Ongil.muted else Ongil.ink, right = if (c > 0) "${c}번 방문" else "아직", rightColor = if (c > 0) Ongil.done else Ongil.muted)
                            }
                        }
                    }
                    Hairline()
                }
            }
            if (status == 0 || status == 3) {
                Text("미진행 ${notYet.size} · 지자체가 발행", color = Ongil.muted, fontSize = 22.sp, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                if (notYet.isEmpty()) Text("모든 스탬프를 시작했어요", color = Ongil.muted, fontSize = 24.sp, modifier = Modifier.padding(vertical = 12.dp))
                notYet.forEach { (set, _, _) ->
                    val reg = store.catalog.region(set.regionId)
                    Row(Modifier.fillMaxWidth().clickable { dialog = set.id }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        StampArt(set, reg?.name ?: "", reg?.sido ?: "", false, size = 56.dp, mini = true, tilt = false)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(set.title, color = Ongil.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text("${reg?.name ?: ""} · ${set.spotIds.size}곳 · ${set.by}", color = Ongil.muted, fontSize = 18.sp)
                        }
                        Text("0 / ${set.spotIds.size}", color = Ongil.muted, fontSize = 22.sp)
                    }
                    Hairline()
                }
            }
            Text("스탬프는 지자체가 발행해요. 세트의 명소에 차가 모두 서면 여기에 쌓여요.", color = Ongil.muted, fontSize = 22.sp, lineHeight = 28.sp, modifier = Modifier.padding(top = 14.dp))
        }
        Spacer(Modifier.height(18.dp))
        BigButton(if (openSet != null) "${store.catalog.region(openSet.regionId)?.name ?: ""} 지도로" else "지도로 돌아가기", Modifier.fillMaxWidth(), primary = false) { if (openSet != null) store.pickRegion(openSet.regionId); store.go(Screen.Home) }
    }
}

@Composable
private fun BoxScope.StampsOverlay(earned: Int, total: Int, open: StampSet?, scope: String) {
    Column(Modifier.stageTop()) {
        Text("스탬프 · $scope", color = Ongil.ink, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
        Text(open?.let { "${it.title} · ${it.spotIds.size}곳" } ?: "지자체가 발행한 세트의 명소에 모두 서면 스탬프 하나", color = Ongil.muted, fontSize = 22.sp)
    }
    Row(stageBottomStart(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Stat("$earned", "받은 스탬프", size = 48, color = Ongil.paint)
        Stat("/ $total", "$scope 스탬프", size = 48)
    }
}
