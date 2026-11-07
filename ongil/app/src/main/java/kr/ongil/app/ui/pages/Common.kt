package kr.ongil.app.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Date
import java.text.SimpleDateFormat
import kr.ongil.core.Stamps
import kr.ongil.core.StampSet
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.ongil.app.store.AppStore
import kr.ongil.app.ui.components.Ongil
import kr.ongil.core.Achievements
import kr.ongil.core.Heat
import kr.ongil.core.SavedState
import java.util.Locale

// ---------- 무대(왼쪽 지도) 위 글자 자리. 다섯 화면이 같은 여백 ----------

/** 무대 왼쪽 위 제목 블록 */
internal fun Modifier.stageTop(): Modifier = padding(start = 40.dp, top = 32.dp)

/** 무대 왼쪽 아래 보조 블록 */
internal fun BoxScope.stageBottomStart(): Modifier = Modifier.align(Alignment.BottomStart).padding(start = 40.dp, bottom = 32.dp)

// ---------- 데이터 도우미 ----------

/** 그 지역에서 방문이 기록된 명소 id */
internal fun SavedState.stampedIn(regionId: String?): Set<String> =
    if (regionId == null) emptySet() else stamps.filter { it.regionId == regionId }.map { it.spotId }.toSet()

/** 전국 히트맵: 시군구별 방문한 명소 수. 기록이 바뀔 때만 다시 센다 */
@Composable
internal fun rememberHeat(saved: SavedState): Map<String, Float> = remember(saved.stamps) { Heat.regionLevels(saved.stamps) }

/** 스탬프 받은 지역 코드(그 지역 세트의 명소에 모두 선 곳) */
@Composable
internal fun rememberDone(store: AppStore, saved: SavedState): Set<String> = remember(saved.stamps, store.catalog.regions) { store.stamps.earnedRegions(saved.stamps) }

/** 세트 스탬프를 받은 날짜("26.05.04"), 아직이면 null */
@Composable
internal fun earnedDate(store: AppStore, set: StampSet): String? {
    val saved by store.records.state.collectAsStateWithLifecycle()
    return remember(saved.stamps, set.id) { Stamps.earnedAt(set, saved.stamps)?.let { SimpleDateFormat("yy.MM.dd", Locale.KOREA).format(Date(it)) } }
}

/** 도전과제 이름. "stamp:<세트>"·구 "region-complete"는 세트 제목으로 */
internal fun achievementLabel(store: AppStore, id: String, regionId: String?): String = when {
    id == Achievements.REGION_COMPLETE -> (regionId?.let { store.stamps.setOf(it)?.title } ?: "지역") + " 스탬프"
    id.startsWith(Achievements.STAMP_PREFIX) -> (store.stamps.set(id.removePrefix(Achievements.STAMP_PREFIX))?.title ?: "지역") + " 스탬프"
    else -> id
}

// ---------- 시각 ----------

/** 60진 한 단계: 초→"m:ss", 분→"h:mm" */
internal fun mmss(total: Int): String = "%d:%02d".format(total / 60, total % 60)

// ---------- 공용 조각 ----------

/** 둥근 알약 버튼(무대 위 '전국 보기'·'시·군·구 고르기', 레일 머리 '지역 바꾸기'·'닫기') */
@Composable
internal fun Pill(text: String, modifier: Modifier = Modifier, height: Int = 80, bg: Color = Ongil.bg, bordered: Boolean = true, padH: Int = 30, fontSize: Int = 26, weight: FontWeight? = FontWeight.Medium, onClick: () -> Unit) {
    val shape = RoundedCornerShape((height / 2).dp)
    Box(
        modifier.height(height.dp).background(bg, shape).let { if (bordered) it.border(1.dp, Ongil.line, shape) else it }.clickable { onClick() }.padding(horizontal = padH.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Ongil.ink, fontSize = fontSize.sp, fontWeight = weight) }
}

/** 명소 한 줄: 점 · 이름 · 오른쪽 짧은 글자. 홈 지역 카드·시군구 목록·그날 카드가 같은 꼴 */
@Composable
internal fun SpotLine(name: String, modifier: Modifier = Modifier, height: Int = 70, dot: Int = 14, gap: Int = 16, dotColor: Color = Ongil.paint, nameColor: Color = Ongil.ink, right: String? = null, rightColor: Color = Ongil.muted) {
    Row(modifier.fillMaxWidth().height(height.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(dot.dp).background(dotColor, CircleShape))
        Spacer(Modifier.width(gap.dp))
        Text(name, color = nameColor, fontSize = 30.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        if (right != null) Text(right, color = rightColor, fontSize = 22.sp)
    }
}
