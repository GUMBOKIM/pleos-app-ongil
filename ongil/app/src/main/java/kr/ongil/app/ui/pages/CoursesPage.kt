package kr.ongil.app.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.ongil.app.store.AppStore
import kr.ongil.app.ui.components.BigButton
import kr.ongil.app.ui.components.Hairline
import kr.ongil.app.ui.components.ChipRow
import kr.ongil.app.ui.components.FilterChip
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kr.ongil.app.ui.components.Ongil
import kr.ongil.app.ui.components.RailHead
import kr.ongil.app.store.Screen
import kr.ongil.app.ui.components.Stage
import kr.ongil.app.ui.components.Tag
import kr.ongil.app.ui.components.map.KoreaMap
import kr.ongil.app.ui.components.map.MapDot
import kr.ongil.core.Course

/**
 * 공개 코스: 다른 차가 실제로 다녀온 코스를 고르면 내비에 그대로 꽂힌다.
 * 1차는 서버 없이 assets 샘플 + 내가 올린 카드(화면엔 표시 안 함).
 */
@Composable
fun CoursesPage(store: AppStore) {
    val saved by store.records.state.collectAsStateWithLifecycle()
    val remote by store.courses.remote.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (remote.isEmpty()) store.refreshCourses() }
    val courses = remember(saved.sharedCardIds, saved.cards, remote) { store.allCourses(saved) }
    val selId by store.course.collectAsStateWithLifecycle()
    val selectedRegion by store.selected.collectAsStateWithLifecycle()
    // 고른 게 없으면 보고 있던 지역의 코스 중 가장 많이 간 것
    val c = courses.firstOrNull { it.id == selId } ?: courses.filter { it.regionId == selectedRegion }.maxByOrNull { it.cars } ?: courses.maxByOrNull { it.cars }
    val spots = remember(c) { c?.spotIds?.mapNotNull { store.catalog.spot(it) } ?: emptyList() }
    val dots = remember(spots) { spots.mapIndexed { i, s -> MapDot(s.lat, s.lng, false, s.name, index = i + 1) } }
    val route = remember(spots) { spots.map { it.latLng } }
    val region = c?.let { store.catalog.region(it.regionId) }

    Stage(stage = {
        KoreaMap(store.catalog.geometry, emptyMap(), c?.regionId, null, c?.regionId, Modifier.fillMaxSize(), dots = dots, zoomFactor = 1.7f, route = route, labels = true)
        CoursesOverlay(c, region?.name, region?.sido, spots.size)
    }) {
        RailHead("공개 코스", onBack = { store.go(Screen.Home) })
        Spacer(Modifier.height(12.dp))
        // 범위: 시·도 → 시·군·구(코스가 있는 곳만). 처음엔 보고 있던 지역
        val regionOf = { id: String -> store.catalog.region(id) }
        var sido by remember { mutableStateOf(selectedRegion?.let { regionOf(it)?.sido }) }
        var regionId by remember { mutableStateOf(selectedRegion) }
        val sidos = remember(courses) { courses.mapNotNull { regionOf(it.regionId)?.sido }.distinct().sortedBy { sd -> SIDO_ORDER.indexOfFirst { sd.startsWith(it) }.let { if (it < 0) 99 else it } } }
        val regionsIn = remember(courses, sido) { courses.map { it.regionId }.distinct().filter { regionOf(it)?.sido == sido }.sortedBy { regionOf(it)?.name } }
        ChipRow {
            FilterChip("전국 ${courses.size}", sido == null) { sido = null; regionId = null }
            sidos.forEach { sd -> FilterChip("${shortSido(sd)} ${courses.count { regionOf(it.regionId)?.sido == sd }}", sido == sd) { sido = sd; regionId = null } }
        }
        if (sido != null) {
            Spacer(Modifier.height(8.dp))
            ChipRow {
                FilterChip("${shortSido(sido!!)} 전체", regionId == null) { regionId = null }
                regionsIn.forEach { rid -> FilterChip("${regionOf(rid)?.name ?: rid} ${courses.count { it.regionId == rid }}", regionId == rid) { regionId = rid } }
            }
        }
        Spacer(Modifier.height(12.dp))
        Hairline()
        Spacer(Modifier.height(10.dp))
        // 지자체 코스 먼저, 그 다음 이 코스로 간 차가 많은 순. 수백 개라 LazyColumn
        val sorted = remember(courses, sido, regionId) { courses.filter { (regionId == null || it.regionId == regionId) && (sido == null || regionOf(it.regionId)?.sido == sido) }.sortedWith(compareByDescending<Course> { it.official }.thenByDescending { !it.sample }.thenByDescending { it.cars }) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (sorted.isEmpty()) item { Text("이 범위엔 아직 코스가 없어요", color = Ongil.muted, fontSize = 24.sp) }
            itemsIndexed(sorted, key = { _, k -> k.id }) { idx, k ->
                val on = k.id == c?.id
                val r = store.catalog.region(k.regionId)
                Column(
                    Modifier.fillMaxWidth().background(if (on) Ongil.paint.copy(alpha = 0.14f) else Ongil.card, RoundedCornerShape(Ongil.rMedium))
                        .border(2.dp, if (on) Ongil.paint else Color.Transparent, RoundedCornerShape(Ongil.rMedium)).clickable { store.selectCourse(k.id) }.padding(horizontal = 22.dp, vertical = 16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(k.title, color = if (on) Ongil.paint else Ongil.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (k.official) Tag("지자체", Ongil.official)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(listOfNotNull(r?.name, "${k.spotIds.size}곳", if (k.official) k.by else if (k.by.isNotBlank()) "by ${k.by}" else null).joinToString(" · "), color = Ongil.muted, fontSize = 21.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        BigButton("이 코스의 볼 곳 고르기", Modifier.fillMaxWidth(), enabled = c != null, sub = "갈 곳만 골라 내 코스를 짜요") { c?.let { store.select(it.regionId); store.startInput(it.spotIds) } }
    }
}

@Composable
private fun BoxScope.CoursesOverlay(c: Course?, regionName: String?, sido: String?, n: Int) {
    Column(Modifier.stageTop()) {
        Text(listOfNotNull(sido, regionName).joinToString(" "), color = Ongil.muted, fontSize = 24.sp)
        Text(c?.title ?: "공개 코스", color = Ongil.ink, fontSize = 48.sp, lineHeight = 56.sp, fontWeight = FontWeight.ExtraBold)
        if (c != null) Text(listOfNotNull("${n}곳", c.distanceKm?.let { "%.0fkm".format(it) }, if (c.official) "${c.by}가 정한 코스" else "이 코스로 간 차 ${c.cars}대").joinToString(" · "), color = if (c.official) Ongil.official else Ongil.paint, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
    Row(stageBottomStart(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (c?.verified == true && !c.official) Tag("실제로 다녀온 차의 기록", Ongil.paint)   // 지자체가 정한 코스는 차가 다녀온 기록이 아니다
    }
}
