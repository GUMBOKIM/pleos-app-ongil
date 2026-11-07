package kr.ongil.app.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kr.ongil.core.OngilJson
import kr.ongil.core.SavedState
import java.io.File

/** 유일한 영속 저장소: JSON 파일 1개, 원자적 교체. 스탬프·계획·세션·카드 전부 여기 */
class RecordRepository(ctx: Context) {
    private companion object { const val TAG = "ONGIL_STORE" }
    private val file = File(ctx.filesDir, "ongil-state.json")
    private val tmp = File(ctx.filesDir, "ongil-state.json.tmp")
    private val lock = Any()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<SavedState> = _state

    /** 깨진 파일은 .bad로 옮겨 두고 빈 상태로 시작한다 (덮어써서 증거를 지우지 않음) */
    private fun load(): SavedState {
        if (!file.exists()) return SavedState()
        return runCatching { OngilJson.decodeFromString(SavedState.serializer(), file.readText()) }
            .getOrElse { Log.w(TAG, "load fail, reset", it); file.renameTo(File(file.path + ".bad")); SavedState() }
    }

    /** 읽기-수정-쓰기 한 묶음. tmp에 다 쓴 뒤 rename이라 중간에 죽어도 이전 파일은 남는다 */
    fun update(f: (SavedState) -> SavedState): SavedState = synchronized(lock) {
        val next = f(_state.value)
        _state.value = next
        runCatching {
            tmp.writeText(OngilJson.encodeToString(SavedState.serializer(), next))
            if (!tmp.renameTo(file)) { file.delete(); check(tmp.renameTo(file)) { "rename ${tmp.name} -> ${file.name}" } }
        }.onFailure { Log.w(TAG, "save fail", it) }
        next
    }

    fun reset() = update { SavedState() }
}
