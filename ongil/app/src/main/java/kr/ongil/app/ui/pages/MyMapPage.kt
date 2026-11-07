package kr.ongil.app.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kr.ongil.app.BuildConfig
import kr.ongil.app.store.AppStore
import kr.ongil.app.ui.components.BigButton
import kr.ongil.app.ui.components.Hairline
import kr.ongil.app.ui.components.Ongil
import kr.ongil.app.ui.components.QrImage
import kr.ongil.app.ui.components.RailHead
import kr.ongil.app.store.Screen
import kr.ongil.app.ui.components.Stage
import kr.ongil.app.ui.components.map.KoreaMap

/**
 * 내 지도: 왼쪽은 전국 히트맵(자랑의 기본 단위), 오른쪽은 숫자 셋·지난 여행(여행 단위로 묶음)·폰으로 받기(QR 다이얼로그). 스탬프 숫자를 누르면 스탬프.
 */
@Composable
fun MyMapPage(store: AppStore) {
    val saved by store.records.state.collectAsStateWithLifecycle()
    val heat = rememberHeat(saved)
    val done = rememberDone(store, saved)
    val earnedSetCount = remember(saved.stamps) { store.stamps.all.count { kr.ongil.core.Stamps.earned(it, saved.stamps) } }   // 스탬프와 같은 수(세트 단위)
    val stampedSpots = remember(saved.stamps) { saved.stamps.map { it.spotId }.toSet().size }

    // 지난 여행은 여행 단위로 묶는다(이어간 회차도 한 줄). 최근 여행이 위
    val trips = remember(saved.cards) { saved.cards.groupBy { it.planId }.values.map { cs -> cs.sortedBy { it.day } }.sortedByDescending { it.last().date } }
    val since = remember(saved.cards) { saved.cards.minOfOrNull { it.date }?.let { "${it.take(4)}년 ${it.substring(5,7).toInt()}월" } }
    var showQr by remember { mutableStateOf(false) }
    val payload = remember(heat, done, earnedSetCount, stampedSpots) { mapPayload(heat, done, earnedSetCount, stampedSpots) }
    if (showQr) QrDialog(payload, "내 지도가 이미지로 열려요. 저장해서 바로 올릴 수 있어요. 기록은 주소 안에만 있고 어디에도 보내지 않아요.") { showQr = false }

    Stage(stage = {
        KoreaMap(store.catalog.geometry, heat, null, null, null, Modifier.fillMaxSize(), done = done)
        Column(Modifier.stageTop()) {
            Text("내 지도", color = Ongil.ink, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
            Text((since?.let { "${it}부터 · " } ?: "") + "많이 방문한 지역일수록 진하게", color = Ongil.muted, fontSize = 22.sp)
        }
    }) {
        RailHead("내 지도", onBack = { store.go(Screen.Home) })
        Spacer(Modifier.height(18.dp))
        // 숫자 셋: 큰 숫자 바로 아래 뜻을 크게(노안·tech 낮은 사람이 "14·12·75가 뭔지 모르겠다")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigStat("${heat.size}", "지역", Ongil.paint, Modifier.weight(1f)) { }
            BigStat("$earnedSetCount", "스탬프 ›", Ongil.ink, Modifier.weight(1f)) { store.go(Screen.Stamps) }
            BigStat("$stampedSpots", "곳 방문", Ongil.ink, Modifier.weight(1f)) { }
        }
        Spacer(Modifier.height(18.dp))
        Hairline()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text("지난 여행", color = Ongil.ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            if (trips.isEmpty()) Text("온길을 켜고 명소에 서면 여기에 쌓여요", color = Ongil.muted, fontSize = 24.sp, modifier = Modifier.padding(vertical = 12.dp))
            trips.forEach { cs ->
                val first = cs.first(); val last = cs.last()
                // 한 여행이 여러 지역을 돌았으면 지역 이름을 다 보여 준다("전주시 · 담양군")
                val names = cs.map { it.regionId }.distinct().map { store.catalog.region(it)?.name ?: it }
                val title = if (names.size > 2) "${names[0]} · ${names[1]} 외 ${names.size - 2}" else names.joinToString(" · ")
                val dates = if (first.date == last.date) first.date.drop(5) else "${first.date.drop(5)} ~ ${last.date.drop(5)}"
                val spots = cs.flatMap { it.stampedSpotIds }.distinct().size
                Row(Modifier.fillMaxWidth().height(76.dp).clickable { store.go(Screen.Card(last.id)) }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, color = Ongil.ink, fontSize = 28.sp, fontWeight = FontWeight.Medium)
                        Text(dates + (if (cs.size > 1) " · ${cs.size}번 나눠 감" else ""), color = Ongil.muted, fontSize = 22.sp)
                    }
                    Text("${spots}곳", color = Ongil.done, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    if (cs.any { it.regionCompleted }) { Spacer(Modifier.width(10.dp)); Text("스탬프", color = Ongil.official, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                }
                Hairline()
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton("폰으로 받기", Modifier.weight(1f), primary = false, height = Ongil.buttonMedium) { showQr = true }
            BigButton("공개 코스", Modifier.weight(1f), primary = false, height = Ongil.buttonMedium) { store.go(Screen.Courses) }
        }
    }
}

/** 큰 숫자 + 그 아래 뜻(22sp). 누르면 관련 화면 */
@Composable
private fun BigStat(value: String, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.background(Ongil.card, RoundedCornerShape(Ongil.rMedium)).clickable { onClick() }.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = Ongil.ink2, fontSize = 22.sp)
    }
}

/** 폰으로 받기: 큰 QR 하나와 설명. 내 지도·여행 기록이 같이 쓴다 */
@Composable
internal fun QrDialog(payload: String, body: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Row(Modifier.background(Ongil.bg, RoundedCornerShape(Ongil.rLarge)).padding(36.dp), verticalAlignment = Alignment.CenterVertically) {
            QrImage(payload, 340)
            Spacer(Modifier.width(32.dp))
            Column(Modifier.width(380.dp)) {
                Text("폰 카메라로 찍어요", color = Ongil.ink, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                Text(body, color = Ongil.ink2, fontSize = 24.sp, lineHeight = 36.sp)
                Spacer(Modifier.height(24.dp))
                BigButton("닫기", Modifier.fillMaxWidth(), primary = false, height = Ongil.buttonMedium) { onDismiss() }
            }
        }
    }
}

/** QR 안에 내 지도 요약: 지역별 진하기 + 집계. 폰 페이지가 이미지로 그린다(서버 없음) */
private fun mapPayload(heat: Map<String, Float>, done: Set<String>, stampCount: Int, spots: Int): String {
    val json = buildJsonObject {
        put("t", "map"); put("n", heat.size); put("c", stampCount); put("s", spots)
        put("h", JsonPrimitive(heat.entries.joinToString(",") { "${it.key}:${"%.1f".format(it.value)}" }))
        put("d", JsonPrimitive(done.joinToString(",")))
    }.toString()
    return "${BuildConfig.SHARE_BASE}/#m=" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
}
