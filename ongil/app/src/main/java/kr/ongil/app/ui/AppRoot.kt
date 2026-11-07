package kr.ongil.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.ongil.app.BuildConfig
import kr.ongil.app.store.AppStore
import kr.ongil.core.TourSession
import kr.ongil.app.ui.components.BigButton
import kr.ongil.app.store.Screen
import kr.ongil.app.ui.components.AppScreen
import kr.ongil.app.ui.components.Ongil
import kr.ongil.app.ui.pages.ArrivalPage
import kr.ongil.app.ui.pages.CardPage
import kr.ongil.app.ui.pages.CoursesPage
import kr.ongil.app.ui.pages.HomePage
import kr.ongil.app.ui.pages.MyMapPage
import kr.ongil.app.ui.pages.InputPage
import kr.ongil.app.ui.pages.RecommendPage
import kr.ongil.app.ui.pages.StampsPage

private fun Screen.order() = when (this) { Screen.Home -> 0; Screen.MyMap, Screen.Courses, Screen.Stamps -> 1; Screen.Input -> 1; Screen.Recommend -> 2; Screen.Arrival -> 3; is Screen.Card -> 4 }

@Composable
fun AppRoot(store: AppStore) {
    val screen by store.screen.collectAsStateWithLifecycle()
    val uxRestricted by store.gw.ux.restricted.collectAsStateWithLifecycle()
    val saved by store.records.state.collectAsStateWithLifecycle()
    var debugOpen by remember { mutableStateOf(false) }

    BackHandler(enabled = screen != Screen.Home) { store.back() }
    AppScreen {
      // 좁은 창 시연: 앱 내용을 반 폭(850dp)에만 그린다. 실차 2윈도우에서 내비 옆에 붙었을 때 모습
      Box(if (Ongil.forceNarrow) Modifier.width(850.dp).fillMaxHeight().background(Ongil.bg) else Modifier.fillMaxSize()) {
        AnimatedContent(targetState = screen, label = "screen", transitionSpec = {
            val forward = targetState.order() >= initialState.order()
            (slideInHorizontally(initialOffsetX = { if (forward) it / 6 else -it / 6 }) + fadeIn()) togetherWith (slideOutHorizontally(targetOffsetX = { if (forward) -it / 6 else it / 6 }) + fadeOut())
        }) { s ->
            when (s) {
                Screen.Home -> HomePage(store)
                Screen.MyMap -> MyMapPage(store)
                Screen.Courses -> CoursesPage(store)
                Screen.Stamps -> StampsPage(store)
                Screen.Input -> InputPage(store)
                Screen.Recommend -> RecommendPage(store)
                Screen.Arrival -> ArrivalPage(store)
                is Screen.Card -> CardPage(store, s.cardId)
            }
        }
      }
        // 주행 중 간소 모드: 차가 CarUxRestrictions로 "주의 분산 최적화 필요"라고 할 때만. 기어만으로는 막지 않는다
        // (조작자가 동승자일 수 있고, 플랫폼이 제한 신호를 준다). 그때는 여행 상태 + 큰 버튼 둘만 남긴다
        if (uxRestricted) DrivingMode(store, saved.session)
        // 디버그 바 (오른쪽 아래 작은 점). 디버그 빌드에만. 릴리스에선 adb 인텐트(reset·seed·fast·screen)로
        if (BuildConfig.DEBUG) Box(Modifier.align(Alignment.BottomEnd).padding(6.dp)) {
            if (!debugOpen) Box(Modifier.width(26.dp).height(26.dp).background(Ongil.line, RoundedCornerShape(13.dp)).clickable { debugOpen = true })
            else Row(Modifier.background(Ongil.card2, RoundedCornerShape(12.dp)).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Dbg("배속 ${if (saved.demoFastDwell) "ON" else "OFF"}", if (saved.demoFastDwell) Ongil.paint else Ongil.muted) { store.toggleFastDwell() }
                Dbg("지난 여행 채우기") { store.seedDemoHistory() }
                Dbg("건너뛰기") { store.skipCurrent() }
                Dbg("초기화", Ongil.red) { store.resetAll() }
                Dbg("닫기") { debugOpen = false }
            }
        }
    }
}

@Composable
private fun Dbg(text: String, color: Color = Ongil.ink, onClick: () -> Unit) {
    Box(Modifier.height(44.dp).background(Ongil.card, RoundedCornerShape(8.dp)).clickable { onClick() }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) { Text(text, color = color, fontSize = 18.sp) }
}

/** 주행 제한 중 화면: 긴 목록·입력·QR 대신 지금 여행 상태와 큰 버튼 둘 */
@Composable
private fun DrivingMode(store: AppStore, session: TourSession?) {
    val next = session?.currentVisit?.let { store.catalog.spot(it.spotId) }
    Box(Modifier.fillMaxSize().background(Ongil.bg.copy(alpha = 0.97f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (next != null) "다음은 ${next.name}" else "주행 중이에요", color = Ongil.ink, fontSize = 44.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(if (next != null) "도착 즈음 알림이 떠요 · 서면 방문이 기록돼요" else "주행 중엔 내비 위 알림과 음성만 나와요", color = Ongil.muted, fontSize = 26.sp)
            Spacer(Modifier.height(36.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BigButton("내비로 가기", Modifier.width(320.dp)) { store.openNavi() }
                if (session?.lastStampedSpotId != null) BigButton("이야기 다시 듣기", Modifier.width(320.dp), primary = false) { store.replayStory() }
            }
        }
    }
}
