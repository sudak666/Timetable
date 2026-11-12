package com.gifwarno.rozklad.core.data.sync

import com.gifwarno.rozklad.core.database.OutboxDao
import com.gifwarno.rozklad.core.database.OutboxEntity
import com.gifwarno.rozklad.core.network.RozkladNetworkDataSource
import com.gifwarno.rozklad.core.network.model.NetworkAlert
import com.gifwarno.rozklad.core.network.model.NetworkAlertLocation
import com.gifwarno.rozklad.core.network.model.NetworkAuthState
import com.gifwarno.rozklad.core.network.model.NetworkMembership
import com.gifwarno.rozklad.core.network.model.NetworkSnapshot
import com.gifwarno.rozklad.core.network.model.NetworkWrite
import com.gifwarno.rozklad.core.network.model.WriteResult
import javax.inject.Provider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Офлайн-черга: що лишається, що відкидається і чи не губляться зміни.
 * (V1/V6 з аудиту 12.11.2026 — найризиковіший код раніше був без тестів.)
 */
class SyncManagerTest {
    private val dao = FakeOutboxDao()
    private val network = ScriptedNetwork()
    private val sync = SyncManager(network, dao, Json, workManager = Provider { error("WorkManager у цих тестах не потрібен") })

    private fun op(row: String) = OutboxEntity(
        familyId = "f1",
        tableName = Tables.GRADES,
        kind = "UPDATE",
        rowId = row,
        payload = """{"status":"approved"}""",
        createdAt = 0,
    )

    @Test
    fun `успішні записи йдуть по порядку й зникають із черги`() = runTest {
        dao.enqueue(op("a"))
        dao.enqueue(op("b"))
        network.results += listOf(WriteResult.Ok, WriteResult.Ok)

        assertTrue(sync.flush())

        assertEquals(listOf("a", "b"), network.sent.map { it.rowId })
        assertTrue(dao.pending().isEmpty())
    }

    @Test
    fun `немає мережі чи збій сервера — зміна лишається, наступні не обганяють її`() = runTest {
        dao.enqueue(op("a"))
        dao.enqueue(op("b"))
        network.results += WriteResult.Retry

        assertFalse(sync.flush())

        assertEquals(listOf("a"), network.sent.map { it.rowId })
        assertEquals(listOf("a", "b"), dao.pending().map { it.rowId })
        assertEquals(1, dao.pending().first().attempts)
        assertNull(sync.status.first().lastError)
    }

    @Test
    fun `після повтору черга дочищається`() = runTest {
        dao.enqueue(op("a"))
        network.results += listOf(WriteResult.Retry, WriteResult.Ok)

        assertFalse(sync.flush())
        assertTrue(sync.flush())

        assertTrue(dao.pending().isEmpty())
    }

    @Test
    fun `свідома відмова сервера — зміну відкидаємо, решту відправляємо, причину показуємо людською мовою`() = runTest {
        dao.enqueue(op("a"))
        dao.enqueue(op("b"))
        network.results += listOf(WriteResult.Rejected("new row violates row-level security policy"), WriteResult.Ok)

        assertTrue(sync.flush())

        assertTrue(dao.pending().isEmpty())
        assertEquals("Зміну не збережено: Недостатньо прав для цієї дії", sync.status.first().lastError)
        assertEquals(0, sync.status.first().pendingChanges)
    }
}

private class FakeOutboxDao : OutboxDao {
    private val rows = MutableStateFlow<List<OutboxEntity>>(emptyList())
    private var seq = 0L

    override suspend fun enqueue(op: OutboxEntity): Long {
        seq += 1
        rows.update { it + op.copy(seq = seq) }
        return seq
    }

    override suspend fun pending(): List<OutboxEntity> = rows.value

    override suspend fun pendingFor(familyId: String) = rows.value.filter { it.familyId == familyId }

    override suspend fun clear() = rows.update { emptyList() }

    override fun observeCount(): Flow<Int> = rows.map { it.size }

    override suspend fun remove(seq: Long) = rows.update { list -> list.filterNot { it.seq == seq } }

    override suspend fun markAttempt(seq: Long) =
        rows.update { list -> list.map { if (it.seq == seq) it.copy(attempts = it.attempts + 1) else it } }
}

/** Мережа, що відповідає на записи заздалегідь заданими результатами; решта методів тут не потрібні. */
private class ScriptedNetwork : RozkladNetworkDataSource {
    val results = ArrayDeque<WriteResult>()
    val sent = mutableListOf<NetworkWrite>()

    override suspend fun write(op: NetworkWrite): WriteResult {
        sent += op
        return results.removeFirst()
    }

    override val authState: Flow<NetworkAuthState> = emptyFlow()

    override suspend fun signIn(email: String, password: String) = unused()

    override suspend fun signUp(email: String, password: String): Boolean = unused()

    override suspend fun resetPassword(email: String) = unused()

    override suspend fun changePassword(password: String) = unused()

    override suspend fun signOut() = unused()

    override suspend fun signInWithGoogle() = unused()

    override suspend fun completeOAuth(callbackUri: String) = unused()

    override suspend fun deleteAccount() = unused()

    override suspend fun registerPushToken(token: String, alerts: Boolean) = unused()

    override suspend fun unregisterPushToken(token: String) = unused()

    override suspend fun membership(userId: String): NetworkMembership? = unused()

    override suspend fun createFamily(familyName: String, parentName: String) = unused()

    override suspend fun joinFamily(code: String, name: String) = unused()

    override suspend fun parentCode(familyId: String): String? = unused()

    override suspend fun rotateInviteCode(familyId: String): String = unused()

    override suspend fun removeMember(familyId: String, userId: String) = unused()

    override suspend fun snapshot(familyId: String): NetworkSnapshot = unused()

    override suspend fun alertLocations(oblastUid: Int?): List<NetworkAlertLocation> = unused()

    override suspend fun setAlertRegion(familyId: String, uid: Int?) = unused()

    override suspend fun alert(uid: Int): NetworkAlert? = unused()

    private fun unused(): Nothing = error("Не використовується в тестах черги")
}
