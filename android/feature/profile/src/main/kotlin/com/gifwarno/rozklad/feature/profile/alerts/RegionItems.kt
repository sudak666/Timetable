package com.gifwarno.rozklad.feature.profile.alerts

import com.gifwarno.rozklad.core.model.AlertLocation
import com.gifwarno.rozklad.core.model.AlertLocationKind
import java.text.Collator
import java.util.Locale

/** Рядок списку в межах області: район (заголовок, теж можна обрати) або громада під ним. */
sealed interface RegionItem {
    val location: AlertLocation

    data class Raion(override val location: AlertLocation) : RegionItem

    data class Hromada(override val location: AlertLocation) : RegionItem
}

private val UK = Locale.forLanguageTag("uk-UA")

/** Порядок українського алфавіту (за кодами символів «І» опинилась би після «Х»). */
internal val ukrainianOrder: Comparator<String> = Collator.getInstance(UK).let { c -> Comparator { a, b -> c.compare(a, b) } }

/** Коротша назва для списку: «… територіальна громада» → «… громада». На сервері лишається повна. */
fun shortName(name: String): String = name.replace(" територіальна громада", " громада")

/**
 * Райони з громадами під кожним, за абеткою. Пошук — за частиною назви (без регістру):
 * знайдений район показуємо з усіма громадами, інакше — лише знайдені громади з їхнім районом.
 */
fun buildRegionItems(children: List<AlertLocation>, query: String): List<RegionItem> {
    val q = query.trim().lowercase(UK)
    fun matches(l: AlertLocation) = q.isEmpty() || l.name.lowercase(UK).contains(q)
    val hromadas = children.filter { it.kind == AlertLocationKind.HROMADA }.groupBy { it.raionUid }
    return children
        .filter { it.kind == AlertLocationKind.RAION }
        .sortedWith(compareBy(ukrainianOrder) { it.name })
        .flatMap { raion ->
            val inside = hromadas[raion.uid].orEmpty().sortedWith(compareBy(ukrainianOrder) { shortName(it.name) })
            val shown = if (matches(raion)) inside else inside.filter(::matches)
            if (!matches(raion) && shown.isEmpty()) {
                emptyList()
            } else {
                listOf(RegionItem.Raion(raion)) + shown.map { RegionItem.Hromada(it) }
            }
        }
}
