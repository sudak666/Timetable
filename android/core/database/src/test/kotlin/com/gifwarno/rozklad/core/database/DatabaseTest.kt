package com.gifwarno.rozklad.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Міграції Room і запити черги, від яких залежить збереження розкладу з версією
 * (аудит 14.11.2026: В7 і Б15 — найризиковіший код без тестів).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = context.getDatabasePath(NAME),
        driver = AndroidSQLiteDriver(),
        databaseClass = RozkladDatabase::class,
    )

    private val db = Room.inMemoryDatabaseBuilder(context, RozkladDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @After
    fun close() = db.close()

    @Test
    fun `міграція 4 → 5 зберігає розклад і додає порожню версію`() {
        helper.createDatabase(4).use {
            it.execSQL(
                "INSERT INTO timetables (childId, familyId, bellsJson, weekJson, holidaysJson, activitiesJson) " +
                    "VALUES ('k1', 'f1', '[]', '[]', '[]', '[]')",
            )
        }
        helper.runMigrationsAndValidate(5).use { migrated ->
            migrated.prepare("SELECT familyId, updatedAt FROM timetables WHERE childId = 'k1'").use { s ->
                assertEquals(true, s.step())
                assertEquals("f1", s.getText(0))
                assertEquals(true, s.isNull(1))
            }
        }
    }

    @Test
    fun `нова версія розкладу витісняє попередню з черги, інші зміни лишаються`() = runTest {
        val outbox = db.outboxDao()
        outbox.enqueue(op("school_timetables", "UPSERT", "k1", """{"v":1}"""))
        outbox.enqueue(op("school_grades", "UPDATE", "g1", "{}"))
        outbox.dropPending("school_timetables", "k1", "UPSERT")
        outbox.enqueue(op("school_timetables", "UPSERT", "k1", """{"v":2}"""))

        val pending = outbox.pending()
        assertEquals(listOf("g1", "k1"), pending.map { it.rowId })
        assertEquals("""{"v":2}""", pending.last().payload)

        outbox.setPayload(pending.last().seq, """{"v":3}""")
        assertEquals("""{"v":3}""", outbox.pending().last().payload)
    }

    @Test
    fun `версія розкладу оновлюється після збереження`() = runTest {
        val family = db.familyDao()
        family.upsertTimetables(listOf(TimetableEntity("k1", "f1", "[]", "[]", "[]")))
        assertNull(family.timetable("k1")?.updatedAt)

        db.outboxDao().setTimetableVersion("k1", "2026-11-14T10:00:00+00:00")

        assertEquals("2026-11-14T10:00:00+00:00", family.timetable("k1")?.updatedAt)
    }

    private fun op(table: String, kind: String, row: String, payload: String) =
        OutboxEntity(familyId = "f1", tableName = table, kind = kind, rowId = row, payload = payload, createdAt = 0)

    private companion object {
        const val NAME = "migration-test.db"
    }
}
