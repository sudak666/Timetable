package com.gifwarno.rozklad.core.network.supabase

import com.gifwarno.rozklad.core.network.RozkladNetworkDataSource
import com.gifwarno.rozklad.core.network.model.NetworkAuthState
import com.gifwarno.rozklad.core.network.model.NetworkChallenge
import com.gifwarno.rozklad.core.network.model.NetworkFamily
import com.gifwarno.rozklad.core.network.model.NetworkGrade
import com.gifwarno.rozklad.core.network.model.NetworkHomework
import com.gifwarno.rozklad.core.network.model.NetworkLedger
import com.gifwarno.rozklad.core.network.model.NetworkMember
import com.gifwarno.rozklad.core.network.model.NetworkMembership
import com.gifwarno.rozklad.core.network.model.NetworkSnapshot
import com.gifwarno.rozklad.core.network.model.NetworkTimetable
import com.gifwarno.rozklad.core.network.model.NetworkUser
import com.gifwarno.rozklad.core.network.model.NetworkWrite
import com.gifwarno.rozklad.core.network.model.WriteKind
import com.gifwarno.rozklad.core.network.model.WriteResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Singleton
internal class SupabaseNetworkDataSource @Inject constructor(private val client: SupabaseClient) : RozkladNetworkDataSource {
    override val authState: Flow<NetworkAuthState> = client.auth.sessionStatus
        .map { status ->
            when (status) {
                is SessionStatus.Initializing -> NetworkAuthState.Initializing

                is SessionStatus.NotAuthenticated -> NetworkAuthState.SignedOut

                // Не вдалося оновити токен (часто — офлайн): лишаємо користувача в застосунку з локальними даними.
                is SessionStatus.RefreshFailure -> client.auth.currentUserOrNull()?.let { NetworkAuthState.SignedIn(it.toNetwork()) }
                    ?: NetworkAuthState.SignedOut

                is SessionStatus.Authenticated -> status.session.user?.let { NetworkAuthState.SignedIn(it.toNetwork()) }
                    ?: NetworkAuthState.SignedOut
            }
        }
        .distinctUntilChanged()

    private fun io.github.jan.supabase.auth.user.UserInfo.toNetwork() = NetworkUser(
        id = id,
        email = email.orEmpty(),
        fullName = userMetadata?.get("full_name")?.jsonPrimitive?.content.orEmpty(),
    )

    override suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signUp(email: String, password: String): Boolean {
        val user = client.auth.signUpWith(Email, redirectUrl = EMAIL_CONFIRM_REDIRECT) {
            this.email = email
            this.password = password
        }
        // Якщо сесії немає — Supabase чекає підтвердження пошти.
        return user != null && client.auth.currentSessionOrNull() == null
    }

    override suspend fun signOut() {
        client.auth.signOut()
    }

    override suspend fun deleteAccount() {
        client.postgrest.rpc("school_delete_account")
        // Користувача на сервері вже немає — /logout відповів би помилкою, тож чистимо сесію лише локально.
        client.auth.clearSession()
    }

    override suspend fun membership(userId: String): NetworkMembership? = client.from("school_members")
        .select(Columns.list("family_id", "role")) {
            filter { eq("user_id", userId) }
            limit(1)
        }
        .decodeList<NetworkMembership>()
        .firstOrNull()

    override suspend fun createFamily(familyName: String, parentName: String) {
        client.postgrest.rpc(
            "school_create_family",
            buildJsonObject {
                put("p_name", familyName)
                put("p_parent_name", parentName)
            },
        )
    }

    override suspend fun joinFamily(code: String, name: String) {
        client.postgrest.rpc(
            "school_join_family",
            buildJsonObject {
                put("p_code", code)
                put("p_name", name)
            },
        )
    }

    override suspend fun parentCode(familyId: String): String? = client.postgrest
        .rpc("school_parent_code", buildJsonObject { put("fid", familyId) })
        .decodeAsOrNull<String>()

    override suspend fun snapshot(familyId: String): NetworkSnapshot = coroutineScope {
        val family = async {
            client.from("school_families").select(Columns.list("id", "name", "invite_code", "rates")) {
                filter { eq("id", familyId) }
            }.decodeSingle<NetworkFamily>()
        }
        val members = async {
            client.from("school_members").select(
                Columns.list("user_id", "role", "name", "goal_title", "goal_amount", "avatar", "theme"),
            ) { filter { eq("family_id", familyId) } }.decodeList<NetworkMember>()
        }
        val grades = async {
            client.from("school_grades").select(
                Columns.list("id", "child_id", "subject", "grade", "date", "status", "amount", "created_at"),
            ) {
                filter { eq("family_id", familyId) }
                order("date", Order.DESCENDING)
                limit(MAX_ROWS)
            }.decodeList<NetworkGrade>()
        }
        val ledger = async {
            client.from("school_ledger").select(
                Columns.list("id", "child_id", "kind", "amount", "note", "date", "created_at"),
            ) {
                filter { eq("family_id", familyId) }
                order("date", Order.DESCENDING)
                limit(MAX_ROWS)
            }.decodeList<NetworkLedger>()
        }
        val challenges = async {
            client.from("school_challenges").select(
                Columns.list("id", "child_id", "title", "subject", "min_grade", "need", "reward", "start_date", "end_date"),
            ) {
                filter { eq("family_id", familyId) }
                limit(MAX_CHALLENGES)
            }.decodeList<NetworkChallenge>()
        }
        val homework = async {
            client.from("school_homework").select(Columns.list("id", "child_id", "subject", "due", "text", "done")) {
                filter { eq("family_id", familyId) }
                order("due", Order.ASCENDING)
                limit(MAX_HOMEWORK)
            }.decodeList<NetworkHomework>()
        }
        val timetables = async {
            client.from("school_timetables").select(Columns.list("child_id", "bells", "week", "holidays", "updated_at")) {
                filter { eq("family_id", familyId) }
            }.decodeList<NetworkTimetable>()
        }
        NetworkSnapshot(
            family = family.await(),
            members = members.await(),
            grades = grades.await(),
            ledger = ledger.await(),
            challenges = challenges.await(),
            homework = homework.await(),
            timetables = timetables.await(),
        )
    }

    override suspend fun write(op: NetworkWrite): WriteResult = try {
        val table = client.from(op.table)
        when (op.kind) {
            WriteKind.INSERT -> table.upsert(op.body) {
                onConflict = op.key
                ignoreDuplicates = true
            }

            WriteKind.UPSERT -> table.upsert(op.body) { onConflict = op.key }

            WriteKind.UPDATE -> table.update(op.body) { filter { eq(op.key, op.rowId) } }

            WriteKind.DELETE -> table.delete { filter { eq(op.key, op.rowId) } }
        }
        WriteResult.Ok
    } catch (e: RestException) {
        WriteResult.Rejected(e.description ?: e.error)
    } catch (_: HttpRequestException) {
        WriteResult.Retry
    } catch (_: HttpRequestTimeoutException) {
        WriteResult.Retry
    } catch (_: IOException) {
        WriteResult.Retry
    }

    private companion object {
        const val MAX_ROWS = 2000L
        const val MAX_HOMEWORK = 1000L
        const val MAX_CHALLENGES = 200L

        /** Підтвердження пошти відкривається у веб-версії — після нього можна увійти в застосунку. */
        const val EMAIL_CONFIRM_REDIRECT = "https://sudak666.github.io/Timetable/"
    }
}
