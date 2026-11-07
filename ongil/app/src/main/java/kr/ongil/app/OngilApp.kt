package kr.ongil.app

import android.app.Application
import android.content.Intent
import android.os.Build
import kr.ongil.app.repository.CatalogRepository
import kr.ongil.app.repository.RecordRepository
import kr.ongil.app.gateway.Gateways
import kr.ongil.app.gateway.aaos.AaosUxRestrictions
import kr.ongil.app.api.MockOngilApi
import kr.ongil.app.api.OngilApi
import kr.ongil.app.repository.CourseRepository
import kr.ongil.app.repository.StampRepository
import kr.ongil.app.gateway.pleos.PleosNarrator
import kr.ongil.app.gateway.pleos.PleosNavigation
import kr.ongil.app.gateway.pleos.PleosPosition
import kr.ongil.app.gateway.pleos.PleosVehicle
import kr.ongil.app.notify.Notifier
import kr.ongil.app.service.TourService
import kr.ongil.core.TourStatus

class OngilApp : Application() {
    lateinit var records: RecordRepository; private set
    lateinit var catalog: CatalogRepository; private set
    lateinit var api: OngilApi; private set
    lateinit var stamps: StampRepository; private set
    lateinit var courses: CourseRepository; private set
    lateinit var gateways: Gateways; private set
    lateinit var notifier: Notifier; private set

    /** 에뮬레이터면 모든 기록에 isSimulation=true (정직성 규칙) */
    val isEmulator: Boolean by lazy {
        Build.FINGERPRINT.contains("generic") || Build.FINGERPRINT.contains("emulator") || Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK") || Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish") || Build.PRODUCT.contains("sdk")
    }

    override fun onCreate() {
        super.onCreate()
        records = RecordRepository(this)
        catalog = CatalogRepository(this)
        api = MockOngilApi(this)          // 서버가 생기면 여기만 바꾼다
        stamps = StampRepository(api)
        courses = CourseRepository(api, records, catalog)
        notifier = Notifier(this)
        gateways = Gateways(
            navigation = PleosNavigation(this),
            position = PleosPosition(this),
            vehicle = PleosVehicle(this),
            narrator = PleosNarrator(this),
            ux = AaosUxRestrictions(this),
        )
        gateways.ux.start()
        gateways.vehicle.start()
        gateways.position.start()
        // 진행 중 여행이 있으면 감시 서비스 복구 (프로세스가 죽었다 살아난 경우)
        if (records.state.value.session?.status == TourStatus.ACTIVE) runCatching { startForegroundService(Intent(this, TourService::class.java)) }
    }
}
