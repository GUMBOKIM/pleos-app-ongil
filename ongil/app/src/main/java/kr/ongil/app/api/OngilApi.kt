package kr.ongil.app.api

import kr.ongil.core.Course
import kr.ongil.core.StampSet

/**
 * 온길 서버 API. 차 밖에 있어야 하는 것만 여기로 온다: 지자체 스탬프 세트, 공개 코스.
 * 추천·내비·알림·방문 판정·기록은 전부 차 안(core/service)이라 API가 아니다.
 * 1차는 MockOngilApi(assets + 메모리)만 있고, 서버가 생기면 Retrofit 구현으로 교체한다.
 */
interface OngilApi {
    suspend fun stampSets(): List<StampSet>
    suspend fun courses(): List<Course>
    suspend fun publishCourse(course: Course): Course
}
