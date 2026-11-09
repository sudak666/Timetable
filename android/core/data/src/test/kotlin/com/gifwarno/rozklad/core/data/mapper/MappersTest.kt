package com.gifwarno.rozklad.core.data.mapper

import com.gifwarno.rozklad.core.database.FamilyEntity
import com.gifwarno.rozklad.core.model.GradeStatus
import com.gifwarno.rozklad.core.network.model.NetworkGrade
import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class MappersTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `розбирає час Postgres з мікросекундами та зсувом`() {
        assertEquals(Instant.parse("2026-10-01T10:00:00.123456Z"), parseInstant("2026-10-01T10:00:00.123456+00:00"))
        assertEquals(Instant.parse("2026-10-01T10:00:00Z"), parseInstant("2026-10-01T10:00:00Z"))
        assertEquals(Instant.EPOCH, parseInstant("сміття"))
    }

    @Test
    fun `оцінка проходить мережа → БД → модель без втрат`() {
        val dto = NetworkGrade("g1", "k1", "Математика", 11, "2026-11-09", "approved", 250, "2026-11-09T08:00:00+00:00")
        val model = dto.asEntity("f1").asModel()
        assertEquals("g1", model.id)
        assertEquals(GradeStatus.APPROVED, model.status)
        assertEquals(250, model.amount)
        assertEquals(Instant.parse("2026-11-09T08:00:00Z"), model.createdAt)
    }

    @Test
    fun `налаштування сім'ї з JSON — числа, рядки, null`() {
        val e = FamilyEntity("f1", "Сім’я", "ABC", """{"12": 500, "11": "400", "streak_len": 3, "streak_bonus": null}""")
        val s = e.settings(json)
        assertEquals(500, s.rates[12])
        assertEquals(400, s.rates[11])
        assertEquals(3, s.streak.len)
        assertEquals(50, s.streak.bonus)
    }

    @Test
    fun `зламаний JSON налаштувань — дефолти, а не падіння`() {
        val s = FamilyEntity("f1", "", "", "{не json").settings(json)
        assertEquals(300, s.rates[12])
    }

    @Test
    fun `розклад — модель, мережа, БД і назад без втрат`() {
        val original = com.gifwarno.rozklad.core.domain.Timetable.EXAMPLE
        val back = original.asNetwork("k1").asEntity("f1", json).asModel(json)
        assertEquals(original, back)
        assertEquals("8:30", original.asNetwork("k1").bells.first().first())
    }

    @Test
    fun `пошкоджений розклад у БД — null, а не падіння`() {
        val broken = com.gifwarno.rozklad.core.database.TimetableEntity("k1", "f1", "[[\"25:99\"]]", "[]", "[]")
        assertEquals(null, broken.asModel(json))
    }
}
