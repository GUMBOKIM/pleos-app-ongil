package kr.ongil.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** 칩 한 개의 높이. 칩·화살표·정렬 알약이 전부 이 높이다 */
const val CHIP_H = 56

/**
 * 칩 한 줄. 많아져도 줄바꿈하지 않고 가로로 스크롤한다. 넘치면 양 끝에 ‹ › 원 버튼이 **칩 위에 겹쳐** 떠서 한 화면씩 옮긴다(차에선 드래그보다 버튼이 편함).
 * 넘친 쪽 끝은 투명하게 깎아 칩이 칼로 잘린 듯 보이지 않게 한다.
 */
@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val sc = rememberScrollState(); val scope = rememberCoroutineScope()
    val overflow = sc.maxValue > 0
    val fade = with(LocalDensity.current) { (CHIP_H + 16).dp.toPx() }
    Box(modifier.fillMaxWidth().height(CHIP_H.dp)) {
        Row(
            Modifier.fillMaxWidth().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
                drawContent()
                if (sc.value > 0) drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = fade), blendMode = BlendMode.DstIn)
                if (sc.value < sc.maxValue) drawRect(Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - fade, endX = size.width), blendMode = BlendMode.DstIn)
            }.horizontalScroll(sc),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = content,
        )
        if (overflow && sc.value > 0) ArrowButton("‹", Modifier.align(Alignment.CenterStart)) { scope.launch { sc.animateScrollTo((sc.value - 360).coerceAtLeast(0)) } }
        if (overflow && sc.value < sc.maxValue) ArrowButton("›", Modifier.align(Alignment.CenterEnd)) { scope.launch { sc.animateScrollTo((sc.value + 360).coerceAtMost(sc.maxValue)) } }
    }
}

@Composable
private fun ArrowButton(glyph: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.size(CHIP_H.dp).background(Ongil.card2, CircleShape).border(1.dp, Ongil.line, CircleShape).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Text(glyph, color = Ongil.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 3.dp))
    }
}

/** 필터·범위·묶음 칩. 앱의 모든 칩이 이것 하나다. official이면 금색 */
@Composable
fun FilterChip(text: String, selected: Boolean, official: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape((CHIP_H / 2).dp)
    val accent = if (official) Ongil.official else Ongil.paint
    Box(
        Modifier.height(CHIP_H.dp).background(if (selected) accent.copy(alpha = 0.18f) else Ongil.card, shape).border(1.dp, if (selected) accent else Ongil.line, shape).clickable { onClick() }.padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) Ongil.ink else if (official) Ongil.official else Ongil.muted, fontSize = 22.sp, lineHeight = 22.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1) }
}
