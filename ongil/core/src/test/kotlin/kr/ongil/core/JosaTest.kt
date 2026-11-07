package kr.ongil.core

import org.junit.Assert.assertEquals
import org.junit.Test

class JosaTest {
    @Test fun `받침 있으면 이에요, 없으면 예요`() {
        assertEquals("비내섬이에요", "비내섬".iyeyo())
        assertEquals("충주댐 · 충주나루예요", "충주댐 · 충주나루".iyeyo())
        assertEquals("미륵대원지예요", "미륵대원지".iyeyo())
        assertEquals("활옥동굴이에요", "활옥동굴".iyeyo())
    }
}
