package kr.ongil.app.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kr.ongil.app.api.OngilApi
import kr.ongil.core.Course
import kr.ongil.core.DayCard
import kr.ongil.core.SavedState

/** 공개 코스 저장소: 서버(API) 목록 + 내가 올린 그날 카드. 올린 카드 id는 RecordRepository에 남긴다 */
class CourseRepository(private val api: OngilApi, private val records: RecordRepository, private val catalog: CatalogRepository) {
    private val _remote = MutableStateFlow<List<Course>>(emptyList())
    val remote: StateFlow<List<Course>> = _remote

    suspend fun refresh() { runCatching { api.courses() }.onSuccess { _remote.value = it } }

    fun fromCard(c: DayCard): Course = Course("my-${c.id}", "${catalog.region(c.regionId)?.name ?: ""} · 내 코스", c.regionId, c.stampedSpotIds, by = "나", date = c.date, distanceKm = c.distanceKm, cars = 1, verified = true, sample = false)

    /** 내가 올린 것 먼저, 그 다음 서버 목록(같은 id는 한 번) */
    fun all(st: SavedState = records.state.value): List<Course> {
        val mine = st.cards.filter { it.id in st.sharedCardIds && it.stampedSpotIds.isNotEmpty() }.map { fromCard(it) }
        val ids = mine.map { it.id }.toSet()
        return mine + _remote.value.filter { it.id !in ids }
    }

    suspend fun share(cardId: String) {
        val card = records.state.value.cards.firstOrNull { it.id == cardId } ?: return
        runCatching { api.publishCourse(fromCard(card)) }
        records.update { it.copy(sharedCardIds = it.sharedCardIds + cardId) }
    }
}
