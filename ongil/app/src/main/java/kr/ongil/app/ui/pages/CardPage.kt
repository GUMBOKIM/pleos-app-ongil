package kr.ongil.app.ui.pages

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import kr.ongil.app.ui.components.BigButton
import kr.ongil.app.ui.components.Chip
import kr.ongil.app.ui.components.Hairline
import kr.ongil.app.ui.components.Ongil
import kr.ongil.app.ui.components.RailHead
import kr.ongil.app.store.Screen
import kr.ongil.app.ui.components.Stage
import kr.ongil.app.ui.components.Stat
import kr.ongil.app.ui.components.map.KoreaMap
import kr.ongil.app.ui.components.map.MapDot
import kr.ongil.core.Achievements
import kr.ongil.core.DayCard
import kr.ongil.core.TourStatus

/** M5 그날 카드 / 스탬프 카드(세트를 다 돌면): 왼쪽은 칠해진 지역과 그날 선 곳, 오른쪽은 기록과 QR */
@Composable
fun CardPage(store: AppStore, cardId: String?) {
    val saved by store.records.state.collectAsStateWithLifecycle()
    val card = (cardId?.let { id -> saved.cards.firstOrNull { it.id == id } } ?: saved.cards.lastOrNull())
    val region = card?.let { store.catalog.region(it.regionId) }
    val spots = card?.let { store.catalog.spotsOf(it.regionId) } ?: emptyList()
    val regionSets = card?.let { store.stamps.setsOf(it.regionId) } ?: emptyList()
    val setTitle = when (regionSets.size) { 0 -> null; 1 -> regionSets[0].title; else -> "${regionSets[0].title} 외 ${regionSets.size - 1}" }
    // 탭은 이 화면만의 상태. 카드가 바뀌면 스탬프 여부로 초기화
    var completionTab by remember(card?.id) { mutableStateOf(card?.regionCompleted == true) }
    val session = saved.session
    val plan = saved.plan
    if (card == null) {
        Stage(stage = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("아직 여행 기록이 없어요", color = Ongil.muted, fontSize = 30.sp) } }) { RailHead("여행 기록", onBack = { store.go(Screen.MyMap) }) }
        return
    }
    val stampedAll = remember(saved.stamps, card.regionId) { saved.stampedIn(card.regionId) }
    val fills = remember(card.regionId, card.regionCompleted) { mapOf(card.regionId to if (card.regionCompleted) 1f else 0.5f) }
    val todays = remember(card.stampedSpotIds) { card.stampedSpotIds.toSet() }
    val dots = remember(spots, stampedAll, todays, completionTab) { spots.map { MapDot(it.lat, it.lng, it.id in stampedAll, it.name, active = !completionTab && it.id in todays) } }
    val route = remember(plan, card.day) { plan?.days?.firstOrNull { it.day == card.day }?.stops?.mapNotNull { st -> store.catalog.spot(st.spotId)?.latLng } ?: emptyList() }

    Stage(stage = {
        KoreaMap(store.catalog.geometry, fills, card.regionId, null, card.regionId, Modifier.fillMaxSize(), dots = dots, zoomFactor = 1.7f, route = if (completionTab) emptyList() else route, labels = true)
        val earnedTitles = remember(saved.stamps, card.regionId) { store.stamps.earnedSets(card.regionId, saved.stamps).joinToString(" · ") { it.title } }
        CardOverlay(card, region?.name, earnedTitles.ifBlank { setTitle }, regionSets.firstOrNull()?.title, completionTab)
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip("이번 온길", !completionTab, Modifier.weight(1f)) { completionTab = false }
            if (setTitle != null) Chip("스탬프 카드", completionTab, Modifier.weight(1f), sub = if (card.regionCompleted) null else "다 가면 열려요") { if (card.regionCompleted) completionTab = true }
        }
        Spacer(Modifier.height(22.dp))
        Hairline()
        val listIds = if (completionTab) spots.map { it.id } else card.stampedSpotIds
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            listIds.forEach { id ->
                val sp = store.catalog.spot(id); val st = saved.stamps.firstOrNull { it.spotId == id }
                val at = st?.let { java.text.SimpleDateFormat("HH:mm", java.util.Locale.KOREA).format(java.util.Date(it.at)) }
                SpotLine(sp?.name ?: id, height = 76, right = st?.let { listOfNotNull(at, if (it.dwellSec >= 60) "${it.dwellSec / 60}분 머묾" else null).joinToString(" · ").ifBlank { "방문" } })
                Hairline()
            }
            if (!completionTab && card.skippedSpotIds.isNotEmpty()) Text("지나감 · " + card.skippedSpotIds.mapNotNull { store.catalog.spot(it)?.name }.joinToString(", "), color = Ongil.muted, fontSize = 22.sp, modifier = Modifier.padding(top = 12.dp))
            // 비어 보이지 않게, 그리고 다음 여행의 씨앗: 이 지역에서 아직 안 간 곳
            val notYet = if (completionTab) emptyList() else spots.filter { it.id !in stampedAll }
            if (notYet.isNotEmpty()) {
                Text("다음에 가 볼 곳 · ${notYet.size}곳", color = Ongil.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 4.dp))
                notYet.forEach { s -> SpotLine(s.name, height = 64, dotColor = Ongil.card2, nameColor = Ongil.ink2, right = "${s.stayMin}분"); Hairline() }
            }
            card.achievements.forEach { a ->
                Row(Modifier.fillMaxWidth().padding(top = 12.dp).background(Ongil.paint.copy(alpha = 0.14f), RoundedCornerShape(16.dp)).padding(horizontal = 18.dp, vertical = 12.dp)) { Text((if (Achievements.isStamp(a)) "스탬프 · " else "도전과제 · ") + achievementLabel(store, a, card.regionId), color = Ongil.paint, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.height(18.dp))
        val shared = card.id in saved.sharedCardIds
        if (!completionTab && card.stampedSpotIds.isNotEmpty()) {
            BigButton(if (shared) "코스 올렸어요" else "이 코스 올리기", Modifier.fillMaxWidth(), primary = false, enabled = !shared, height = Ongil.buttonMedium) { store.shareCard(card.id) }
            Spacer(Modifier.height(10.dp))
        }
        when {
            session?.status == TourStatus.DAY_DONE && plan != null && session.planId == card.planId -> BigButton("남은 ${store.remainingCount()}곳 보기", Modifier.fillMaxWidth(), sub = "빼거나 다시 골라 출발해요") { store.viewRemaining() }
            else -> BigButton("내 지도로", Modifier.fillMaxWidth()) { store.go(Screen.MyMap) }
        }
    }
}

/** 무대 위: 날짜·제목·진행(왼쪽 위), 거리·시간·선 곳 수(왼쪽 아래). 0이거나 변화 없는 숫자는 숨긴다 */
@Composable
private fun BoxScope.CardOverlay(card: DayCard, regionName: String?, setTitle: String?, firstSet: String?, completionTab: Boolean) {
    Column(Modifier.stageTop()) {
        Text(card.date, color = Ongil.muted, fontSize = 24.sp)
        Text(if (completionTab) "${setTitle ?: regionName} 스탬프" else (regionName ?: "") + (if (card.day > 1) " · 이어서" else ""), color = Ongil.ink, fontSize = 56.sp, lineHeight = 64.sp, fontWeight = FontWeight.ExtraBold)
        Text(if (completionTab) "${card.progressTotal}곳에 모두 가서 받았어요" else "오늘 ${card.stampedSpotIds.size}곳 · ${firstSet ?: regionName} ${card.progressAfter}/${card.progressTotal}", color = Ongil.paint, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
    Row(stageBottomStart(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        card.distanceKm?.takeIf { it >= 5.0 }?.let { Stat("%.0f".format(it), "km", size = 40) }
        card.durationMin?.takeIf { it >= 5 }?.let { Stat(mmss(it), "걸린 시간", size = 40) }
        Stat("${card.stampedSpotIds.size}", "방문", size = 40, color = Ongil.paint)
    }
}

