package kr.ongil.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kr.ongil.app.OngilApp
import kr.ongil.app.gateway.Fix
import kr.ongil.app.gateway.VehicleSnapshot
import kr.ongil.app.notify.Notifier
import kr.ongil.core.iyeyo
import kr.ongil.core.Achievements
import kr.ongil.core.DayCard
import kr.ongil.core.Geo
import kr.ongil.core.Planner
import kr.ongil.core.RegionVisit
import kr.ongil.core.SavedState
import kr.ongil.core.Stamps
import kr.ongil.core.Spot
import kr.ongil.core.Stamp
import kr.ongil.core.TourSession
import kr.ongil.core.TourStatus
import kr.ongil.core.VisitJudge
import kr.ongil.core.VisitStage
import kr.ongil.core.VisitState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 여행 중 유일하게 깨어 있는 것. 위치·기어를 보고 방문 상태를 굴리고 스탬프를 저장한다.
 * 화면은 Store의 Flow만 구독한다. 화면이 죽어도 여기는 산다.
 *
 * 한 틱 = 거리 누적 → (1km 근접 보정) → 반경·정차·체류 판정(core VisitJudge) → 스탬프 → 알림·TTS.
 * 상태 변경은 records.update 한 번에 묶고, 알림·TTS 같은 부수효과는 그 밖에서 쏜다.
 */
class TourService : Service() {
    companion object {
        const val ACTION_START = "kr.ongil.app.START_DAY"
        const val ACTION_STOP = "kr.ongil.app.STOP"
        const val ACTION_SKIP = "kr.ongil.app.SKIP_CURRENT"
        const val ACTION_NEXT_SEGMENT = "kr.ongil.app.NEXT_SEGMENT"
        const val EXTRA_DAY = "day"
        /** 근접 알림 반경. 여기 들어오면 헤드업 + TTS 1회 */
        const val APPROACH_M = 1000f
        private const val REGION_RECHECK_M = 200.0
        private const val TAG = "ONGIL_SVC"
        /** 위치·기어 콜백이 안 와도 정차 타이머는 돌아야 하므로 주기 틱 */
        private const val TICK_MS = 5_000L
        /** 시연 배속(demoFastDwell): 정차 3분 → 6초 */
        private const val FAST_DWELL_DIVISOR = 30
        private const val STORY_DELAY_MS = 800L
        /** 거리 누적에서 뺄 것: 3m 미만은 GPS 떨림, 2km 이상 점프는 좌표 재생 순간이동 */
        private const val MIN_STEP_M = 3.0
        private const val MAX_JUMP_M = 2000.0

        fun startDay(ctx: Context, day: Int) {
            val i = Intent(ctx, TourService::class.java).setAction(ACTION_START).putExtra(EXTRA_DAY, day)
            ctx.startForegroundService(i)
        }
        fun stop(ctx: Context) = ctx.startService(Intent(ctx, TourService::class.java).setAction(ACTION_STOP))
        fun skip(ctx: Context) = ctx.startService(Intent(ctx, TourService::class.java).setAction(ACTION_SKIP))
        fun nextSegment(ctx: Context) = ctx.startService(Intent(ctx, TourService::class.java).setAction(ACTION_NEXT_SEGMENT))
    }

    private val app get() = application as OngilApp
    private val records get() = app.records
    private val catalog get() = app.catalog
    private val stamps get() = app.stamps
    private val gw get() = app.gateways
    private val notifier get() = app.notifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watch: Job? = null    // 위치 × 차량 신호 결합 구독
    private var ticker: Job? = null   // 주기 틱
    private var storyJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startDay(intent.getIntExtra(EXTRA_DAY, 1))
            ACTION_STOP -> { finishDay(); stopSelf() }
            ACTION_SKIP -> skipCurrent()
            ACTION_NEXT_SEGMENT -> scope.launch { registerCurrentSegment() }
            // 프로세스가 죽었다 살아난 경우(OngilApp / START_STICKY)
            else -> if (records.state.value.session?.status == TourStatus.ACTIVE) resume() else stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() { stopWatching(); scope.cancel(); super.onDestroy() }

    // ───────── 시작·재개·종료 ─────────

    private fun goForeground(text: String) {
        val n = notifier.ongoing(text)
        if (Build.VERSION.SDK_INT >= 29) startForeground(Notifier.ID_TOUR, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(Notifier.ID_TOUR, n)
    }

    private fun remainingText(s: TourSession) = "${s.visits.count { it.stage != VisitStage.STAMPED && it.stage != VisitStage.PASSED }}곳 남음"

    private fun startDay(day: Int) {
        val st = records.state.value
        val plan = st.plan ?: run { stopSelf(); return }
        val dp = plan.days.firstOrNull { it.day == day } ?: run { stopSelf(); return }
        // 전에 섰던 곳도 오늘 다시 선다(재방문도 기록, 히트맵이 진해짐). 같은 여행 안에서만 한 번
        val visits = dp.spotIds.map { id -> VisitState(id, VisitStage.PENDING) }
        records.update { it.copy(session = TourSession(plan.id, plan.regionId, day, TourStatus.ACTIVE, visits, 0, System.currentTimeMillis(), isSimulation = app.isEmulator)) }
        resume()
    }

    private fun resume() {
        val s = records.state.value.session ?: return
        goForeground(remainingText(s))
        gw.position.start(); gw.vehicle.start()
        registerApproachAlerts(s)
        stopWatching()
        watch = scope.launch {
            combine(gw.position.fix, gw.vehicle.snapshot) { f, v -> f to v }.collect { (fix, veh) -> tick(fix, veh) }
        }
        ticker = scope.launch { while (true) { delay(TICK_MS); tick(gw.position.fix.value, gw.vehicle.snapshot.value) } }
    }

    private fun stopWatching() { watch?.cancel(); ticker?.cancel(); watch = null; ticker = null }

    /** 사용자가 끝냄(ACTION_STOP): 카드 만들고 감시·알림 전부 내림 */
    private fun finishDay() { finishDayInternal(); stopWatching(); gw.position.clearProximity(); notifier.cancelAll(); stopForeground(STOP_FOREGROUND_REMOVE) }

    /** 오늘 코스 끝: 그날 카드를 만들고 세션을 DAY_DONE/COMPLETED로. 이미 끝났으면 아무 일 없음 */
    private fun finishDayInternal() {
        var finished = false
        records.update { st ->
            val s = st.session ?: return@update st
            if (s.status != TourStatus.ACTIVE) return@update st
            finished = true
            val card = buildDayCard(st, s)
            val lastDay = st.plan?.days?.maxOf { it.day } ?: s.day
            st.copy(session = s.copy(status = if (s.day >= lastDay) TourStatus.COMPLETED else TourStatus.DAY_DONE), cards = st.cards.filter { it.id != card.id } + card)
        }
        if (!finished) return
        stopWatching(); gw.position.clearProximity()
        notifier.updateOngoing("오늘 코스 끝 · 지도에 칠해졌어요")
    }

    private fun buildDayCard(st: SavedState, s: TourSession): DayCard {
        val after = stampedCount(st, s.regionId); val total = catalog.spotsOf(s.regionId).size
        val todayStamps = st.stamps.filter { it.planId == s.planId && it.at >= s.startedAt }
        return DayCard(
            id = "${s.planId}-d${s.day}", planId = s.planId, day = s.day,
            date = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(Date(s.startedAt)),
            regionId = s.regionId,
            stampedSpotIds = s.visits.filter { it.stage == VisitStage.STAMPED && todayStamps.any { t -> t.spotId == it.spotId } }.map { it.spotId },
            skippedSpotIds = s.visits.filter { it.stage == VisitStage.PASSED }.map { it.spotId },
            distanceKm = if (s.distanceM > 0) Math.round(s.distanceM / 100.0) / 10.0 else null,
            durationMin = ((System.currentTimeMillis() - s.startedAt) / 60000).toInt(),
            socStart = s.socStart, socEnd = s.socLast,
            achievements = todayStamps.flatMap { it.achievements }.distinct(),
            isSimulation = s.isSimulation,
            regionCompleted = stamps.earnedSets(s.regionId, st.stamps).isNotEmpty(),
            progressBefore = (after - todayStamps.size).coerceAtLeast(0), progressAfter = after, progressTotal = total,
            title = (catalog.region(s.regionId)?.name ?: "") + if (s.day > 1) " · 이어서" else "",
        )
    }

    // ───────── 1km 근접 ─────────

    private fun registerApproachAlerts(s: TourSession) {
        gw.position.clearProximity()
        s.visits.filter { it.stage == VisitStage.PENDING }.forEach { v ->
            val sp = catalog.spot(v.spotId) ?: return@forEach
            gw.position.addProximity(v.spotId, sp.lat, sp.lng, APPROACH_M) { id -> onApproach(id) }
        }
    }

    /** SDK 근접 알림(ENTER)과 틱의 거리 보정 어느 쪽이 먼저 와도 approachNotified로 한 번만 */
    private fun approached(v: VisitState) = v.copy(approachNotified = true, stage = if (v.stage == VisitStage.PENDING) VisitStage.APPROACH_NOTIFIED else v.stage)
    private fun announceApproach(sp: Spot) { notifier.approach(sp); gw.narrator.speak("곧 ${sp.name.iyeyo()}. 주차 후 온길에서 도착을 확인할 수 있어요.") }

    /** 근접 알림 콜백(SDK 스레드). 지금 가는 곳일 때만 */
    private fun onApproach(spotId: String) {
        val sp = catalog.spot(spotId) ?: return
        var fire = false
        records.update { st ->
            val s = st.session ?: return@update st
            val cur = s.currentVisit ?: return@update st
            if (cur.spotId != spotId || cur.approachNotified) return@update st
            fire = true
            st.copy(session = s.copy(visits = s.visits.map { if (it.spotId == spotId) approached(it) else it }))
        }
        if (fire) announceApproach(sp)
    }

    // ───────── 틱: 반경 → 정차 → 스탬프 ─────────

    /** 한 틱에서 일어난 전이. update 안에서는 상태만 바꾸고 알림·TTS는 밖에서 */
    private class Events { var approached: Spot? = null; var dwellStarted: Spot? = null; var stamped: Spot? = null }

    private fun dwellMs(sp: Spot, fast: Boolean) = sp.stamp.dwellSec * 1000L / (if (fast) FAST_DWELL_DIVISOR else 1)

    @Synchronized
    private fun tick(fix: Fix?, veh: VehicleSnapshot) {
        if (records.state.value.session?.status != TourStatus.ACTIVE) return
        val now = System.currentTimeMillis()
        val ev = Events()
        records.update { st ->
            var s = st.session ?: return@update st
            s = withDistance(s, fix)
            // 지나간 시군구 기록(여행·날짜당 한 번) → 히트맵
            val entered = fix?.let { regionOf(it) }?.takeIf { code -> st.visits.none { it.regionId == code && it.planId == s.planId && it.day == s.day } }
            val visits = if (entered != null) st.visits + RegionVisit(entered, now, s.planId, s.day) else st.visits
            // 판정 대상: 순서상 다음 명소. 단, 다른 미완료 명소의 반경 안에 차가 있으면 그곳을 먼저(한 곳을 건너뛰고 다음 곳에 서도 기록되게)
            val cur = if (fix == null) s.currentVisit else s.visits.firstOrNull { v ->
                v.stage != VisitStage.STAMPED && v.stage != VisitStage.PASSED && catalog.spot(v.spotId)?.let { Geo.distanceM(fix.lat, fix.lng, it.lat, it.lng) <= it.stamp.radiusM } == true
            } ?: s.currentVisit
            val sp = cur?.let { catalog.spot(it.spotId) }
            if (cur == null || sp == null || fix == null) return@update st.copy(visits = visits, session = s)

            val d = Geo.distanceM(fix.lat, fix.lng, sp.lat, sp.lng)
            var v = cur
            // ① 1km 안에 들어왔는데 근접 알림이 안 왔으면 여기서 보정
            if (d <= APPROACH_M && !v.approachNotified) { v = approached(v); ev.approached = sp }
            // ② 반경 안 + 기어 P + 연속 체류 → 스탬프. 판정은 core
            val before = v.stage
            v = VisitJudge.step(v, VisitJudge.Obs(d, sp.stamp.radiusM, veh.parked, now, dwellMs(sp, st.demoFastDwell)))
            // ③ 전이 기록 (도착 알림은 1회)
            if (before != VisitStage.DWELLING && v.stage == VisitStage.DWELLING) ev.dwellStarted = sp
            if ((v.stage == VisitStage.IN_RADIUS || v.stage == VisitStage.DWELLING) && !v.arrivedNotified) v = v.copy(arrivedNotified = true)   // 반경 진입 자체는 알리지 않는다(주행 중 탭 유도 금지). 정차하면 알림
            s = s.copy(visits = s.visits.map { if (it.spotId == v.spotId) v else it })
            // ④ 스탬프: 세션과 스탬프 목록을 같은 update에서 바꾼다 (같은 곳 두 번은 안 찍음)
            if (v.stage == VisitStage.STAMPED && before != VisitStage.STAMPED) {
                ev.stamped = sp
                val stamps = if (st.stamps.any { it.spotId == sp.id && it.planId == s.planId }) st.stamps else st.stamps + newStamp(sp, v, s, fix, now, veh)
                return@update st.copy(visits = visits, session = s.copy(lastStampedSpotId = sp.id), stamps = stamps)
            }
            st.copy(visits = visits, session = s)
        }
        afterTick(ev)
    }

    /** 지역 안에서 다른 여행에 또 서면 스탬프가 하나 더 쌓인다(히트맵). 진행도는 서로 다른 명소 수 */
    private fun stampedCount(st: SavedState, code: String) = st.stamps.mapNotNullTo(HashSet()) { if (it.regionId == code) it.spotId else null }.size

    // 시군구 판정은 230개 폴리곤 point-in-polygon이라 200m 넘게 움직였을 때만 다시 한다
    private var regionCacheLat = 0.0; private var regionCacheLng = 0.0; private var regionCache: String? = null; private var regionCached = false
    private fun regionOf(fix: Fix): String? {
        if (regionCached && Geo.distanceM(regionCacheLat, regionCacheLng, fix.lat, fix.lng) < REGION_RECHECK_M) return regionCache
        regionCache = catalog.geometry.regionAt(fix.lat, fix.lng); regionCacheLat = fix.lat; regionCacheLng = fix.lng; regionCached = true
        return regionCache
    }

    private fun withDistance(s: TourSession, fix: Fix?): TourSession {
        fix ?: return s
        val pl = s.lastLat; val pg = s.lastLng
        val step = if (pl != null && pg != null) Geo.distanceM(pl, pg, fix.lat, fix.lng).takeIf { it in MIN_STEP_M..MAX_JUMP_M } ?: 0.0 else 0.0
        return s.copy(distanceM = s.distanceM + step, lastLat = fix.lat, lastLng = fix.lng, lastFixAt = fix.atMs)
    }

    private fun newStamp(sp: Spot, v: VisitState, s: TourSession, fix: Fix, now: Long, veh: VehicleSnapshot) = Stamp(
        spotId = sp.id, regionId = sp.regionId, planId = s.planId, at = now, lat = fix.lat, lng = fix.lng,
        dwellSec = ((now - (v.dwellStartedAt ?: now)) / 1000).toInt(),
        vinHash = veh.vinHash, certainty = if (veh.parked) "HIGH" else "MEDIUM", isSimulation = s.isSimulation,
    )   // achievements에는 스탬프 태그("stamp:<setId>")만 들어간다. 미션·도전과제는 없음

    /** 틱이 저장한 다음에 하는 일: 알림·이야기·다음 세그먼트·하루 마감 */
    private fun afterTick(ev: Events) {
        val st = records.state.value; val s = st.session ?: return
        // 알림 진행도: 그 명소가 든 지자체 세트가 있으면 "충주 10경 1/10", 없으면 지역 명소 기준
        fun progressFor(sp: Spot): String = stamps.setsWith(sp.id).firstOrNull { it.regionId == s.regionId }?.let { set -> "${set.title} ${Stamps.progress(set, st.stamps)}/${set.spotIds.size}" }
            ?: "${catalog.region(s.regionId)?.name ?: ""} ${stampedCount(st, s.regionId)}/${catalog.spotsOf(s.regionId).size}"
        ev.approached?.let { announceApproach(it) }
        ev.dwellStarted?.let { notifier.parked(it, it.stamp.dwellSec / 60); playStory(it) }
        ev.stamped?.let { sp ->
            notifier.stamped(sp, progressFor(sp))
            notifier.updateOngoing(remainingText(s))
            markRegionComplete(sp, st, s)
            // 이 세그먼트가 끝났으면 다음 세그먼트 자동 등록(정차 중이므로 허용)
            scope.launch { if (segmentFinished(sp.id)) registerCurrentSegment() }
        }
        if (s.done) finishDayInternal()
    }

    /** 이 방문으로 다 돈 세트(여러 개일 수 있음)마다 이 방문에 "stamp:<세트>"를 달고 스탬프 알림 */
    private fun markRegionComplete(sp: Spot, st: SavedState, s: TourSession) {
        for (set in stamps.setsWith(sp.id).filter { it.regionId == s.regionId }) {
            if (!Stamps.earned(set, st.stamps)) continue
            val tag = Achievements.stampOf(set.id)
            if (st.stamps.any { tag in it.achievements }) continue   // 이미 받은 스탬프
            records.update { x -> x.copy(stamps = x.stamps.map { if (it.spotId == sp.id && it.planId == s.planId) it.copy(achievements = it.achievements + tag) else it }) }
            notifier.stampEarned(set.title)
        }
    }

    /** 도착 보상: 정차가 시작되면 이야기 1회(TTS) */
    private fun playStory(sp: Spot) {
        var play = false
        records.update { st ->
            val s = st.session ?: return@update st
            val v = s.visits.firstOrNull { it.spotId == sp.id } ?: return@update st
            if (v.storyPlayed) return@update st
            play = true
            st.copy(session = s.copy(visits = s.visits.map { x -> if (x.spotId == sp.id) x.copy(storyPlayed = true) else x }))
        }
        if (!play) return
        storyJob?.cancel()
        storyJob = scope.launch { delay(STORY_DELAY_MS); gw.narrator.speak("${sp.name.iyeyo()}. " + sp.story) }
    }

    // ───────── 내비 세그먼트(경유지 2 + 목적지 1) ─────────

    private fun todaySegments(st: SavedState, s: TourSession) = st.plan?.let { plan ->
        val dp = plan.days.firstOrNull { it.day == s.day } ?: return@let null
        val cat = catalog.catalog(plan.regionId) ?: return@let null
        Planner.segments(dp, cat, plan.lodging)
    }

    private fun segmentFinished(spotId: String): Boolean {
        val st = records.state.value; val s = st.session ?: return false
        val seg = todaySegments(st, s)?.getOrNull(s.segmentIndex) ?: return false
        return seg.lastOrNull()?.spotId == spotId
    }

    /** 현재 세그먼트(또는 다음)를 내비에 등록. 이미 끝난 세그먼트면 다음으로 넘김 */
    private suspend fun registerCurrentSegment(): Result<String> {
        val st = records.state.value; val s = st.session ?: return Result.failure(IllegalStateException("no session"))
        val segs = todaySegments(st, s) ?: return Result.failure(IllegalStateException("no plan/day/catalog"))
        val doneIds = s.visits.filter { it.stage == VisitStage.STAMPED || it.stage == VisitStage.PASSED }.map { it.spotId }.toSet()
        var idx = s.segmentIndex
        while (idx < segs.size && segs[idx].all { it.spotId == null || it.spotId in doneIds }) idx++
        if (idx >= segs.size) return Result.failure(IllegalStateException("오늘 구간이 모두 끝났어요"))
        val seg = segs[idx].filter { it.spotId == null || it.spotId !in doneIds }
        val r = gw.navigation.registerRoute(seg)
        if (r.isSuccess) records.update { it.copy(session = it.session?.copy(segmentIndex = idx)) }
        Log.i(TAG, "segment $idx register: $r")
        return r
    }

    /** 사용자가 "지나감": 지금 가는 곳을 PASSED로 하고 다음 세그먼트로 */
    private fun skipCurrent() {
        records.update { st ->
            val s = st.session ?: return@update st; val cur = s.currentVisit ?: return@update st
            st.copy(session = s.copy(visits = s.visits.map { if (it.spotId == cur.spotId) it.copy(stage = VisitStage.PASSED) else it }))
        }
        scope.launch { registerCurrentSegment() }
        if (records.state.value.session?.done == true) finishDayInternal()
    }
}
