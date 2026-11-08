package com.gifwarno.rozklad.core.data.mapper

import com.gifwarno.rozklad.core.database.ChallengeEntity
import com.gifwarno.rozklad.core.database.FamilyEntity
import com.gifwarno.rozklad.core.database.GradeEntity
import com.gifwarno.rozklad.core.database.HomeworkEntity
import com.gifwarno.rozklad.core.database.LedgerEntity
import com.gifwarno.rozklad.core.database.MemberEntity
import com.gifwarno.rozklad.core.domain.Rewards
import com.gifwarno.rozklad.core.model.Challenge
import com.gifwarno.rozklad.core.model.Family
import com.gifwarno.rozklad.core.model.Grade
import com.gifwarno.rozklad.core.model.GradeStatus
import com.gifwarno.rozklad.core.model.Homework
import com.gifwarno.rozklad.core.model.LedgerEntry
import com.gifwarno.rozklad.core.model.LedgerKind
import com.gifwarno.rozklad.core.model.Member
import com.gifwarno.rozklad.core.model.RewardSettings
import com.gifwarno.rozklad.core.model.Role
import com.gifwarno.rozklad.core.network.model.NetworkChallenge
import com.gifwarno.rozklad.core.network.model.NetworkFamily
import com.gifwarno.rozklad.core.network.model.NetworkGrade
import com.gifwarno.rozklad.core.network.model.NetworkHomework
import com.gifwarno.rozklad.core.network.model.NetworkLedger
import com.gifwarno.rozklad.core.network.model.NetworkMember
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/* network → entity (зберігаємо) → model (показуємо). Жодна інша частина застосунку не бачить DTO. */

internal fun parseInstant(s: String): Instant = try {
    OffsetDateTime.parse(s).toInstant()
} catch (_: DateTimeParseException) {
    runCatching { Instant.parse(s) }.getOrDefault(Instant.EPOCH)
}

internal fun NetworkFamily.asEntity() = FamilyEntity(id, name, inviteCode, (rates ?: JsonObject(emptyMap())).toString())

internal fun NetworkMember.asEntity(familyId: String) = MemberEntity(
    familyId = familyId,
    userId = userId,
    role = role,
    name = name,
    goalTitle = goalTitle.orEmpty(),
    goalAmount = goalAmount ?: 0,
    avatar = avatar.orEmpty(),
    theme = theme.orEmpty(),
)

internal fun NetworkGrade.asEntity(familyId: String) =
    GradeEntity(id, familyId, childId, subject, grade, date, status, amount, parseInstant(createdAt).toEpochMilli())

internal fun NetworkHomework.asEntity(familyId: String) = HomeworkEntity(id, familyId, childId, subject, due, text, done)

internal fun NetworkLedger.asEntity(familyId: String) =
    LedgerEntity(id, familyId, childId, kind, amount, note.orEmpty(), date, parseInstant(createdAt).toEpochMilli())

internal fun NetworkChallenge.asEntity(familyId: String) =
    ChallengeEntity(id, familyId, childId, title, subject, minGrade, need, reward, startDate, endDate)

internal fun FamilyEntity.asModel() = Family(id, name, inviteCode)

internal fun FamilyEntity.settings(json: Json): RewardSettings {
    val obj = runCatching { json.parseToJsonElement(settingsJson) as? JsonObject }.getOrNull() ?: return Rewards.DEFAULT_SETTINGS
    val flat = obj.mapValues { (_, v) -> (v as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content }
    return Rewards.parseSettings(flat)
}

internal fun roleOf(s: String) = if (s == "parent") Role.PARENT else Role.CHILD

internal fun MemberEntity.asModel() = Member(userId, roleOf(role), name, goalTitle, goalAmount, avatar, theme)

internal fun GradeEntity.asModel() = Grade(
    id = id,
    childId = childId,
    subject = subject,
    value = value,
    date = LocalDate.parse(date),
    status = if (status == "approved") GradeStatus.APPROVED else GradeStatus.PENDING,
    amount = amount,
    createdAt = Instant.ofEpochMilli(createdAt),
)

internal fun HomeworkEntity.asModel() = Homework(id, childId, subject, LocalDate.parse(due), text, done)

internal fun LedgerEntity.asModel() = LedgerEntry(
    id = id,
    childId = childId,
    kind = if (kind == "payout") LedgerKind.PAYOUT else LedgerKind.BONUS,
    amount = amount,
    note = note,
    date = LocalDate.parse(date),
    createdAt = Instant.ofEpochMilli(createdAt),
)

internal fun ChallengeEntity.asModel() =
    Challenge(id, childId, title, subject, minGrade, need, reward, LocalDate.parse(startDate), LocalDate.parse(endDate))
