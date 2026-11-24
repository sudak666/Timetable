package com.gifwarno.rozklad.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UserMessageTest {
    @Test
    fun `вимоги Supabase до пароля — українською`() {
        assertEquals("Пароль має бути від 8 символів", UserMessage.of(IllegalStateException("Password should be at least 8 characters.")))
        assertEquals(
            "Цей пароль занадто простий або вже витікав — придумай інший",
            UserMessage.of(IllegalStateException("Password is known to be weak and easy to guess, please choose a different one.")),
        )
    }

    @Test
    fun `частий запит листа — зачекати, а не технічний текст`() {
        val wait = "Лист уже надсилали щойно — зачекай хвилину й перевір пошту 📬"
        assertEquals(wait, UserMessage.of(IllegalStateException("For security purposes, you can only request this after 42 seconds.")))
        assertEquals(wait, UserMessage.of(IllegalStateException("email rate limit exceeded")))
    }

    @Test
    fun `злитий пароль — наше повідомлення як є`() {
        assertEquals(
            "Цей пароль уже витікав в інтернеті — придумай інший",
            UserMessage.of(IllegalArgumentException("Цей пароль уже витікав в інтернеті — придумай інший")),
        )
    }
}
