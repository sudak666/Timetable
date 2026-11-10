package com.gifwarno.rozklad.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler

/** Публічні адреси (ті самі, що вказуються в Google Play Console). */
object Links {
    const val SITE = "https://sudak666.github.io/Timetable/"
    const val PRIVACY_POLICY = "https://sudak666.github.io/Timetable/privacy.html"
}

@Composable
fun PrivacyPolicyLink(modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    TextButton(onClick = { uri.openUri(Links.PRIVACY_POLICY) }, modifier = modifier) {
        Text("Політика конфіденційності", style = MaterialTheme.typography.bodySmall)
    }
}

/** Текст запрошення в сім'ю — як у вебі («Надіслати запрошення»). */
fun inviteText(code: String): String = "Приєднуйся до нашого розкладу: ${Links.SITE} — увійди й введи код $code"

/** Системне вікно «Поділитися» з запрошенням (месенджери, пошта тощо). */
fun shareInvite(context: android.content.Context, code: String) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(android.content.Intent.EXTRA_TEXT, inviteText(code))
    context.startActivity(android.content.Intent.createChooser(send, "Надіслати запрошення"))
}
