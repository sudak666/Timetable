package com.gifwarno.rozklad.core.network.supabase

import java.net.URLDecoder

/** Розбір адреси повернення після OAuth: ?code=… або ?error=…&error_description=… (параметри можуть бути й у #фрагменті). */
internal data class OAuthCallback(val code: String?, val error: String?) {
    companion object {
        /**
         * Розбираємо рядок вручну, а не через java.net.URI: той відкидає всю адресу через одне
         * зіпсоване «%», і разом з ним губиться справжній code.
         */
        fun parse(uri: String): OAuthCallback {
            val query = uri.substringAfter('?', "").substringBefore('#')
            val fragment = uri.substringAfter('#', "")
            val params = listOf(query, fragment)
                .flatMap { it.split('&') }
                .mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { (k, v) -> k to decode(v) } }
                .toMap()
            val error = params["error_description"] ?: params["error"]
            return OAuthCallback(code = params["code"]?.takeIf { it.isNotBlank() }, error = error?.takeIf { it.isNotBlank() })
        }

        /**
         * Перевантаження з Charset є лише з API 33 (minSdk 26) — тож назва кодування рядком.
         * Зіпсоване «%» у значенні не повинно валити вхід: беремо значення як є.
         */
        private fun decode(v: String): String = runCatching { URLDecoder.decode(v, "UTF-8") }.getOrDefault(v)
    }
}
