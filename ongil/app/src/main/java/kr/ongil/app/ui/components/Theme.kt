package kr.ongil.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.ongil.app.R

/**
 * Pleos Connect UI Design Guide 다크 토큰(바탕·글자·버튼) + 온길 의미색(칠·인주).
 * 서체 Asta Sans(OFL). 전환 120ms(눌림)/200ms(선택). 최소 터치 48dp, 버튼 Large 100 · radius 32.
 */
/** 색 한 벌. Pleos Connect 가이드의 다크 토큰이 기본, 라이트는 같은 역할로 맞춘 것 */
private class Palette(
    val bg: Color, val bg2: Color, val card: Color, val card2: Color, val line: Color,
    val ink: Color, val ink2: Color, val muted: Color, val faint: Color,
    val filled: Color, val filledPressed: Color, val onFilled: Color, val basic: Color, val basicPressed: Color,
    val paint: Color, val paintSoft: Color, val none: Color,
)
private val DARK = Palette(   // 온길 디자인 시스템 v1(11/7): 청록 강조, 밤 바탕은 청록기 도는 검정
    bg = Color(0xFF161D1C), bg2 = Color(0xFF0F1514), card = Color(0xFF233231), card2 = Color(0xFF2F4442), line = Color(0x1FFFFFFF),
    ink = Color(0xFFF2F7F6), ink2 = Color(0xD6F2F7F6), muted = Color(0xB3F2F7F6), faint = Color(0x80F2F7F6),
    filled = Color(0xFF3FCBB8), filledPressed = Color(0xFF2DB3A1), onFilled = Color(0xFF0B2A27), basic = Color(0x1A3FCBB8), basicPressed = Color(0x333FCBB8),
    paint = Color(0xFF3FCBB8), paintSoft = Color(0xFF1F5F57), none = Color(0xFF263231),
)
private val LIGHT = Palette(  // 낮: 청록기 도는 흰 바탕, 카드는 옅은 청록, 버튼은 청록
    bg = Color(0xFFF3F9F8), bg2 = Color(0xFFFFFFFF), card = Color(0xFFE4F0EE), card2 = Color(0xFFD0E4E1), line = Color(0x1F0E4A44),
    ink = Color(0xFF10201E), ink2 = Color(0xD610201E), muted = Color(0xA610201E), faint = Color(0x9910201E),
    filled = Color(0xFF0E9A8A), filledPressed = Color(0xFF0B7A6D), onFilled = Color(0xFFFFFFFF), basic = Color(0x140E9A8A), basicPressed = Color(0x290E9A8A),
    paint = Color(0xFF0E9A8A), paintSoft = Color(0xFFBFE8E0), none = Color(0xFFE4EBEA),
)

/**
 * Pleos Connect UI Design Guide 토큰(바탕·글자·버튼) + 온길 의미색(칠·인주). 시스템 테마(낮/밤)를 따라 다크·라이트가 바뀐다.
 * 서체 Asta Sans(OFL). 전환 120ms(눌림)/200ms(선택). 최소 터치 48dp, 버튼 Large 100 · radius 32.
 */
object Ongil {
    var dark by mutableStateOf(true)
    private val p: Palette get() = if (dark) DARK else LIGHT
    // surface (Pleos)
    val bg get() = p.bg            // background_primary · 패널
    val bg2 get() = p.bg2          // background_secondary · 바닥
    val card get() = p.card        // surface_high
    val card2 get() = p.card2      // surface_accent
    val line get() = p.line        // 머리선
    // text (Pleos)
    val ink get() = p.ink
    val ink2 get() = p.ink2
    val muted get() = p.muted
    val faint get() = p.faint
    // buttons (Pleos)
    val filled get() = p.filled
    val filledPressed get() = p.filledPressed
    val onFilled get() = p.onFilled
    val basic get() = p.basic
    val basicPressed get() = p.basicPressed
    // informative (Pleos)
    val blue = Color(0xFF0082FF)      // 현재 위치 · 경로에만
    val done get() = if (dark) Color(0xFF5FDCCB) else Color(0xFF0B7A6D)   // "방문이 기록됐어요" 같은 확정 문구(청록 잉크)
    val red get() = if (dark) Color(0xFFFF7A6B) else Color(0xFFD23B2B)
    // 온길 의미색
    val paint get() = p.paint         // 칠: 자주 간 땅, 진행
    val paintSoft get() = p.paintSoft // 칠: 한 번 지난 땅
    val none get() = p.none           // 지도: 아직 안 선 땅
    val stamp get() = if (dark) Color(0xFFFF7A5C) else Color(0xFFE8553A)   // 인주: 방문 순간
    val official get() = if (dark) Color(0xFFF2C06A) else Color(0xFF9A6508)   // 금색: 지자체가 발행한 것(스탬프 세트·공식 코스). 초록은 청록과 헷갈려 뺌

    @OptIn(ExperimentalTextApi::class)
    val asta = FontFamily(
        Font(R.font.asta_sans_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.asta_sans_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.asta_sans_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
        Font(R.font.asta_sans_variable, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
    )
    val titleL = TextStyle(fontFamily = asta, fontSize = 40.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold)
    val titleM = TextStyle(fontFamily = asta, fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold)
    val labelM = TextStyle(fontFamily = asta, fontSize = 24.sp, lineHeight = 28.sp)
    val rLarge = 32.dp; val rMedium = 24.dp; val rSmall = 16.dp
    val rail = 560.dp
    val narrowBelow = 1100.dp   // 앱 창이 이보다 좁으면(2윈도우 반 화면) 지도 없이 레일만
    /** 시연용(디버그 빌드, 인텐트 `--ez narrow true`): 에뮬레이터는 창이 하나라 좁은 창을 흉내 낸다 */
    var forceNarrow by mutableStateOf(false)
    val railPad = PaddingValues(start = 36.dp, end = 36.dp, top = 28.dp, bottom = 28.dp)
    const val buttonLarge = 100       // Pleos Button Large. 이 높이 이상이면 radius 32 · 라벨 32
    const val buttonMedium = 84       // 보조 버튼(같은 줄에 주 버튼과 섞지 않는다. 한 줄의 버튼은 높이를 맞춘다)
    const val buttonSmall = 72        // 목록 안 작은 행동 버튼(추가하기 등)
}

@Composable
fun OngilTheme(content: @Composable () -> Unit) {
    Ongil.dark = isSystemInDarkTheme()   // 차 시스템 테마(낮/밤)를 그대로 따른다
    val base = TextStyle(fontFamily = Ongil.asta, fontSize = 28.sp, lineHeight = TextUnit.Unspecified, color = Ongil.ink)
    MaterialTheme(
        colorScheme = (if (Ongil.dark) darkColorScheme(primary = Ongil.paint, background = Ongil.bg2, surface = Ongil.bg, onBackground = Ongil.ink, onSurface = Ongil.ink) else lightColorScheme(primary = Ongil.paint, background = Ongil.bg2, surface = Ongil.bg, onBackground = Ongil.ink, onSurface = Ongil.ink)),
        typography = Typography(bodyLarge = base, bodyMedium = base, bodySmall = base, titleLarge = Ongil.titleL, titleMedium = Ongil.titleM, labelLarge = Ongil.labelM),
        content = content,
    )
}

/** 화면 바닥. 바닥은 bg2, 패널은 bg */
@Composable
fun AppScreen(content: @Composable BoxScope.() -> Unit) { Box(Modifier.fillMaxSize().background(Ongil.bg2)) { content() } }

/** 왼쪽 무대 + 오른쪽 레일. 모든 화면이 같은 뼈대 */
@Composable
fun Stage(modifier: Modifier = Modifier, stage: @Composable BoxScope.() -> Unit, rail: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        // 실차가 2윈도우(내비 옆에 온길)로 앱 창을 좁게 주면 지도를 접고 레일만 꽉 채운다. 에뮬레이터는 창이 하나라 항상 넓다
        if (maxWidth < Ongil.narrowBelow || Ongil.forceNarrow) CompositionLocalProvider(LocalNarrow provides true) { Column(Modifier.fillMaxSize().background(Ongil.bg).padding(Ongil.railPad)) { rail() } }
        else Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) { stage() }
            Column(Modifier.width(Ongil.rail).fillMaxHeight().background(Ongil.bg).padding(Ongil.railPad)) { rail() }
        }
    }
}

/** 지금 레일이 좁은 창(지도 없음)에 그려지는 중인지. 무대에만 있던 제목을 레일이 대신 보여줄 때 쓴다 */
val LocalNarrow = compositionLocalOf { false }

/** 레일 머리: 뒤로 + 작은 라벨 + 큰 제목 */
@Composable
fun RailHead(title: String, over: String? = null, onBack: (() -> Unit)? = null, size: Int = 48, right: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.Top) {
        if (onBack != null) { BackButton(onBack); Spacer(Modifier.width(18.dp)) }
        Column(Modifier.weight(1f)) {
            if (over != null) Text(over, color = Ongil.muted, fontSize = 22.sp)
            Text(title, color = Ongil.ink, fontSize = size.sp, lineHeight = (size + 8).sp, fontWeight = FontWeight.ExtraBold)
        }
        right()
    }
}

@Composable
fun BackButton(onClick: () -> Unit) {
    Box(Modifier.size(80.dp).background(Ongil.basic, CircleShape).clickable { onClick() }, contentAlignment = Alignment.Center) { Text("←", color = Ongil.ink, fontSize = 34.sp) }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) { Box(modifier.fillMaxWidth().height(1.dp).background(Ongil.line)) }

/** 큰 숫자 + 작은 라벨 */
@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier, color: Color = Ongil.ink, size: Int = 44) {
    Column(modifier) {
        Text(value, color = color, fontSize = size.sp, lineHeight = (size + 4).sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = Ongil.muted, fontSize = 20.sp)
    }
}

/** 명소 점 N개로 보여주는 진행도. 칠해진 점 = 방문 */
@Composable
fun DotRow(total: Int, have: Int, modifier: Modifier = Modifier, dot: Int = 18, activeIndex: Int = -1) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total) { i ->
            val c by animateColorAsState(if (i < have) Ongil.paint else if (i == activeIndex) Ongil.ink else Ongil.card2, tween(300), label = "d$i")
            Box(Modifier.size(dot.dp).background(c, CircleShape).let { if (i < have || i == activeIndex) it else it.border(1.dp, Ongil.faint, CircleShape) })
        }
    }
}

/** Pleos Filled 버튼: Large 100 · radius 32 · 라벨 32 Bold. primary=false면 Basic(white 5%) */
@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, primary: Boolean = true, height: Int = Ongil.buttonLarge, sub: String? = null, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val bg by animateColorAsState(when { !enabled -> Ongil.basic; primary -> if (pressed) Ongil.filledPressed else Ongil.filled; else -> if (pressed) Ongil.basicPressed else Ongil.basic }, tween(120), label = "bg")
    val fg = if (!enabled) Ongil.muted else if (primary) Ongil.onFilled else Ongil.ink
    val large = height >= 96
    val sc by animateFloatAsState(if (pressed && enabled) 0.98f else 1f, tween(120), label = "sc")
    Column(modifier.scale(sc).height(height.dp).background(bg, RoundedCornerShape(if (large) Ongil.rLarge else Ongil.rMedium)).clickable(interactionSource = src, indication = null, enabled = enabled) { onClick() }.padding(horizontal = 28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = fg, fontSize = if (large) 32.sp else 26.sp, fontWeight = FontWeight.Bold)
        if (sub != null) Text(sub, color = if (primary && enabled) fg else fg.copy(alpha = 0.8f), fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 고르는 칩(탭): 선택되면 칠 색 테두리 + 글자 */
@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, sub: String? = null, onClick: () -> Unit) {
    val height = if (sub == null) 88 else 100
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.97f else 1f, tween(120), label = "p")
    val bg by animateColorAsState(if (selected) Ongil.paint.copy(alpha = 0.16f) else if (pressed) Ongil.card2 else Ongil.card, tween(200), label = "bg")
    val border by animateColorAsState(if (selected) Ongil.paint else Color.Transparent, tween(200), label = "bd")
    val shape = RoundedCornerShape(Ongil.rMedium)
    Column(modifier.scale(sc).height(height.dp).background(bg, shape).border(2.dp, border, shape).clickable(interactionSource = src, indication = null) { onClick() }.padding(horizontal = 20.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = if (selected) Ongil.paint else Ongil.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub != null) Text(sub, color = Ongil.muted, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Tag(text: String, color: Color = Ongil.muted, modifier: Modifier = Modifier) {
    Box(modifier.background(color.copy(alpha = 0.16f).compositeOver(Ongil.bg2), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 5.dp)) { Text(text, color = color, fontSize = 20.sp, fontWeight = FontWeight.Medium) }
}
