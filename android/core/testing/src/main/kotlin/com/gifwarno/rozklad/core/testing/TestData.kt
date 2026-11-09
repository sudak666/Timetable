package com.gifwarno.rozklad.core.testing

import com.gifwarno.rozklad.core.domain.Rewards
import com.gifwarno.rozklad.core.model.Family
import com.gifwarno.rozklad.core.model.FamilyContext
import com.gifwarno.rozklad.core.model.FamilyData
import com.gifwarno.rozklad.core.model.Grade
import com.gifwarno.rozklad.core.model.GradeStatus
import com.gifwarno.rozklad.core.model.Member
import com.gifwarno.rozklad.core.model.Role
import com.gifwarno.rozklad.core.model.SyncStatus
import com.gifwarno.rozklad.core.model.UserAccount
import java.time.Instant
import java.time.LocalDate

/** Готові дані для тестів: сім'я з батьком і дитиною. */
object TestData {
    val parentUser = UserAccount("p1", "parent@example.com", "Мама")
    val parent = Member("p1", Role.PARENT, "Мама", "", 0, "🦉", "")
    val child = Member("k1", Role.CHILD, "Сашко", "Велосипед", 3000, "🦊", "")

    fun grade(id: String, value: Int, date: String, status: GradeStatus = GradeStatus.APPROVED) = Grade(
        id = id,
        childId = child.userId,
        subject = "Математика",
        value = value,
        date = LocalDate.parse(date),
        status = status,
        amount = null,
        createdAt = Instant.parse("${date}T10:00:00Z"),
    )

    fun familyData(grades: List<Grade> = emptyList()) = FamilyData(
        family = Family("f1", "Сім’я Мама", "ABC123"),
        members = listOf(parent, child),
        grades = grades,
        ledger = emptyList(),
        challenges = emptyList(),
        homework = emptyList(),
        settings = Rewards.DEFAULT_SETTINGS,
    )

    fun parentContext(data: FamilyData = familyData()) = FamilyContext(
        user = parentUser,
        role = Role.PARENT,
        familyId = "f1",
        data = data,
        child = child,
        sync = SyncStatus(0, null),
    )
}
