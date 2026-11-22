package com.gifwarno.rozklad.core.model

/**
 * Папір сторінки — нагорода за рівні (стиль «Щоденник»): удень це аркуш зошита, уночі — візерунок крейдою на дошці.
 * [key] зберігається в налаштуваннях пристрою й на сайті (localStorage) — не змінювати.
 */
enum class Paper(val key: String, val title: String) {
    GRID("grid", "Клітинка"),
    LINES("lines", "Лінійка"),
    DOTS("dots", "Крапки"),
    GRAPH("graph", "Міліметрівка"),
    KRAFT("kraft", "Крафт"),
    ;

    companion object {
        fun fromKey(key: String?): Paper = entries.firstOrNull { it.key == key } ?: GRID
    }
}
