package com.gifwarno.rozklad.core.network.supabase

import com.gifwarno.rozklad.core.network.RozkladNetworkDataSource
import com.gifwarno.rozklad.core.network.model.NetworkAlert
import com.gifwarno.rozklad.core.network.model.NetworkAlertLocation
import com.gifwarno.rozklad.core.network.model.NetworkAlertState
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
import io.github.jan.supabase.auth.providers.Google
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
import kotlinx.serialization.json.JsonObject
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

    override suspend fun resetPassword(email: String) {
        client.auth.resetPasswordForEmail(email, redirectUrl = EMAIL_CONFIRM_REDIRECT)
    }

    override suspend fun changePassword(password: String) {
        client.auth.updateUser { this.password = password }
    }

    override suspend fun signOut() {
        client.auth.signOut()
    }

    override suspend fun signInWithGoogle() {
        client.auth.signInWith(Google) {
            // Дати вибрати акаунт Google, а не входити мовчки в останній.
            queryParams["prompt"] = "select_account"
        }
    }

    override suspend fun completeOAuth(callbackUri: String) {
        val query = OAuthCallback.parse(callbackUri)
        query.error?.let { throw IllegalStateException(it) }
        val code = query.code ?: throw IllegalStateException("Google не повернув код входу")
        client.auth.exchangeCodeForSession(code)
    }

    override suspend fun deleteAccount() {
        client.postgrest.rpc("school_delete_account")
        // Користувача на сервері вже немає — /logout відповів би помилкою, тож чистимо сесію лише локально.
        client.auth.clearSession()
    }

    override suspend fun registerPushToken(token: String, alerts: Boolean) {
        client.postgrest.rpc(
            "school_register_fcm",
            buildJsonObject {
                put("p_token", token)
                put("p_alerts", alerts)
            },
        )
    }

    override suspend fun unregisterPushToken(token: String) {
        client.from("school_fcm_tokens").delete { filter { eq("token", token) } }
    }

    override suspend fun membership(userId: String): NetworkMembership? = client.from("school_members")
        .select(Columns.list("family_id", "role")) {
            filter { eq("user_id", userId) }
            // Якщо сімей кілька — завжди та сама (як у вебі), а не випадкова.
            order("family_id", Order.ASCENDING)
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
        // Невірний код сервер повертає як null, а не винятком: так він рахує невдалі спроби (ліміт — 10 на годину).
        val familyId = client.postgrest.rpc(
            "school_join_family",
            buildJsonObject {
                put("p_code", code)
                put("p_name", name)
            },
        ).decodeAsOrNull<String>()
        checkNotNull(familyId) { "Невірний код" }
    }

    override suspend fun parentCode(familyId: String): String? = client.postgrest
        .rpc("school_parent_code", buildJsonObject { put("fid", familyId) })
        .decodeAsOrNull<String>()

    override suspend fun rotateInviteCode(familyId: String): String = client.postgrest
        .rpc("school_rotate_invite_code", buildJsonObject { put("fid", familyId) })
        .decodeAs<String>()

    override suspend fun removeMember(familyId: String, userId: String) {
        // RLS мовчки пропускає чужі видалення (0 рядків) — перевіряємо, що рядок справді зник.
        val removed = client.from("school_members").delete {
            select(Columns.list("user_id"))
            filter {
                eq("family_id", familyId)
                eq("user_id", userId)
            }
        }.decodeList<JsonObject>()
        check(removed.isNotEmpty()) { "Прибрати учасника можуть лише батьки" }
    }

    override suspend fun snapshot(familyId: String): NetworkSnapshot = coroutineScope {
        val family = async {
            client.from("school_families").select(Columns.list("id", "name", "invite_code", "rates", "alert_uid")) {
                filter { eq("id", familyId) }
            }.decodeSingle<NetworkFamily>()
        }
        val members = async {
            client.from("school_members").select(
                Columns.list("user_id", "role", "name", "goal_title", "goal_amount", "avatar", "theme"),
            ) { filter { eq("family_id", familyId) } }.decodeList<NetworkMember>()
        }
        // Оцінки й журнал — усі, без обрізання: з них рахується баланс «до виплати» (Rewards.totals).
        // Сторінками, бо сервер віддає не більше ~1000 рядків за запит; id — для однозначного порядку.
        val grades = async {
            allPages { from, to ->
                client.from("school_grades").select(
                    Columns.list("id", "child_id", "subject", "grade", "date", "status", "amount", "created_at"),
                ) {
                    filter { eq("family_id", familyId) }
                    order("date", Order.DESCENDING)
                    order("id", Order.ASCENDING)
                    range(from, to)
                }.decodeList<NetworkGrade>()
            }
        }
        val ledger = async {
            allPages { from, to ->
                client.from("school_ledger").select(
                    Columns.list("id", "child_id", "kind", "amount", "note", "date", "created_at"),
                ) {
                    filter { eq("family_id", familyId) }
                    order("date", Order.DESCENDING)
                    order("id", Order.ASCENDING)
                    range(from, to)
                }.decodeList<NetworkLedger>()
            }
        }
        val challenges = async {
            allPages { from, to ->
                client.from("school_challenges").select(
                    Columns.list("id", "child_id", "title", "subject", "min_grade", "need", "reward", "start_date", "end_date"),
                ) {
                    filter { eq("family_id", familyId) }
                    order("id", Order.ASCENDING)
                    range(from, to)
                }.decodeList<NetworkChallenge>()
            }
        }
        val homework = async {
            allPages { from, to ->
                client.from("school_homework").select(Columns.list("id", "child_id", "subject", "due", "text", "done")) {
                    filter { eq("family_id", familyId) }
                    order("due", Order.ASCENDING)
                    order("id", Order.ASCENDING)
                    range(from, to)
                }.decodeList<NetworkHomework>()
            }
        }
        val timetables = async {
            client.from("school_timetables").select(Columns.list("child_id", "bells", "week", "holidays", "updated_at")) {
                filter { eq("family_id", familyId) }
            }.decodeList<NetworkTimetable>()
        }
        val familyRow = family.await()
        NetworkSnapshot(
            family = familyRow,
            members = members.await(),
            grades = grades.await(),
            ledger = ledger.await(),
            challenges = challenges.await(),
            homework = homework.await(),
            timetables = timetables.await(),
            alert = familyRow.alertUid?.let { alert(it) },
        )
    }

    /** Усі рядки запиту, сторінка за сторінкою, доки сторінка не виявиться неповною. */
    private suspend fun <T> allPages(load: suspend (from: Long, to: Long) -> List<T>): List<T> {
        val out = mutableListOf<T>()
        var from = 0L
        while (true) {
            val page = load(from, from + PAGE - 1)
            out += page
            if (page.size < PAGE) return out
            from += PAGE
        }
    }

    override suspend fun alertLocations(oblastUid: Int?): List<NetworkAlertLocation> =
        client.from("school_alert_locations").select(Columns.list("uid", "name", "kind", "oblast_uid", "raion_uid")) {
            filter {
                if (oblastUid == null) isIn("kind", listOf("oblast", "city")) else eq("oblast_uid", oblastUid)
            }
            order("name", Order.ASCENDING)
        }.decodeList<NetworkAlertLocation>()

    override suspend fun setAlertRegion(familyId: String, uid: Int?) {
        // RLS мовчки пропускає чужі оновлення (0 рядків) — перевіряємо, що зміна справді відбулась.
        val changed = client.from("school_families").update(buildJsonObject { put("alert_uid", uid) }) {
            select(Columns.list("id"))
            filter { eq("id", familyId) }
        }.decodeList<JsonObject>()
        check(changed.isNotEmpty()) { "Змінити місце можуть лише батьки" }
    }

    override suspend fun alert(uid: Int): NetworkAlert? = coroutineScope {
        val location = async {
            client.from("school_alert_locations").select(Columns.list("uid", "name", "kind", "oblast_uid", "raion_uid")) {
                filter { eq("uid", uid) }
            }.decodeSingleOrNull<NetworkAlertLocation>()
        }
        val state = async {
            client.from("school_alert_state").select(Columns.list("uid", "status", "since", "checked_at")) {
                filter { eq("uid", uid) }
            }.decodeSingleOrNull<NetworkAlertState>()
        }
        location.await()?.let { NetworkAlert(it, state.await()) }
    }

    override suspend fun write(op: NetworkWrite): WriteResult = try {
        val table = client.from(op.table)
        when (op.kind) {
            WriteKind.INSERT -> {
                table.upsert(op.body) {
                    onConflict = op.key
                    ignoreDuplicates = true
                }
                WriteResult.Ok
            }

            WriteKind.UPSERT -> {
                table.upsert(op.body) { onConflict = op.key }
                WriteResult.Ok
            }

            // RLS і видалений рядок дають «0 рядків» без помилки — інакше показали б «збережено», а зміни немає.
            WriteKind.UPDATE -> {
                val changed = table.update(op.body) {
                    select(Columns.list(op.key))
                    filter { eq(op.key, op.rowId) }
                }.decodeList<JsonObject>()
                if (changed.isEmpty()) WriteResult.Rejected(NOT_APPLIED) else WriteResult.Ok
            }

            // Видалення ідемпотентне: рядка вже немає — мета досягнута.
            WriteKind.DELETE -> {
                table.delete { filter { eq(op.key, op.rowId) } }
                WriteResult.Ok
            }
        }
    } catch (e: RestException) {
        // Назавжди відкидаємо лише справжню відмову сервера (права, обмеження, некоректні дані).
        // Збій сервера чи протермінований токен — не привід втратити оцінку, додану офлайн.
        when (writeFailureOf(e.statusCode)) {
            WriteFailure.REJECTED -> WriteResult.Rejected(e.description ?: e.error)

            WriteFailure.TRANSIENT -> WriteResult.Retry

            WriteFailure.AUTH -> {
                // Офлайн довше за годину → токен доступу протермінований. Оновлюємо й пробуємо знову.
                runCatching { client.auth.refreshCurrentSession() }
                WriteResult.Retry
            }
        }
    } catch (_: HttpRequestException) {
        WriteResult.Retry
    } catch (_: HttpRequestTimeoutException) {
        WriteResult.Retry
    } catch (_: IOException) {
        WriteResult.Retry
    }

    private companion object {
        /** Розмір сторінки: PostgREST у Supabase віддає не більше 1000 рядків за запит. */
        const val PAGE = 1000L

        /** UPDATE не зачепив жодного рядка: запис видалили на іншому пристрої або бракує прав. */
        const val NOT_APPLIED = "запис уже видалено або змінити його можуть лише батьки"

        /** Підтвердження пошти відкривається у веб-версії — після нього можна увійти в застосунку. */
        const val EMAIL_CONFIRM_REDIRECT = "https://sudak666.github.io/Timetable/"
    }
}
