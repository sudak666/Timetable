package com.gifwarno.rozklad.core.domain

import com.gifwarno.rozklad.core.model.Grade
import java.time.LocalDate

/** Нагорода на шляху трофеїв: аватар або колір, що відкривається на рівні. */
sealed interface TrophyReward {
    data class Avatar(val emoji: String) : TrophyReward

    data class Accent(val color: Long) : TrophyReward
}

data class RoadStep(val level: Int, val reward: TrophyReward, val got: Boolean, val next: Boolean, val xpNeeded: Int)

data class Badge(val emoji: String, val title: String, val got: Boolean)

/** Трофеї, значки, аватари й кольори — 1:1 з вебом (src/features/rewards.ts, src/views/grades.ts). */
object Trophies {
    /** Аватар → рівень, з якого він доступний. */
    val AVATARS: List<Pair<String, Int>> = listOf(
        "🦊" to 1, "🐼" to 1, "🐸" to 1, "🐙" to 2, "🐯" to 2, "🦁" to 3, "🐺" to 3, "🦉" to 4,
        "🐧" to 4, "🦄" to 5, "🤖" to 6, "👽" to 7, "🦖" to 8, "🐲" to 10, "👾" to 12, "🦈" to 15,
    )

    /** Колір застосунку (зберігається в school_members.theme як «#rrggbb») → рівень. */
    val THEMES: List<Pair<String, Int>> = listOf(
        "#6c5ce7" to 1,
        "#0869b5" to 1,
        "#00866b" to 2,
        "#c2185b" to 3,
        "#b84a00" to 4,
        "#8a5c00" to 5,
        "#c62828" to 6,
        "#2d3436" to 8,
    )

    /** Батькам відкрито все (як у вебі). */
    const val PARENT_LEVEL = 99

    fun parseColor(hex: String?): Long? {
        val h = hex?.takeIf { it.length == HEX_LEN && it.startsWith("#") } ?: return null
        return h.substring(1).toLongOrNull(RADIX)?.let { OPAQUE or it }
    }

    /** Шлях: на кожному рівні > 1 — перша нагорода цього рівня (спершу аватари, потім кольори). */
    fun road(level: Int): List<RoadStep> {
        val byLevel = linkedMapOf<Int, TrophyReward>()
        AVATARS.forEach { (a, l) -> byLevel.putIfAbsent(l, TrophyReward.Avatar(a)) }
        THEMES.forEach { (c, l) -> byLevel.putIfAbsent(l, TrophyReward.Accent(checkNotNull(parseColor(c)))) }
        val levels = byLevel.keys.filter { it > 1 }.sorted()
        val next = levels.firstOrNull { it > level }
        return levels.map { l ->
            RoadStep(l, checkNotNull(byLevel[l]), got = l <= level, next = l == next, xpNeeded = (l - 1) * Rewards.XP_PER_LEVEL)
        }
    }

    /**
     * Досягнення з підтверджених оцінок дитини.
     * [earned] — усього зароблено (Totals.earned), [weekStart] — понеділок поточного тижня.
     */
    fun badges(approved: List<Grade>, earned: Int, rates: Map<Int, Int>, weekStart: LocalDate): List<Badge> {
        val best = Rewards.bestTenRun(approved)
        val week = approved.filter { it.date >= weekStart }
        return listOf(
            Badge("🎯", "Перша оцінка", approved.isNotEmpty()),
            Badge("👑", "Перша 12", approved.any { it.value == TOP_GRADE }),
            Badge("🔥", "3 десятки поспіль", best >= THREE),
            Badge("🚀", "5 поспіль ≥10", best >= FIVE),
            Badge("📚", "20 оцінок", approved.size >= TWENTY),
            Badge("💯", "50 оцінок", approved.size >= FIFTY),
            Badge("💰", "Заробив 1000 ₴", earned >= THOUSAND),
            Badge("🌈", "Тиждень без мінусів", week.size >= THREE && week.all { Rewards.gradeValue(it, rates) >= 0 }),
        )
    }

    private const val HEX_LEN = 7
    private const val RADIX = 16
    private const val OPAQUE = 0xFF000000L
    private const val TOP_GRADE = 12
    private const val THREE = 3
    private const val FIVE = 5
    private const val TWENTY = 20
    private const val FIFTY = 50
    private const val THOUSAND = 1000
}
