package kr.ongil.app.ui.pages

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import kr.ongil.app.ui.components.FilterChip
import kr.ongil.app.ui.components.ChipRow
import kr.ongil.app.ui.components.SpotDialog
import kr.ongil.app.ui.components.StampDialog
import kr.ongil.app.ui.components.StampListDialog
import kr.ongil.app.ui.components.StampArt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.ongil.app.store.AppStore
import kr.ongil.app.ui.components.BigButton
import kr.ongil.app.ui.components.DotRow
import kr.ongil.app.ui.components.Hairline
import kr.ongil.app.ui.components.Ongil
import kr.ongil.app.store.Screen
import kr.ongil.app.ui.components.Stage
import kr.ongil.app.ui.components.map.KoreaMap
import kr.ongil.app.ui.components.map.MapDot
import kr.ongil.core.Heat
import kr.ongil.core.LatLng
import kr.ongil.core.Region
import kr.ongil.core.StampSet
import kr.ongil.core.TourSession
import kr.ongil.core.TourStatus

/** 시·도 목록 순서(지도 위에서 아래, 서쪽에서 동쪽 느낌으로) */
internal val SIDO_ORDER = listOf("서울", "경기", "인천", "강원", "충청북", "충청남", "대전", "세종", "전북", "전남", "경상북", "대구", "경상남", "부산", "울산", "제주")

/** M0 지도 홈: 왼쪽은 전국 지도(지역을 누르면 확대), 오른쪽 레일은 그 지역의 볼 곳과 시작 버튼 */
@Composable
fun HomePage(store: AppStore) {
    val saved by store.records.state.collectAsStateWithLifecycle()
    val selected by store.selected.collectAsStateWithLifecycle()
    val zoom by store.zoom.collectAsStateWithLifecycle()
    val picker by store.picker.collectAsStateWithLifecycle()
    val pickerSido by store.pickerSido.collectAsStateWithLifecycle()
    val navLoc by store.gw.navigation.currentLocation.collectAsStateWithLifecycle()
    val fix by store.gw.position.fix.collectAsStateWithLifecycle()
    val cur = navLoc ?: fix?.let { LatLng(it.lat, it.lng) }

    // 지도 색 = 그 시군구에서 방문한 명소 수. 스탬프 받은 지역은 테두리. 기록이 바뀔 때만 다시 센다
    val stampsByRegion = remember(saved.stamps) { saved.stamps.groupBy { it.regionId }.mapValues { it.value.map { s -> s.spotId }.toSet() } }
    val heat = rememberHeat(saved)
    val done = rememberDone(store, saved)
    val spotCounts = remember(saved.stamps) { Heat.spotCounts(saved.stamps) }
    val coloredCount = heat.size
    val dots = remember(zoom, selected, spotCounts) {
        val sel = selected
        if (!zoom || sel == null) emptyList()
        else store.catalog.spotsOf(sel).map { val n = spotCounts[it.id] ?: 0; MapDot(it.lat, it.lng, n > 0, it.name, heat = (0.5f + (n - 1) * 0.25f).coerceIn(0f, 1f)) }
    }

    Stage(stage = {
        val focus = remember(picker, pickerSido) { if (picker && pickerSido != null) store.catalog.regions.filter { it.sido == pickerSido }.map { it.code }.toSet() else emptySet() }
        KoreaMap(store.catalog.geometry, heat, if (picker) null else selected, cur, if (zoom && !picker) selected else null, Modifier.fillMaxSize(), dots = if (picker) emptyList() else dots, zoomFactor = 2.2f, labels = zoom && !picker, focusCodes = focus, done = done) { if (!picker) store.select(it) }
        HomeOverlay(store, coloredCount, spotCounts.size, zoom, selected != null)
    }) {
        if (picker) { RegionPicker(store, pickerSido, selected, stampsByRegion); return@Stage }
        AnimatedContent(targetState = selected, label = "region", transitionSpec = { (fadeIn(tween(260)) + slideInVertically { 40 }) togetherWith fadeOut(tween(160)) }) { code ->
            val r = code?.let { store.catalog.region(it) }
            if (r == null) Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) { Text("지도에서 지역을 눌러보세요", color = Ongil.muted, fontSize = 28.sp) }
            else RegionCard(store, r, stampsByRegion[r.code] ?: emptySet(), saved.session, spotCounts)
        }
    }
}

/** 무대 위 글자·버튼: 워드마크 + 전국 집계, 상태 칩, 전국 보기 + 범례, 시·군·구 고르기 */
@Composable
private fun BoxScope.HomeOverlay(store: AppStore, coloredCount: Int, spotCount: Int, zoom: Boolean, picked: Boolean) {
    Column(Modifier.stageTop()) {
        Text("온길", color = Ongil.ink, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$coloredCount", color = Ongil.paint, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            Text(" 지역 · ", color = Ongil.muted, fontSize = 22.sp, modifier = Modifier.padding(bottom = 5.dp))
            Text("$spotCount", color = Ongil.paint, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            Text("곳 방문했어요", color = Ongil.muted, fontSize = 22.sp, modifier = Modifier.padding(bottom = 5.dp))
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Pill("내 지도", height = Ongil.buttonSmall, padH = 24, fontSize = 22) { store.go(Screen.MyMap) }
            Pill("스탬프", height = Ongil.buttonSmall, padH = 24, fontSize = 22) { store.go(Screen.Stamps) }
            Pill("공개 코스", height = Ongil.buttonSmall, padH = 24, fontSize = 22) { store.go(Screen.Courses) }
        }
    }
    // 아래: 전국 보기 + 범례
    Row(stageBottomStart(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        AnimatedVisibility(zoom, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 }) {
            Pill("전국 보기") { store.zoomOut() }
        }
        HeatLegend(); Legend(Ongil.none, "스탬프 받음", ring = true)
    }
    // 오른쪽 아래: 지역 고르기 (지도 탭이 어려울 때). 지역을 고르면 레일의 "지역 바꾸기"가 같은 일을 하므로 숨긴다
    if (!picked) Pill("지역 고르기", Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 32.dp)) { store.openPicker(true) }
}

/** 히트맵 범례: 안 간 땅 → 자주 간 땅으로 이어지는 띠 하나 */
@Composable
private fun HeatLegend() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("안 간 곳", color = Ongil.muted, fontSize = 22.sp)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.width(140.dp).height(14.dp).background(Brush.horizontalGradient(listOf(Ongil.none, Ongil.paintSoft, Ongil.paint)), RoundedCornerShape(7.dp)))
        Spacer(Modifier.width(8.dp))
        Text("많이 방문", color = Ongil.muted, fontSize = 22.sp)
    }
}

@Composable
internal fun Legend(c: Color, label: String, ring: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(18.dp).background(c, CircleShape).border(if (ring) 3.dp else 1.dp, if (ring) Ongil.paint else Ongil.faint, CircleShape)); Spacer(Modifier.width(8.dp)); Text(label, color = Ongil.muted, fontSize = 22.sp) }
}

/** 레일: 고른 지역의 이름·진행도·볼 곳 목록·시작/이어가기 버튼 */
@Composable
private fun RegionCard(store: AppStore, r: Region, st: Set<String>, session: TourSession?, spotCounts: Map<String, Int>) {
    val sp = store.catalog.spotsOf(r.code)
    val sets = remember(r.code) { store.stamps.setsOf(r.code) }
    val remote by store.courses.remote.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (remote.isEmpty()) store.refreshCourses() }
    val saved by store.records.state.collectAsStateWithLifecycle()
    val courses = remember(r.code, remote, saved.sharedCardIds) { store.coursesOf(r.code, saved) }
    var showStamp by remember(r.code) { mutableStateOf<String?>(null) }   // 크게 보는 세트 id
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(if (st.isNotEmpty()) "${r.sido} · ${st.size}곳 방문했어요" else r.sido, color = if (st.isNotEmpty()) Ongil.done else Ongil.muted, fontSize = 24.sp)
                Text(r.name, color = Ongil.ink, fontSize = 64.sp, lineHeight = 72.sp, fontWeight = FontWeight.ExtraBold)
            }
            Pill("지역 바꾸기", bg = Ongil.basic, padH = 26, fontSize = 24) { store.openPicker(true) }
        }
        Spacer(Modifier.height(18.dp))
        if (sp.isEmpty()) {
            Text("볼 곳을 준비하고 있어요", color = Ongil.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text("이 지역은 곧 열려요. 지금은 ${store.catalog.catalogs.size}개 지역에서 여행을 시작할 수 있어요.", color = Ongil.muted, fontSize = 23.sp, lineHeight = 33.sp)
            Spacer(Modifier.weight(1f))
            BigButton("볼 곳 있는 지역 고르기", Modifier.fillMaxWidth(), primary = false) { store.openPicker(true) }
            return@Column
        }
        // 진행도는 지자체 세트 기준. 세트 밖 명소는 "가볼만한 곳"(TourAPI)으로 따로 센다
        val setIds = remember(sets) { sets.flatMap { it.spotIds }.toSet() }
        val inSet = remember(sp, setIds) { sp.filter { it.id in setIds } }
        val others = remember(sp, setIds) { sp.filter { it.id !in setIds } }
        var filter by remember(r.code) { mutableStateOf("all") }   // all · set:<id> · others · course:<id>
        fun doneOf(set: StampSet) = set.spotIds.count { it in st }
        showStamp?.let { id -> sets.firstOrNull { it.id == id } }?.let { set ->
            val n = doneOf(set)
            StampDialog(set, r.sido, r.name, n >= set.spotIds.size, earnedDate(store, set), n, set.spotIds.map { id -> (sp.firstOrNull { it.id == id }?.name ?: id) to (spotCounts[id] ?: 0) }) { showStamp = null }
        }
        if (sets.isNotEmpty()) {
            // 지자체 스탬프는 칩처럼 한 줄: 세트마다 72dp 스탬프(채움 = 진행도). 다섯 넘으면 넷 + "+N"(누르면 전체 목록). 설명 글은 없다
            var showAll by remember(r.code) { mutableStateOf(false) }
            val shownSets = if (sets.size > MAX_STAMP_CHIPS) sets.take(MAX_STAMP_CHIPS - 1) else sets
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                shownSets.forEach { set -> val n = doneOf(set)
                    // 스탬프 아래 이름·진행을 작게: 회색 동그라미만으론 뭔지 모른다(3차 평가)
                    Column(Modifier.width(120.dp).clickable { showStamp = set.id }, horizontalAlignment = Alignment.CenterHorizontally) {
                        StampArt(set, r.name, r.sido, n >= set.spotIds.size, size = 72.dp, mini = true, tilt = false, fraction = n.toFloat() / set.spotIds.size.coerceAtLeast(1)) { showStamp = set.id }
                        Text(set.title, color = Ongil.ink2, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        Text("${n}/${set.spotIds.size}", color = if (n >= set.spotIds.size) Ongil.official else Ongil.muted, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (sets.size > MAX_STAMP_CHIPS) Box(Modifier.size(72.dp).background(Ongil.card2, CircleShape).border(1.dp, Ongil.line, CircleShape).clickable { showAll = true }, contentAlignment = Alignment.Center) { Text("+${sets.size - shownSets.size}", color = Ongil.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            }
            if (showAll) StampListDialog(sets.map { set -> Triple(set, doneOf(set), earnedDate(store, set)) }, r.sido, r.name, onPick = { showAll = false; showStamp = it }) { showAll = false }
        } else {
            // 스탬프 세트가 없는 지역: 방문 기록만
            DotRow(sp.size, st.size, dot = 22)
            Spacer(Modifier.height(10.dp))
            Text(when { st.isEmpty() -> "${sp.size}곳 중 아직 한 곳도 안 갔어요"; else -> "${sp.size}곳 중 ${st.size}곳에 갔어요" }, color = Ongil.ink2, fontSize = 26.sp)
            Text("지자체 스탬프는 아직 없어요 · 한국관광공사 명소", color = Ongil.muted, fontSize = 22.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(16.dp))
        // 칩: 전체 / 지자체 세트(초록) / 가볼만한 곳 / 다른 사람 코스 하나씩(고르면 그 사람 코스의 명소가 아래에)
        val courseChips = remember(courses) { courses.sortedByDescending { it.official }.take(5) }
        ChipRow {
            FilterChip("전체 ${sp.size}", filter == "all") { filter = "all" }
            sets.forEach { set -> FilterChip("${set.title} ${doneOf(set)}/${set.spotIds.size}", filter == "set:${set.id}", official = true) { filter = "set:${set.id}" } }
            if (sets.isNotEmpty() && others.isNotEmpty()) FilterChip("가볼만한 곳 ${others.size}", filter == "others") { filter = "others" }
            courseChips.forEach { c -> FilterChip(if (c.official) c.title else "${c.title} · ${c.by}", filter == "course:${c.id}", official = c.official) { filter = "course:${c.id}" } }
            FilterChip("＋ 코스 더", false) { store.go(Screen.Courses) }   // 공개 코스에서 시·군·구별로 더 찾기
        }
        Spacer(Modifier.height(10.dp))
        val course = remember(filter, courses) { courses.firstOrNull { "course:${it.id}" == filter } }
        val chipSet = remember(filter, sets) { sets.firstOrNull { "set:${it.id}" == filter } }
        var courseSel by remember(filter) { mutableStateOf(course?.spotIds?.toSet() ?: emptySet()) }   // 코스에서 갈 곳만 고르기
        var showSpot by remember(r.code) { mutableStateOf<String?>(null) }   // 설명을 여는 명소 id
        Hairline()
        val shown = when { course != null -> course.spotIds.mapNotNull { id -> sp.firstOrNull { it.id == id } }; chipSet != null -> chipSet.spotIds.mapNotNull { id -> sp.firstOrNull { it.id == id } }; filter == "others" -> others; else -> inSet + others }
        showSpot?.let { id -> sp.firstOrNull { it.id == id } }?.let { s ->
            SpotDialog(s, r.name, r.sido, spotCounts[s.id] ?: 0, sets.filter { s.id in it.spotIds }.map { set -> Triple(set, doneOf(set), doneOf(set) >= set.spotIds.size) }, onStamp = { showSpot = null; showStamp = it }) { showSpot = null }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (course != null) {
                // 그 사람이 실제로 다녀온 순서 그대로. 갈 곳만 체크해서 내 코스를 짠다
                Text(if (course.official) "${course.by}가 정한 코스 · 갈 곳만 남기고 누르세요" else "갈 곳만 남기고 누르세요 · 전부 따라갈 필요 없어요", color = if (course.official) Ongil.official else Ongil.muted, fontSize = 20.sp, modifier = Modifier.padding(vertical = 8.dp))
                Hairline()
            }
            shown.forEachIndexed { idx, s ->
                val has = s.id in st
                val n = spotCounts[s.id] ?: 0
                val right = if (has) "${n}번 방문 · ${s.stayMin}분" else "${s.stayMin}분"
                if (course != null) {
                    val on = s.id in courseSel
                    Row(Modifier.fillMaxWidth().height(70.dp).clickable { courseSel = if (on) courseSel - s.id else courseSel + s.id }, verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(32.dp).background(if (on) Ongil.paint else Ongil.card2, CircleShape), contentAlignment = Alignment.Center) { Text("${idx + 1}", color = if (on) Ongil.onFilled else Ongil.muted, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(14.dp))
                        Text(s.name, color = if (on) Ongil.ink else Ongil.muted, fontSize = 30.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(right, color = if (has) Ongil.done else Ongil.muted, fontSize = 22.sp)
                    }
                    Hairline()
                    return@forEachIndexed
                }
                // 명소 줄: 점 · 이름 · 방문/머무는 시간 · 맨 오른쪽에 든 세트의 스탬프 배지(각자 제 스탬프를 연다). 줄을 누르면 설명
                val inSets = sets.filter { s.id in it.spotIds }
                Row(Modifier.fillMaxWidth().height(70.dp).clickable { showSpot = s.id }, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).background(if (has) Ongil.paint else Ongil.card2, CircleShape))
                    Spacer(Modifier.width(16.dp))
                    Text(s.name, color = if (has) Ongil.muted else Ongil.ink, fontSize = 30.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(right, color = if (has) Ongil.done else Ongil.muted, fontSize = 22.sp)
                    if (inSets.isNotEmpty()) {
                        Spacer(Modifier.width(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            inSets.forEach { ts -> StampArt(ts, r.name, r.sido, doneOf(ts) >= ts.spotIds.size, size = 36.dp, mini = true, tilt = false, fraction = doneOf(ts).toFloat() / ts.spotIds.size.coerceAtLeast(1)) { showStamp = ts.id } }
                        }
                    }
                }
                Hairline()
            }
        }
        Spacer(Modifier.height(20.dp))
        val busy = session?.takeIf { it.regionId == r.code }?.status.let { it == TourStatus.ACTIVE || it == TourStatus.DAY_DONE }
        if (course != null && !busy) {
            // 코스를 고른 상태: 맨 아래 버튼 하나가 그 코스로 입력 화면
            BigButton(if (courseSel.isEmpty()) "갈 곳을 골라 주세요" else "고른 ${courseSel.size}곳으로 코스 짜기", Modifier.fillMaxWidth(), enabled = courseSel.isNotEmpty(), sub = "볼 곳을 확인하고 코스를 만들어요") { store.startInput(course.spotIds.filter { it in courseSel }) }
        } else {
            if (course != null) {
                BigButton(if (courseSel.isEmpty()) "갈 곳을 골라 주세요" else "고른 ${courseSel.size}곳으로 코스 짜기", Modifier.fillMaxWidth(), primary = false, height = Ongil.buttonMedium, enabled = courseSel.isNotEmpty()) { store.startInput(course.spotIds.filter { it in courseSel }) }
                Spacer(Modifier.height(10.dp))
            }
            RegionActions(store, r, session)
        }
    }
}

private const val MAX_STAMP_CHIPS = 5   // 지역 카드 스탬프 칩 최대. 넘으면 넷 + "+N"

/** 맨 아래 버튼: 진행 중이면 이어가기, 하루 끝났으면 다음 날, 아니면 시작 */
@Composable
private fun RegionActions(store: AppStore, r: Region, session: TourSession?) {
    val mine = session?.takeIf { it.regionId == r.code }
    when (mine?.status) {
        TourStatus.ACTIVE -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton("여행 이어가기", Modifier.fillMaxWidth()) { store.go(Screen.Arrival) }
            BigButton("내비로 가기", Modifier.fillMaxWidth(), primary = false, height = Ongil.buttonMedium) { store.openNavi() }
        }
        TourStatus.DAY_DONE -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 지난번에 못 간 곳이 남아 있다: 바로 이어가거나, 코스를 보고 빼고 넣거나, 새로 고른다
            BigButton("남은 ${store.remainingCount()}곳 이어가기", Modifier.fillMaxWidth(), sub = "내비가 길을 안내해요") { store.continueDay(mine.day + 1) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton("남은 곳 보기", Modifier.weight(1f), primary = false, height = Ongil.buttonMedium) { store.viewRemaining() }
                BigButton("새로 고르기", Modifier.width(200.dp), primary = false, height = Ongil.buttonMedium) { store.startInput() }
            }
        }
        else -> if (store.catalog.spotsOf(r.code).isEmpty()) BigButton("곧 열리는 지역이에요", Modifier.fillMaxWidth(), enabled = false, sub = "다른 지역을 골라 주세요") {}
                else BigButton("${r.name} 여행 시작", Modifier.fillMaxWidth()) { store.startInput() }
    }
}

/** 레일 안 지역 선택기: 왼쪽 시·도, 오른쪽 시·군·구 */
@Composable
private fun RegionPicker(store: AppStore, sido: String?, selected: String?, stampsByRegion: Map<String, Set<String>>) {
    val sidos = remember(store.catalog.regions) { store.catalog.regions.map { it.sido }.distinct().sortedBy { sd -> SIDO_ORDER.indexOfFirst { sd.startsWith(it) }.let { if (it < 0) 99 else it } } }
    val list = remember(sido, store.catalog.regions) { store.catalog.regions.filter { it.sido == sido }.sortedBy { it.name } }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("어디로 갈까요", color = Ongil.ink, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Pill("닫기", height = Ongil.buttonMedium, bg = Ongil.basic, bordered = false, padH = 28, fontSize = 24, weight = null) { store.openPicker(false) }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.width(200.dp).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                sidos.forEach { sd ->
                    val on = sd == sido
                    Box(Modifier.fillMaxWidth().height(80.dp).background(if (on) Ongil.card else Color.Transparent, RoundedCornerShape(16.dp)).clickable { store.setPickerSido(sd) }.padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
                        Text(shortSido(sd), color = if (on) Ongil.ink else Ongil.muted, fontSize = 28.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState())) {
                list.forEach { r ->
                    val total = store.catalog.spotsOf(r.code).size; val has = total > 0; val n = stampsByRegion[r.code]?.size ?: 0
                    SpotLine(
                        r.name, Modifier.clickable { store.pickRegion(r.code) }, height = 88, dot = 12, gap = 14,
                        dotColor = if (n > 0) Ongil.paint else if (has) Ongil.ink else Ongil.card2,
                        nameColor = if (r.code == selected) Ongil.paint else if (has) Ongil.ink else Ongil.muted,
                        right = when { n > 0 -> "$n / $total"; has -> "미방문"; else -> "준비 중" },   // 방문한 곳 / 볼 곳 수
                        rightColor = if (n > 0) Ongil.done else if (has) Ongil.muted else Ongil.faint,
                    )
                    Hairline()
                }
            }
        }
    }
}

internal fun shortSido(s: String): String = s.replace("특별자치도", "").replace("특별자치시", "").replace("특별시", "").replace("광역시", "").replace("통합특별시", "").let { if (it.length > 4) it.take(4) else it }

