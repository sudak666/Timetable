package com.gifwarno.rozklad.core.network

import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Чи витікав пароль в інтернет — база Have I Been Pwned (безкоштовна заміна платної перевірки Supabase).
 * k-анонімність: з пристрою йде лише перший 5-символьний шматок SHA-1, сервіс повертає всі суфікси з таким
 * початком, а збіг ми шукаємо самі. Ні пароль, ні його повний хеш нікуди не потрапляють.
 */
internal object LeakedPasswords {
    /** true — пароль у базі злитих; false — ні; null — перевірити не вдалося (немає мережі): не заважаємо. */
    fun isLeaked(password: String): Boolean? {
        val hash = sha1(password)
        val body = runCatching { fetchRange(hash.take(PREFIX)) }.getOrNull() ?: return null
        return leakCount(body, hash.drop(PREFIX)) > 0
    }

    fun sha1(s: String): String = buildString {
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8)).forEach { b ->
            val v = b.toInt()
            append(HEX[(v shr NIBBLE) and LOW])
            append(HEX[v and LOW])
        }
    }

    /** Відповідь — рядки «СУФІКС:КІЛЬКІСТЬ»; рядки-заповнювачі (Add-Padding) мають кількість 0. */
    fun leakCount(body: String, suffix: String): Int = body.lineSequence()
        .map { it.trim().split(':') }
        .firstOrNull { it.size == 2 && it[0].equals(suffix, ignoreCase = true) }
        ?.get(1)?.toIntOrNull() ?: 0

    private fun fetchRange(prefix: String): String {
        val c = URL("$RANGE_URL$prefix").openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            // Відповіді однакового розміру — за трафіком не видно, який був префікс.
            c.setRequestProperty("Add-Padding", "true")
            c.setRequestProperty("User-Agent", "Dvanadtsiatka-Android")
            check(c.responseCode == HttpURLConnection.HTTP_OK) { "HIBP ${c.responseCode}" }
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private const val HEX = "0123456789ABCDEF"
    private const val NIBBLE = 4
    private const val LOW = 0xF
    private const val RANGE_URL = "https://api.pwnedpasswords.com/range/"
    private const val PREFIX = 5
    private const val TIMEOUT_MS = 4_000
}

/** Новий пароль знайдено в базі злитих — Supabase його не отримує. */
class LeakedPasswordException : IllegalArgumentException("Цей пароль уже витікав в інтернеті — придумай інший")
