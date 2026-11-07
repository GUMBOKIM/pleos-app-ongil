package kr.ongil.app


import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import kr.ongil.app.notify.Notifier
import kr.ongil.app.ui.AppRoot
import kr.ongil.app.store.AppStore
import kr.ongil.app.ui.components.OngilTheme

class MainActivity : ComponentActivity() {
    private val vm: AppStore by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val need = listOf(Manifest.permission.POST_NOTIFICATIONS, "pleos.car.permission.FUSED_LOCATION").filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 1)
        consume(intent)
        setContent { OngilTheme { AppRoot(vm) } }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); consume(intent) }

    /** 인텐트 엑스트라는 한 번만 쓴다. 액티비티가 재생성될 때 같은 인텐트로 또 화면을 바꾸지 않도록 지운다 */
    private fun consume(intent: Intent?) {
        if (intent == null) return
        handleDebug(intent)
        vm.openFromNotification(intent.getStringExtra(Notifier.EXTRA_SCREEN))
        listOf(Notifier.EXTRA_SCREEN, "reset", "seed", "fast", "narrow").forEach { intent.removeExtra(it) }
    }

    override fun onResume() { super.onResume(); vm.gw.navigation.refreshCurrentLocation() }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        vm.gw.position.restart()
    }

    /** 시연용. 에뮬레이터에서만: adb shell am start ... --ez fast true | --ez reset true */
    private fun handleDebug(intent: Intent?) {
        if (intent == null || !vm.app.isEmulator) return
        if (intent.getBooleanExtra("reset", false)) vm.resetAll()
        if (intent.getBooleanExtra("seed", false)) vm.seedDemoHistory()
        if (intent.hasExtra("fast")) vm.setFastDwell(intent.getBooleanExtra("fast", false))
        if (BuildConfig.DEBUG && intent.hasExtra("narrow")) kr.ongil.app.ui.components.Ongil.forceNarrow = intent.getBooleanExtra("narrow", false)
    }
}
