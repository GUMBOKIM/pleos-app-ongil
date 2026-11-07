package kr.ongil.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kr.ongil.core.Spot
import kr.ongil.core.StampSet

/**
 * 명소 설명 다이얼로그. 지역 카드에서 명소 줄(배지가 아닌 곳)을 누르면 연다.
 * 다이얼로그로 한 이유: 스탬프 다이얼로그와 같은 결(지도 위에 잠깐 띄우고 닫기), 화면 이동이 없어 차에서 길을 잃지 않는다. 설명은 30초 분량이라 한 장에 들어간다.
 * sets = (세트, 방문 수, 받았는지). 배지를 누르면 그 스탬프를 크게.
 */
@Composable
fun SpotDialog(spot: Spot, regionName: String, sido: String, visits: Int, sets: List<Triple<StampSet, Int, Boolean>>, onStamp: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(760.dp).background(Ongil.bg, RoundedCornerShape(Ongil.rLarge)).padding(36.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("$sido $regionName", color = Ongil.muted, fontSize = 22.sp)
                    Text(spot.name, color = Ongil.ink, fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.ExtraBold)
                }
                if (sets.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    sets.forEach { (set, n, earned) -> StampArt(set, regionName, sido, earned, size = 56.dp, mini = true, tilt = false, fraction = n.toFloat() / set.spotIds.size.coerceAtLeast(1)) { onStamp(set.id) } }
                }
            }
            Spacer(Modifier.height(8.dp))
            // 어떤 곳인지 한 줄: 많이 알려진 곳인지, 내려서 얼마나 걷는지, 얼마나 머무는지
            Text(listOfNotNull(
                if (spot.famous) "많이 찾는 곳" else "덜 알려진 곳",
                if (spot.getOff) "주차 후 ${spot.walkM}m 걸어요" else "차에서 보여요",
                "${spot.stayMin}분쯤 머물러요",
                if (visits > 0) "${visits}번 방문" else null,
            ).joinToString(" · "), color = if (visits > 0) Ongil.done else Ongil.muted, fontSize = 22.sp)
            if (sets.isNotEmpty()) Text(sets.joinToString(" · ") { it.first.title } + "에 들어 있어요", color = Ongil.official, fontSize = 20.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(16.dp))
            Hairline()
            Spacer(Modifier.height(16.dp))
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(spot.story.ifBlank { "소개글을 준비하고 있어요." }, color = Ongil.ink2, fontSize = 26.sp, lineHeight = 40.sp)
            }
            Spacer(Modifier.height(20.dp))
            BigButton("닫기", Modifier.fillMaxWidth(), primary = false, height = Ongil.buttonMedium) { onDismiss() }
        }
    }
}
