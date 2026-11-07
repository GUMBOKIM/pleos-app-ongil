package kr.ongil.app.api

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kr.ongil.core.Course
import kr.ongil.core.OngilJson
import kr.ongil.core.StampSet

/**
 * 서버 없는 구현. 스탬프 세트·공개 코스는 assets JSON, 올린 코스는 메모리.
 * 응답 지연을 조금 흉내 내서 화면이 로딩 상태를 다루는지 보이게 한다.
 */
class MockOngilApi(ctx: Context) : OngilApi {
    private companion object { const val TAG = "ONGIL_API"; const val LATENCY_MS = 120L }
    private val assets = ctx.assets
    private val published = ArrayList<Course>()

    private fun <T> readAsset(name: String, ser: kotlinx.serialization.KSerializer<List<T>>): List<T> =
        runCatching { assets.open(name).bufferedReader().use { OngilJson.decodeFromString(ser, it.readText()) } }
            .onFailure { Log.w(TAG, "$name 없음", it) }.getOrDefault(emptyList())

    override suspend fun stampSets(): List<StampSet> = withContext(Dispatchers.IO) { delay(LATENCY_MS); readAsset("stamp-sets.json", ListSerializer(StampSet.serializer())) }

    override suspend fun courses(): List<Course> = withContext(Dispatchers.IO) { delay(LATENCY_MS); published + readAsset("courses-sample.json", ListSerializer(Course.serializer())) }

    override suspend fun publishCourse(course: Course): Course { delay(LATENCY_MS); published.removeAll { it.id == course.id }; published.add(0, course); return course }
}
