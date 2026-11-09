package com.gifwarno.rozklad.core.network.supabase

import java.net.URI
import java.net.URLDecoder

/** Розбір адреси повернення після OAuth: ?code=… або ?error=…&error_description=… (параметри можуть бути й у #фрагменті). */
internal data class OAuthCallback(val code: String?, val error: String?) {
    companion object {
        fun parse(uri: String): OAuthCallback {
            val u = runCatching { URI(uri) }.getOrNull() ?: return OAuthCallback(null, "Некоректна адреса повернення")
            val params = listOfNotNull(u.rawQuery, u.rawFragment)
                .flatMap { it.split('&') }
                .mapNotNull { p ->
                    p.split('=', limit = 2).takeIf { it.size == 2 }?.let { (k, v) ->
                        k to
                            URLDecoder.decode(v, Charsets.UTF_8)
                    }
                }
                .toMap()
            val error = params["error_description"] ?: params["error"]
            return OAuthCallback(code = params["code"]?.takeIf { it.isNotBlank() }, error = error)
        }
    }
}
