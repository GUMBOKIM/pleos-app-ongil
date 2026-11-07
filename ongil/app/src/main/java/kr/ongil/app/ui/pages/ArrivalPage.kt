package kr.ongil.app.ui.pages

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kr.ongil.app.store.AppStore
import kr.ongil.app.ui.components.BigButton
import kr.ongil.app.ui.components.DotRow
import kr.ongil.app.ui.components.Hairline
import kr.ongil.app.ui.components.LocalNarrow
import kr.ongil.app.ui.components.Ongil
import kr.ongil.app.ui.components.RailHead
import kr.ongil.app.store.Screen
import kr.ongil.app.ui.components.Stage
import kr.ongil.app.ui.components.map.KoreaMap
import kr.ongil.app.ui.components.map.MapDot
import kr.ongil.core.Spot
import kr.ongil.core.TourSession
import kr.ongil.core.TourStatus
import kr.ongil.core.VisitStage
import kr.ongil.core.VisitState

/** M4 도착: 지도 위 그 명소의 점이 맥동하다가, 방문 기록이 그 위로 쾅 찍힌다. 오른쪽은 이야기와 다음 */
@Composable
fun ArrivalPage(store: AppStore) {
    val saved by store.records.state.collectAsStateWithLifecycle()
    val speaking by store.gw.narrator.speaking.collectAsStateWithLifecycle()
    val session = saved.session
    val region = session?.let { store.catalog.region(it.regionId) }
    val spots = session?.let { store.catalog.spotsOf(it.regionId) } ?: emptyList()
    val stampedIds = remember(saved.stamps, session?.regionId) { saved.stampedIn(session?.regionId) }
    val cur = session?.currentVisit
    val showId = when {
        cur != null && (cur.stage == VisitStage.IN_RADIUS || cur.stage == VisitStage.DWELLING) -> cur.spotId
        session?.lastStampedSpotId != null -> session.lastStampedSpotId
        else -> cur?.spotId
    }
    val visit = session?.visits?.firstOrNull { it.spotId == showId }
    val spot = showId?.let { store.catalog.spot(it) }
    val next = session?.visits?.firstOrNull { it.spotId != showId && it.stage != VisitStage.STAMPED && it.stage != VisitStage.PASSED }?.let { store.catalog.spot(it.spotId) }
    // 서 있는 동안만 0.5초마다 시계를 돌린다 (타이머 링·남은 시간)
    val dwelling = visit?.stage == VisitStage.DWELLING
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(dwelling) { if (dwelling) while (true) { now = System.currentTimeMillis(); delay(500) } }
    val fast = saved.demoFastDwell
    val dwellMs = (spot?.stamp?.dwellSec ?: 180) * 1000L / (if (fast) 30 else 1)
    val elapsed = visit?.dwellStartedAt?.let { now - it } ?: 0L
    val stamped = visit?.stage == VisitStage.STAMPED
    val progress = when (visit?.stage) { VisitStage.STAMPED -> 1f; VisitStage.DWELLING -> (elapsed.toFloat() / dwellMs).coerceIn(0f, 1f); else -> 0f }

    if (session == null || spot == null) {
        Stage(stage = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("진행 중인 여행이 없어요", color = Ongil.muted, fontSize = 30.sp) } }) {
            RailHead("도착", onBack = { store.go(Screen.Home) })
        }
        return
    }
    val fills = rememberHeat(saved)
    // 보여주는 명소만 이름·active. 스탬프가 찍히면 pulse를 0으로 꺼서 파동만 멈춘다
    val dots = remember(spots, stampedIds, spot.id) { spots.map { MapDot(it.lat, it.lng, it.id in stampedIds, if (it.id == spot.id) it.name else "", active = it.id == spot.id) } }
    val pulseT = rememberInfiniteTransition(label = "pulse")
    val pulse by pulseT.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "p")

    var dotPos by remember { mutableStateOf<Offset?>(null) }
    val stampPx = with(LocalDensity.current) { 300.dp.toPx() }
    Stage(stage = {
        KoreaMap(store.catalog.geometry, fills, session.regionId, null, session.regionId, Modifier.fillMaxSize(), dots = dots, zoomFactor = 1.7f, pulse = if (stamped) 0f else pulse, labels = false, onActivePos = { if (dotPos != it) dotPos = it })
        Column(Modifier.stageTop()) {
            Text(region?.name ?: "", color = Ongil.muted, fontSize = 24.sp)
            Text(spot.name, color = Ongil.ink, fontSize = 48.sp, lineHeight = 56.sp, fontWeight = FontWeight.ExtraBold)
        }
        // 스탬프: 그 명소 점 위에. 점 위치를 아직 모르면 무대 가운데
        val pos = dotPos
        Box(if (pos != null) Modifier.offset { IntOffset((pos.x - stampPx / 2).toInt(), (pos.y - stampPx / 2).toInt()) } else Modifier.align(Alignment.Center), contentAlignment = Alignment.Center) { StampHero(progress, stamped, spot.name, region?.name ?: "", spot.stamp.radiusM, elapsed, dwellMs, visit?.stage) }
        Column(stageBottomStart()) {
            Text(dwellStatus(visit, spot, elapsed, dwellMs, fast), color = if (stamped) Ongil.done else Ongil.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            if (stamped) Text("차 위치와 기어 P로 기록했어요", color = Ongil.muted, fontSize = 22.sp)
        }
    }) {
        // 좁은 창(내비 옆)에선 무대가 없으니 명소 이름을 레일 위에
        if (LocalNarrow.current) { Text(region?.name ?: "", color = Ongil.muted, fontSize = 22.sp); Text(spot.name, color = Ongil.ink, fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(12.dp)) }
        // 진행도: 이 명소가 든 지자체 세트가 있으면 그 세트 기준(제목과 같은 분모), 없으면 지역 명소 전체
        val set = remember(spot.id) { store.stamps.setsWith(spot.id).firstOrNull() }
        val have = if (set != null) set.spotIds.count { it in stampedIds } else stampedIds.size
        val total = set?.spotIds?.size ?: spots.size
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$have", color = Ongil.paint, fontSize = 56.sp, lineHeight = 60.sp, fontWeight = FontWeight.ExtraBold)
            Text(" / $total", color = Ongil.muted, fontSize = 28.sp, modifier = Modifier.padding(bottom = 8.dp))
            Spacer(Modifier.weight(1f))
            Text(store.stampTitleFor(session.regionId, spot.id)?.let { "$it · 다 가면 스탬프" } ?: "${region?.name}에 선 곳", color = Ongil.muted, fontSize = 22.sp, modifier = Modifier.padding(bottom = 10.dp))
        }
        Spacer(Modifier.height(10.dp))
        DotRow(total, have, dot = 20, activeIndex = if (stamped) -1 else have)
        Spacer(Modifier.height(24.dp))
        Hairline()
        Spacer(Modifier.height(18.dp))
        StoryPanel(store, spot, speaking)
        Spacer(Modifier.height(20.dp))
        Hairline()
        Spacer(Modifier.height(16.dp))
        NextSection(store, session, next)
    }
}

/** 무대 왼쪽 아래 상태 문장 */
private fun dwellStatus(visit: VisitState?, spot: Spot, elapsed: Long, dwellMs: Long, fast: Boolean): String = when (visit?.stage) {
    VisitStage.STAMPED -> "반경 ${spot.stamp.radiusM}m 안에서 ${spot.stamp.dwellSec / 60}분 서 있어서 방문이 기록됐어요"
    VisitStage.DWELLING -> "서 있는 중 ${fmt(elapsed)} / ${fmt(dwellMs)}"
    VisitStage.IN_RADIUS -> "반경 안이에요. 기어를 P에 두면 3분을 재요"
    VisitStage.PASSED -> "서지 않고 지나갔어요"
    else -> visit?.lastDistanceM?.let { "${spot.name}까지 ${"%.0f".format(it)}m" } ?: "위치 확인 중"
}

/** 차가 들려주는 이야기: 제목·듣는 중 막대·본문·출처·다시 듣기/멈춤 */
@Composable
private fun ColumnScope.StoryPanel(store: AppStore, spot: Spot, speaking: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("차가 들려주는 이야기", color = Ongil.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (speaking) SpeakingBars() else Text("약 30초", color = Ongil.muted, fontSize = 22.sp)
    }
    Spacer(Modifier.height(12.dp))
    Text(spot.story, color = Ongil.ink2, fontSize = 28.sp, lineHeight = 42.sp, modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()))
    Text(storySourceLabel(spot), color = Ongil.muted, fontSize = 19.sp)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BigButton(if (speaking) "듣는 중" else "다시 듣기", Modifier.weight(1f), primary = false, height = Ongil.buttonMedium) { store.replayStory() }
        BigButton("멈춤", Modifier.width(200.dp), primary = false, height = Ongil.buttonMedium) { store.stopStory() }
    }
}

/** 레일 맨 아래: 다음 명소로 내비 / 오늘은 여기까지. 끝나면 홈(지도)으로. 카드·공유는 내 지도에서 나중에 */
@Composable
private fun NextSection(store: AppStore, session: TourSession, next: Spot?) {
    if (session.status != TourStatus.ACTIVE || next == null) {
        Text("오늘 코스가 끝났어요", color = Ongil.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("지도에 칠해졌어요 · 기록은 내 지도에서 나중에", color = Ongil.muted, fontSize = 22.sp)
        Spacer(Modifier.height(12.dp))
        BigButton("지도로 돌아가기", Modifier.fillMaxWidth()) { if (session.status == TourStatus.ACTIVE) store.endDay() else store.go(Screen.Home) }
    } else {
        Text("다음은 ${next.name}", color = Ongil.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(next.hint, color = Ongil.muted, fontSize = 22.sp)
        Spacer(Modifier.height(12.dp))
        BigButton("내비로 가기", Modifier.fillMaxWidth()) { store.openNavi() }
        Spacer(Modifier.height(10.dp))
        BigButton("오늘은 여기까지", Modifier.fillMaxWidth(), primary = false, height = Ongil.buttonMedium) { store.endDay() }
    }
}

private fun fmt(ms: Long): String = mmss((ms / 1000).toInt())

/** 이야기 출처를 사람 말로. 데이터 파일의 기술적 출처 문자열(TourAPI id 등)은 화면에 안 보인다 */
private fun storySourceLabel(spot: Spot): String = when {
    "충주시" in spot.storySource || "충주시" in spot.source -> "충주시 문화관광 자료를 바탕으로 정리했어요"
    else -> "한국관광공사 소개글을 바탕으로 정리했어요"
}

/** 방문 기록: 찍히기 전엔 숨 쉬는 타이머 링, 찍히는 순간 파문 두 겹이 퍼지고 인주가 튀며 2.4배에서 탄성 있게 내려앉는다 */
@Composable
private fun StampHero(progress: Float, stamped: Boolean, name: String, regionName: String, radiusM: Int, elapsed: Long, dwellMs: Long, stage: VisitStage?) {
    val slam = remember { Animatable(if (stamped) 1f else 0f) }
    val ripple = remember { Animatable(if (stamped) 1f else 0f) }
    val breathe = rememberInfiniteTransition(label = "breathe").animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    LaunchedEffect(stamped) {
        if (stamped) {
            slam.snapTo(0f); ripple.snapTo(0f)
            launch { ripple.animateTo(1f, tween(1100, easing = LinearOutSlowInEasing)) }
            slam.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
        }
    }
    Box(Modifier.size(320.dp), contentAlignment = Alignment.Center) {
        if (!stamped) {
            val pulse = if (stage == VisitStage.DWELLING) 1f + 0.03f * breathe.value else 1f
            Canvas(Modifier.fillMaxSize().scale(pulse)) {
                drawCircle(Ongil.bg2.copy(alpha = 0.75f), size.minDimension / 2 - 12f)
                drawCircle(Ongil.line, style = Stroke(14f))
                if (stage == VisitStage.DWELLING) drawCircle(Ongil.paint.copy(alpha = 0.12f + 0.1f * breathe.value), size.minDimension / 2 - 30f)
                if (progress > 0f) drawArc(Ongil.paint, -90f, 360f * progress, false, style = Stroke(14f, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (stage == VisitStage.DWELLING) fmt(dwellMs - elapsed) else "${radiusM}m 안", color = Ongil.ink, fontSize = if (stage == VisitStage.DWELLING) 56.sp else 28.sp, fontWeight = FontWeight.ExtraBold)
                Text(if (stage == VisitStage.DWELLING) "남았어요" else "3분 서 있으면 방문 기록", color = Ongil.muted, fontSize = 22.sp, textAlign = TextAlign.Center)
            }
        } else {
            // 파문: 찍힌 자리에서 바깥으로 두 겹, 인주 방울 8개가 튀어 나가며 옅어진다
            val r = ripple.value
            Canvas(Modifier.fillMaxSize()) {
                val c = center; val base = size.minDimension / 2
                drawCircle(Ongil.stamp.copy(alpha = (1f - r) * 0.55f), base * (0.55f + 0.75f * r), c, style = Stroke(10f * (1f - r) + 2f))
                val r2 = (r - 0.18f).coerceIn(0f, 1f) / 0.82f
                drawCircle(Ongil.stamp.copy(alpha = (1f - r2) * 0.35f), base * (0.6f + 0.6f * r2), c, style = Stroke(6f * (1f - r2) + 1f))
                for (k in 0 until 8) {
                    val a = Math.toRadians(k * 45.0 + 20.0); val dist = base * (0.62f + 0.5f * r)
                    val p = androidx.compose.ui.geometry.Offset(c.x + (dist * Math.cos(a)).toFloat(), c.y + (dist * Math.sin(a)).toFloat())
                    drawCircle(Ongil.stamp.copy(alpha = (1f - r) * 0.9f), (10f - 6f * r) * (if (k % 2 == 0) 1f else 0.6f), p)
                }
            }
            val sc = 2.4f - 1.4f * slam.value
            val rot = -18f + 12f * slam.value
            Box(Modifier.size(260.dp).scale(sc).rotate(rot).alpha(0.35f + 0.65f * slam.value).background(Ongil.bg2.copy(alpha = 0.92f), CircleShape).background(Ongil.stamp.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) { drawCircle(Ongil.stamp, style = Stroke(10f)); drawCircle(Ongil.stamp, radius = size.minDimension / 2 - 22f, style = Stroke(3f)) }
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("온길 방문", color = Ongil.stamp, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(name, color = Ongil.stamp, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, lineHeight = 40.sp)
                    Text(regionName, color = Ongil.stamp.copy(alpha = 0.85f), fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun SpeakingBars() {
    val t = rememberInfiniteTransition(label = "bars")
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { i ->
            val h: Float by t.animateFloat(initialValue = 10f, targetValue = 30f, animationSpec = infiniteRepeatable(tween(380 + i * 70), RepeatMode.Reverse), label = "b$i")
            Box(Modifier.width(6.dp).height(h.dp).background(Ongil.paint, RoundedCornerShape(3.dp)))
        }
    }
}
