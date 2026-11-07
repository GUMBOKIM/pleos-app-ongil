package kr.ongil.app.store

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.ongil.app.OngilApp
import kr.ongil.app.gateway.TripEstimate
import kr.ongil.app.service.TourService
import kr.ongil.core.iyeyo
import kr.ongil.core.Budget
import kr.ongil.core.Companions
import kr.ongil.core.Course
import kr.ongil.core.DayPlan
import kr.ongil.core.LatLng
import kr.ongil.core.OngilJson
import kr.ongil.core.Plan
import kr.ongil.core.Planner
import kr.ongil.core.SavedState
import kr.ongil.core.TourStatus
import kr.ongil.core.VisitStage

sealed class Screen {
    data object Home : Screen()
    data object Input : Screen()
    data object Recommend : Screen()
    data object Arrival : Screen()
    data class Card(val cardId: String?) : Screen()
    data object MyMap : Screen()      // 내 지도(히트맵 자랑 카드)
    data object Stamps : Screen()     // 스탬프: 지자체 세트 스탬프
    data object Courses : Screen()    // 공개 코스(샘플) → 내비에 꽂기
}

data class InputDraft(
    val picked: List<String> = emptyList(),   // 사용자가 직접 고른 볼 곳(고른 순서). 비어 있으면 코스를 못 만든다. 언제·누구랑·숙소는 묻지 않는다
)

data class RecommendUi(
    val days: List<DayPlan> = emptyList(),
    val estimate: TripEstimate? = null,
    val estimateState: String = "",
    val registering: Boolean = false,
    val registerResult: String? = null,
)

/** 지금 시각(그날 0시부터 분). 코스의 도착 시각은 참고용 */
private fun nowMin(): Int { val n = java.time.LocalDateTime.now(); return n.hour * 60 + n.minute }

/** 에뮬레이터에서 현재 위치를 못 받을 때 쓰는 기본 출발점(서울시청) */
private val SEOUL = LatLng(37.5665, 126.9780)

class AppStore(app: Application) : AndroidViewModel(app) {
    val app get() = getApplication<OngilApp>()
    val records get() = app.records
    val catalog get() = app.catalog
    val stamps get() = app.stamps
    val courses get() = app.courses
    val gw get() = app.gateways

    // ---------- 화면 · 지도 ----------
    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen
    private val _selected = MutableStateFlow<String?>(null)   // 지도에서 고른 시군구 코드
    val selected: StateFlow<String?> = _selected
    private val _zoom = MutableStateFlow(false)
    val zoom: StateFlow<Boolean> = _zoom
    private val _picker = MutableStateFlow(false)             // 시/군/구 고르기 패널
    val picker: StateFlow<Boolean> = _picker
    private val _pickerSido = MutableStateFlow<String?>(null)
    val pickerSido: StateFlow<String?> = _pickerSido

    // ---------- 입력(M1) ----------
    private val _draft = MutableStateFlow(InputDraft())
    val draft: StateFlow<InputDraft> = _draft

    // ---------- 추천(M2) ----------
    private val _rec = MutableStateFlow(RecommendUi())
    val rec: StateFlow<RecommendUi> = _rec
    private val _expanded = MutableStateFlow<String?>(null)     // 펼친 명소 id
    val expanded: StateFlow<String?> = _expanded

    init {
        // 첫 지역: 진행 중 여행 > 내비가 이미 아는 현재 위치의 시군구 > 데모 지역.
        // (currentLocation은 StateFlow라 생성 시점 값만 의미가 있다. 그 뒤 들어오는 위치로 지역을 바꾸진 않음)
        val s = records.state.value.session
        _selected.value = when {
            s != null && s.status != TourStatus.COMPLETED -> s.regionId
            else -> gw.navigation.currentLocation.value?.let { catalog.geometry.regionAt(it.lat, it.lng) } ?: catalog.demoRegionId
        }
        if (s != null && s.status == TourStatus.ACTIVE && s.currentVisit?.stage.let { it == VisitStage.DWELLING || it == VisitStage.IN_RADIUS }) _screen.value = Screen.Arrival
    }

    fun go(s: Screen) { _screen.value = s }
    /** 플랫폼 뒤로 가기. 각 화면의 ← 와 같은 목적지. 홈에서는 false(앱이 뒤로 빠짐) */
    fun back(): Boolean {
        when (_screen.value) {
            Screen.Home -> return false
            Screen.Recommend -> go(Screen.Input)
            is Screen.Card -> go(Screen.MyMap)
            else -> go(Screen.Home)
        }
        return true
    }
    fun openFromNotification(screen: String?) {
        when (screen) {
            "arrival" -> _screen.value = Screen.Arrival
            "home" -> _screen.value = Screen.Home
            "card" -> _screen.value = Screen.Card(null)   // 시연용: 최근 기록 열기
        }
    }

    /** 지도 탭: 같은 지역을 다시 누르면 줌 토글, 다른 지역이면 그 지역으로 확대 */
    fun select(code: String?) {
        if (code == null) return
        if (_selected.value == code) _zoom.update { !it } else { _selected.value = code; _zoom.value = true }
    }
    fun zoomOut() { _zoom.value = false }
    fun pickRegion(code: String) { _selected.value = code; _zoom.value = true; _picker.value = false }
    fun openPicker(open: Boolean) {
        _picker.value = open
        if (open) _pickerSido.value = _selected.value?.let { catalog.region(it)?.sido } ?: _pickerSido.value ?: catalog.regions.first().sido
    }
    fun setPickerSido(s: String) { _pickerSido.value = s }

    /** 지금 위치. 방문 판정과 같은 FusedLocation을 먼저 본다(내비 위치는 GPS 재생을 못 받는 경우가 있어 뒤로) */
    val currentLocation: LatLng? get() = gw.position.fix.value?.let { LatLng(it.lat, it.lng) } ?: gw.navigation.currentLocation.value

    // ---------- M1 ----------
    /** 입력 화면으로. prefill = 다른 사람 코스·세트에서 골라 온 볼 곳(사용자가 입력 화면에서 더 고치고 코스를 만든다) */
    fun startInput(prefill: List<String> = emptyList()) {
        val code = _selected.value ?: return
        if (catalog.catalog(code) == null) return
        _draft.value = InputDraft(picked = prefill.distinct())
        _screen.value = Screen.Input
    }
    /** 볼 곳 고르기: 누르면 넣고 다시 누르면 뺀다 */
    fun togglePick(id: String) = _draft.update { d -> d.copy(picked = if (id in d.picked) d.picked - id else d.picked + id) }
    fun clearPicks() = _draft.update { it.copy(picked = emptyList()) }
    /** 코스 화면 "다시 짜기": 지금 코스의 명소를 고른 채로 입력 화면으로 */
    fun editCourse() { _draft.value = InputDraft(picked = _rec.value.days.flatMap { it.spotIds }); _screen.value = Screen.Input }

    // ---------- M2 ----------
    fun recommend() {
        val code = _selected.value ?: return
        val cat = catalog.catalog(code) ?: return
        val d = _draft.value
        if (d.picked.isEmpty()) return
        val origin = currentLocation ?: SEOUL
        val now = System.currentTimeMillis()
        val days = listOf(Planner.checklist(cat, d.picked, origin, nowMin()))
        val plan = Plan("plan-$now", code, Budget.DAY, Companions.COUPLE, 2, null, false, origin, days, "car", now)
        records.update { it.copy(plan = plan, session = null) }
        _rec.value = RecommendUi(days = days)
        _expanded.value = null
        _screen.value = Screen.Recommend
        estimateFirst()
    }

    private fun estimateFirst() {
        val plan = records.state.value.plan ?: return
        val first = (_rec.value.days.firstOrNull() ?: plan.days.firstOrNull())?.stops?.firstOrNull()?.let { catalog.spot(it.spotId) } ?: return
        _rec.update { it.copy(estimateState = "내비에 도착 예측 요청 중") }
        viewModelScope.launch {
            val e = gw.navigation.estimate(first.latLng, first.name)
            _rec.update { it.copy(estimate = e, estimateState = if (e == null) "출발하면 내비가 알려줘요" else "") }
        }
    }

    fun toggleExpanded(id: String) = _expanded.update { if (it == id) null else id }

    /** 빼기: 선택 목록을 다시 짠다 */
    fun removeSpot(id: String) = rebuildWith(currentSpotIds() - id)
    fun coursesOf(regionId: String, st: SavedState = records.state.value): List<Course> = courses.all(st).filter { it.regionId == regionId }
    private fun currentSpotIds(): List<String> = _rec.value.days.flatMap { it.spotIds }
    private fun rebuildWith(ids: List<String>) {
        val plan = records.state.value.plan ?: return
        val cat = catalog.catalog(plan.regionId) ?: return
        val cur = _rec.value.days.firstOrNull()
        val day = cur?.day ?: 1
        val from = (if (day == 1) plan.origin else currentLocation) ?: plan.origin ?: SEOUL
        val rebuilt = Planner.checklist(cat, ids, from, cur?.startMin ?: nowMin(), day)
        records.update { it.copy(plan = plan.copy(days = plan.days.filter { it.day != day } + rebuilt)) }
        _rec.update { it.copy(days = listOf(rebuilt)) }
    }

    fun registerAndGo() {
        val plan = records.state.value.plan ?: return
        val cat = catalog.catalog(plan.regionId) ?: return
        _rec.update { it.copy(registering = true, registerResult = null) }
        viewModelScope.launch {
            val dp = _rec.value.days.firstOrNull() ?: plan.days.first()
            TourService.startDay(app, dp.day)
            val seg = Planner.segments(dp, cat, plan.lodging).firstOrNull() ?: emptyList()
            val r = gw.navigation.registerRoute(seg)
            _rec.update { it.copy(registering = false, registerResult = r.fold({ "내비에 들어갔어요 · ${seg.size}곳 · $it" }, { "내비 연결 실패: ${it.message}" })) }
            if (r.isSuccess) gw.navigation.openNavigation()
        }
    }

    // ---------- 내 지도 · 공개 코스 ----------
    private val _course = MutableStateFlow<String?>(null)     // 공개 코스 화면에서 고른 코스
    val course: StateFlow<String?> = _course
    fun selectCourse(id: String) { _course.value = id }

    fun allCourses(st: SavedState = records.state.value): List<Course> = courses.all(st)
    fun refreshCourses() = viewModelScope.launch { courses.refresh() }
    fun shareCard(cardId: String) = viewModelScope.launch { courses.share(cardId) }

    // ---------- 여행 중(M4·M5) ----------
    fun openNavi() = gw.navigation.openNavigation()
    fun continueDay(day: Int) { TourService.startDay(app, day); viewModelScope.launch { delay(500); TourService.nextSegment(app); gw.navigation.openNavigation() } }
    fun skipCurrent() = TourService.skip(app)
    /** 오늘은 여기까지. 못 간 곳이 있으면 다음 회차(day+1) 코스로 남겨 두고 홈에서 "이어가기"로 꺼낸다. 카드는 안 띄움(기록은 내 지도 → 지난 여행) */
    fun endDay() {
        val st = records.state.value; val s = st.session; val plan = st.plan
        if (s != null && plan != null && s.status == TourStatus.ACTIVE) {
            val left = s.visits.filter { it.stage != VisitStage.STAMPED }.map { it.spotId }
            val cat = catalog.catalog(plan.regionId)
            if (left.isNotEmpty() && cat != null) {
                val from = currentLocation ?: plan.origin ?: SEOUL
                val next = Planner.checklist(cat, left, from, 9 * 60, day = s.day + 1)
                records.update { it.copy(plan = plan.copy(days = plan.days.filter { d -> d.day <= s.day } + next)) }
            }
        }
        TourService.stop(app); _screen.value = Screen.Home
    }
    /** 남은 곳 코스 보기(이어가기 전에 빼고 넣기) */
    fun viewRemaining() {
        val st = records.state.value; val s = st.session ?: return; val plan = st.plan ?: return
        val dp = plan.days.firstOrNull { it.day == s.day + 1 } ?: return
        _rec.value = RecommendUi(days = listOf(dp)); _expanded.value = null
        _screen.value = Screen.Recommend
        estimateFirst()
    }
    /** 남은 곳 수(이어가기 버튼용) */
    fun remainingCount(): Int { val st = records.state.value; val s = st.session ?: return 0; return st.plan?.days?.firstOrNull { it.day == s.day + 1 }?.stops?.size ?: 0 }
    fun replayStory() { val s = records.state.value.session ?: return; val id = s.lastStampedSpotId ?: s.currentVisit?.spotId ?: return; val sp = catalog.spot(id) ?: return; gw.narrator.speak("${sp.name.iyeyo()}. " + sp.story) }
    fun stopStory() = gw.narrator.stop()

    // ---------- 디버그 ----------
    fun toggleFastDwell() = records.update { it.copy(demoFastDwell = !it.demoFastDwell) }
    fun setFastDwell(on: Boolean) = records.update { it.copy(demoFastDwell = on) }
    /** 시연용 '지난 여행' 기록(assets/demo-state.json, tools/demo/make_demo_state.py 생성)을 넣는다. 충주는 비어 있음 */
    fun seedDemoHistory() {
        val seed = runCatching { OngilJson.decodeFromString(SavedState.serializer(), app.assets.open("demo-state.json").bufferedReader().readText()) }.getOrNull() ?: return
        records.update { st -> st.copy(
            stamps = seed.stamps + st.stamps.filter { s -> seed.stamps.none { it.spotId == s.spotId && it.planId == s.planId } },
            cards = seed.cards + st.cards.filter { c -> seed.cards.none { it.id == c.id } },
            visits = seed.visits + st.visits.filter { v -> seed.visits.none { it.regionId == v.regionId && it.planId == v.planId } },
        ) }
    }
    /** 도착 화면 머리줄: 그 명소가 든 세트가 있으면 그 이름 */
    fun stampTitleFor(regionId: String, spotId: String?): String? = (spotId?.let { id -> stamps.setsWith(id).firstOrNull { it.regionId == regionId } } ?: stamps.setOf(regionId))?.title
    fun resetAll() { TourService.stop(app); records.reset(); _rec.value = RecommendUi(); _screen.value = Screen.Home }
}
