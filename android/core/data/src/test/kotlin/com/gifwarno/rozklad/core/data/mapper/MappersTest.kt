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
    fun `заняття — модель, мережа, БД і назад без втрат, порожні поля йдуть як null`() {
        val club = com.gifwarno.rozklad.core.model.Club(
            "Футбол",
            "⚽",
            setOf(java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.TUESDAY),
            java.time.LocalTime.of(16, 0),
            java.time.LocalTime.of(17, 30),
            bring = "форма",
        )
        val original = com.gifwarno.rozklad.core.domain.Timetable.EXAMPLE.copy(clubs = listOf(club))
        val net = original.asNetwork("k1")
        assertEquals(listOf(2, 4), net.activities.single().days)
        assertEquals("16:00", net.activities.single().start)
        assertEquals(null, net.activities.single().place)
        assertEquals(original, net.asEntity("f1", json).asModel(json))
    }

    @Test
    fun `зіпсовані заняття не ховають уроки`() {
        val e = com.gifwarno.rozklad.core.domain.Timetable.EXAMPLE.asNetwork("k1").asEntity("f1", json)
        val broken = e.copy(activitiesJson = """[{"t":"X","e":"⚽","d":[1],"a":"25:00","b":"1:00"}, 5]""")
        val m = broken.asModel(json)
        assertEquals(com.gifwarno.rozklad.core.domain.Timetable.EXAMPLE.week, m?.week)
        assertEquals(emptyList<Any>(), m?.clubs)
        // Рядок зі старої БД (до занять) — без колонки, за замовчуванням "[]".
        assertEquals(emptyList<Any>(), e.copy(activitiesJson = "[]").asModel(json)?.clubs)
    }

    @Test
    fun `пошкоджений розклад у БД — null, а не падіння`() {
        val broken = com.gifwarno.rozklad.core.database.TimetableEntity("k1", "f1", "[[\"25:99\"]]", "[]", "[]")
        assertEquals(null, broken.asModel(json))
    }
}
