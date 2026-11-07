package kr.ongil.app.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kr.ongil.app.MainActivity
import kr.ongil.app.R
import kr.ongil.core.Spot

/**
 * 주행 중 온길이 끼어드는 유일한 통로. 헤드업 알림(IMPORTANCE_HIGH, CATEGORY_NAVIGATION)은 내비 위에 뜬다(10/30 실측).
 * 버튼은 두지 않는다(주행 중 선택 요구 금지). 본문 탭 = 정차 후 온길로 돌아오기.
 */
class Notifier(private val ctx: Context) {
    companion object {
        const val CH_HUN = "ongil_hun"
        const val CH_TOUR = "ongil_tour_min"
        const val ID_HUN = 71
        const val ID_STAMP_SET = 72   // 세트 스탬프는 방문 알림과 따로(덮어쓰지 않게)
        const val ID_TOUR = 70
        const val EXTRA_SCREEN = "screen"
        private const val APPROACH_TIMEOUT_MS = 12_000L
        private const val EVENT_TIMEOUT_MS = 20_000L   // 도착·방문·스탬프 카드도 스스로 사라진다(차 HUN은 취소해도 남을 수 있음)
    }
    private val nm = ctx.getSystemService(NotificationManager::class.java)

    init {   // 앱 프로세스마다 1회(OngilApp). createNotificationChannel은 멱등
        nm.createNotificationChannel(NotificationChannel(CH_HUN, "온길 도착 안내", NotificationManager.IMPORTANCE_HIGH).apply { description = "다음 볼 곳 1km 전 · 도착 확인" })
        nm.createNotificationChannel(NotificationChannel(CH_TOUR, "온길 여행 진행", NotificationManager.IMPORTANCE_MIN).apply { description = "여행 중 위치·정차 확인" })
    }

    private fun openApp(screen: String): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(EXTRA_SCREEN, screen)
        return PendingIntent.getActivity(ctx, screen.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** 포그라운드 서비스 상주 알림. AAOS는 CATEGORY_NAVIGATION이면 중요도와 상관없이 헤드업으로 띄우므로 여기는 SERVICE */
    fun ongoing(text: String): Notification = Notification.Builder(ctx, CH_TOUR)
        .setSmallIcon(R.drawable.ic_ongil).setContentTitle("온길 여행 중").setContentText(text)
        .setCategory(Notification.CATEGORY_SERVICE).setOngoing(true).setOnlyAlertOnce(true)
        .setContentIntent(openApp("home")).build()

    fun updateOngoing(text: String) = nm.notify(ID_TOUR, ongoing(text))

    /** 헤드업 카드 한 장. 본문 탭 = 그 화면으로 */
    private fun headsUp(title: String, text: String, screen: String, timeoutMs: Long? = null, subText: String? = null, action: String? = null): Notification = Notification.Builder(ctx, CH_HUN)
        .setSmallIcon(R.drawable.ic_ongil).setContentTitle(title).setContentText(text)
        .setCategory(Notification.CATEGORY_NAVIGATION).setAutoCancel(true)
        .apply {
            if (timeoutMs != null) setTimeoutAfter(timeoutMs); if (subText != null) setSubText(subText)
            // 버튼은 정차 중에만 단다(주행 중 선택 요구 금지). 앱을 뒤에서 직접 띄우는 건 Android가 막으므로 버튼으로 연다
            if (action != null) addAction(Notification.Action.Builder(null, action, openApp(screen)).build())
        }
        .setContentIntent(openApp(screen)).build()

    /** 반경 안에서 기어 P: 정차 확인 시작. 정차 중이라 '온길 열기' 버튼을 둔다 */
    fun parked(spot: Spot, dwellMin: Int) = nm.notify(ID_HUN, headsUp("${spot.name}에 정차했어요", "${dwellMin}분 서 있으면 방문으로 기록돼요 · 이야기가 나와요", "arrival", timeoutMs = EVENT_TIMEOUT_MS, action = "온길 열기 · 도착 확인"))

    /** 1km 전. 12초 뒤 스스로 사라진다 */
    fun approach(spot: Spot) = nm.notify(ID_HUN, headsUp(
        "온길 · 곧 ${spot.name}",
        (if (spot.getOff) "주차 후 ${spot.walkM}m 걸어요" else "차에서 보여요") + " · ${spot.stayMin}분 머물기",
        "arrival", timeoutMs = APPROACH_TIMEOUT_MS, subText = "온길",
    ))

    fun stamped(spot: Spot, progress: String) = nm.notify(ID_HUN, headsUp("${spot.name} 방문 기록됐어요", "$progress · 탭하면 다음 장소", "arrival", timeoutMs = EVENT_TIMEOUT_MS))
    /** 세트 마지막 명소에 서서 스탬프를 받은 순간 */
    fun stampEarned(setTitle: String) = nm.notify(ID_STAMP_SET, headsUp("$setTitle 스탬프를 받았어요", "스탬프 화면에 들어갔어요 · 탭하면 보기", "arrival", timeoutMs = EVENT_TIMEOUT_MS))

    fun cancelAll() { nm.cancel(ID_HUN); nm.cancel(ID_STAMP_SET); nm.cancel(ID_TOUR) }
}
