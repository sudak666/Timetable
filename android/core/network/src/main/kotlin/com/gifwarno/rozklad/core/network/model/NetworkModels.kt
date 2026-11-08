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
)

data class NetworkUser(val id: String, val email: String, val fullName: String)

sealed interface NetworkAuthState {
    data object Initializing : NetworkAuthState

    data object SignedOut : NetworkAuthState

    data class SignedIn(val user: NetworkUser) : NetworkAuthState
}

enum class WriteKind { INSERT, UPDATE, DELETE }

/** Одна зміна для сервера; вставки ідемпотентні за клієнтським id. */
data class NetworkWrite(val table: String, val kind: WriteKind, val rowId: String, val body: JsonObject)

sealed interface WriteResult {
    data object Ok : WriteResult

    /** Немає мережі / сервер недоступний — спробувати пізніше. */
    data object Retry : WriteResult

    /** Сервер відхилив (RLS, валідація) — повтор не допоможе. */
    data class Rejected(val message: String) : WriteResult
}
