package kr.ongil.app.gateway.pleos

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kr.ongil.app.gateway.Narrator
import kr.ongil.app.gateway.guard
import java.util.Locale

/** Pleos TTS 3.3.1(ON_DEVICE, ko-KR) 래퍼. 준비 전 speak는 모아 두고 준비되면 순서대로 */
class PleosNarrator(ctx: Context) : Narrator {
    private companion object { const val TAG = "ONGIL_TTS" }
    private val tts = TextToSpeech(ctx, Mode.ON_DEVICE)
    @Volatile private var ready = false
    private val _speaking = MutableStateFlow(false)
    override val speaking: StateFlow<Boolean> = _speaking
    private val pending = java.util.concurrent.CopyOnWriteArrayList<String>()

    init {
        guard(TAG, "tts init") {
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    tts.addEventListener(object : EventListener {
                        override fun onReady() {}
                        override fun onStart() { _speaking.value = true }
                        override fun onDone() { _speaking.value = false }
                        override fun onStop() { _speaking.value = false }
                        override fun onError(e: ErrorType) { _speaking.value = false; Log.w(TAG, "tts error $e") }
                        override fun onUpdatedRms(r: Double) {}
                    })
                    tts.setLocale(Locale.KOREA)
                    ready = true
                    pending.toList().forEach { speak(it) }; pending.clear()
                }
                override fun onFailure(e: ErrorType) { Log.w(TAG, "tts init fail $e") }
            })
        }
    }

    override fun speak(text: String) {
        if (!ready) { pending += text; return }
        guard(TAG, "speak") { tts.speak(text, true) }
    }
    override fun stop() { guard(TAG, "stop") { tts.stop() } }
}
