package com.gifwarno.rozklad.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun EmptyState(
    emoji: String,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    // Картка посередині — читається і на тлі «арени», і на звичайному.
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        GameSurface {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Велике емодзі — щоб картка не здавалася порожньою.
                Text(emoji, style = MaterialTheme.typography.displayLarge)
                Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                if (message != null) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                if (actionLabel != null && onAction != null) {
                    GameButton(actionLabel, onAction, Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

/** Кругла «плашка» з емодзі на кольоровому тлі — іконка предмета. */
@Composable
fun EmojiBadge(emoji: String, color: Color, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Surface(
        modifier = modifier.size(44.dp).semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        shape = CircleShape,
        color = color.copy(alpha = 0.16f),
    ) {
        Box(contentAlignment = Alignment.Center) { Text(emoji, style = MaterialTheme.typography.titleLarge) }
    }
}

/** Оцінка в кружечку з кольором за рівнем. */
@Composable
fun ValueBadge(label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.size(44.dp), shape = CircleShape, color = color) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
