package com.gifwarno.rozklad.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/* DTO 1:1 з таблицями Supabase (school_*). Змінюються лише разом із міграцією в supabase/migrations. */

@Serializable
data class NetworkFamily(
    val id: String,
    val name: String = "",
    @SerialName("invite_code") val inviteCode: String = "",
    val rates: JsonObject? = null,
    /** Місце для повітряних тривог (school_alert_locations.uid); null — не обрано. */
    @SerialName("alert_uid") val alertUid: Int? = null,
)

@Serializable
data class NetworkMember(
    @SerialName("user_id") val userId: String,
    val role: String,
    val name: String = "",
    @SerialName("goal_title") val goalTitle: String? = null,
    @SerialName("goal_amount") val goalAmount: Int? = null,
    val avatar: String? = null,
    val theme: String? = null,
)

@Serializable
data class NetworkMembership(@SerialName("family_id") val familyId: String, val role: String)

@Serializable
data class NetworkGrade(
    val id: String,
    @SerialName("child_id") val childId: String,
    val subject: String,
    val grade: Int,
    val date: String,
    val status: String,
    val amount: Int? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class NetworkLedger(
    val id: String,
    @SerialName("child_id") val childId: String,
    val kind: String,
    val amount: Int,
    val note: String? = null,
    val date: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class NetworkChallenge(
    val id: String,
    @SerialName("child_id") val childId: String,
    val title: String,
    val subject: String? = null,
    @SerialName("min_grade") val minGrade: Int,
    val need: Int,
    val reward: Int,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
)

@Serializable
data class NetworkHomework(
    val id: String,
    @SerialName("child_id") val childId: String,
    val subject: String,
    val due: String,
    val text: String,
    val done: Boolean,
)

/** Усі дані сім'ї одним знімком. */
data class NetworkSnapshot(
    val family: NetworkFamily,
    val members: List<NetworkMember>,
    val grades: List<NetworkGrade>,
    val ledger: List<NetworkLedger>,
    val challenges: List<NetworkChallenge>,
    val homework: List<NetworkHomework>,
    val timetables: List<NetworkTimetable> = emptyList(),
    /** Обране місце й стан тривоги для нього (null — місце не обрано). */
    val alert: NetworkAlert? = null,
)

/** Місце з довідника school_alert_locations. */
@Serializable
data class NetworkAlertLocation(
    val uid: Int,
    val name: String,
    val kind: String,
    @SerialName("oblast_uid") val oblastUid: Int? = null,
    @SerialName("raion_uid") val raionUid: Int? = null,
)

/** Рядок school_alert_state: A / P / N, з якого часу, остання перевірка. */
@Serializable
data class NetworkAlertState(val uid: Int, val status: String, val since: String, @SerialName("checked_at") val checkedAt: String)

/** Обране місце і його стан; [state] == null — сервер ще не перевіряв це місце. */
data class NetworkAlert(val location: NetworkAlertLocation, val state: NetworkAlertState?)

/** Розклад дитини (school_timetables). Формат — див. supabase/migrations/20261108_school_timetables.sql. */
@Serializable
data class NetworkTimetable(
    @SerialName("child_id") val childId: String,
    val bells: List<List<String>>,
    val week: List<List<NetworkLesson>>,
    val holidays: List<List<String>> = emptyList(),
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class NetworkLesson(@SerialName("s") val subject: String, @SerialName("o") val optional: Boolean = false)

data class NetworkUser(val id: String, val email: String, val fullName: String)

sealed interface NetworkAuthState {
    data object Initializing : NetworkAuthState

    data object SignedOut : NetworkAuthState

    data class SignedIn(val user: NetworkUser) : NetworkAuthState
}

/** INSERT — створити, якщо немає (ідемпотентно); UPSERT — створити або повністю замінити. */
enum class WriteKind { INSERT, UPSERT, UPDATE, DELETE }

/** Одна зміна для сервера; вставки ідемпотентні за клієнтським id. */
data class NetworkWrite(
    val table: String,
    val kind: WriteKind,
    val rowId: String,
    val body: JsonObject,
    /** Колонка-ключ рядка: для більшості таблиць `id`, для розкладу — `child_id`. */
    val key: String = "id",
)

sealed interface WriteResult {
    data object Ok : WriteResult

    /** Немає мережі / сервер недоступний — спробувати пізніше. */
    data object Retry : WriteResult

    /** Сервер відхилив (RLS, валідація) — повтор не допоможе. */
    data class Rejected(val message: String) : WriteResult
}
