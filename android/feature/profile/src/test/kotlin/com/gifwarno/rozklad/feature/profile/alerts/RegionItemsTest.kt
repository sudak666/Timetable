package com.gifwarno.rozklad.feature.profile.alerts

import com.gifwarno.rozklad.core.model.AlertLocation
import com.gifwarno.rozklad.core.model.AlertLocationKind
import org.junit.Assert.assertEquals
import org.junit.Test

class RegionItemsTest {
    private fun raion(uid: Int, name: String) = AlertLocation(uid, name, AlertLocationKind.RAION, 22, null)

    private fun hromada(uid: Int, name: String, raion: Int) = AlertLocation(uid, name, AlertLocationKind.HROMADA, 22, raion)

    private val children = listOf(
        hromada(1293, "м. Харків та Харківська територіальна громада", 124),
        raion(124, "Харківський район"),
        hromada(1300, "Дергачівська територіальна громада", 124),
        raion(120, "Ізюмський район"),
        hromada(1200, "Балаклійська територіальна громада", 120),
    )

    private fun names(items: List<RegionItem>) = items.map { (if (it is RegionItem.Raion) "# " else "") + it.location.name }

    @Test
    fun `райони за абеткою, громади під своїм районом`() {
        assertEquals(
            listOf(
                "# Ізюмський район",
                "Балаклійська територіальна громада",
                "# Харківський район",
                "Дергачівська територіальна громада",
                "м. Харків та Харківська територіальна громада",
            ),
            names(buildRegionItems(children, "")),
        )
    }

    @Test
    fun `пошук громади показує її разом із районом`() {
        assertEquals(
            listOf("# Харківський район", "м. Харків та Харківська територіальна громада"),
            names(buildRegionItems(children, "  харків та ")),
        )
    }

    @Test
    fun `знайдений район — з усіма громадами`() {
        assertEquals(listOf("# Ізюмський район", "Балаклійська територіальна громада"), names(buildRegionItems(children, "ізюм")))
    }

    @Test
    fun `нічого не знайдено — порожньо`() {
        assertEquals(emptyList<String>(), names(buildRegionItems(children, "Львів")))
    }

    @Test
    fun `коротка назва громади`() {
        assertEquals("Дергачівська громада", shortName("Дергачівська територіальна громада"))
    }
}
