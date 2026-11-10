package com.gifwarno.rozklad.feature.grades

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gifwarno.rozklad.core.designsystem.component.GameSegmented
import com.gifwarno.rozklad.core.designsystem.component.GameSurface
import com.gifwarno.rozklad.core.designsystem.theme.GameFont
import com.gifwarno.rozklad.core.designsystem.theme.RozkladTheme
import com.gifwarno.rozklad.core.domain.Rewards
import com.gifwarno.rozklad.core.domain.Timetable
import com.gifwarno.rozklad.core.model.Grade
import com.gifwarno.rozklad.core.ui.gradeColor
import java.time.LocalDate

/** Предмет → дата → натиснув оцінку = збережено. Три дотики, як у вебі. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddGradeSheet(onDismiss: () -> Unit, onAdd: (String, Int, LocalDate) -> Unit) {
    var subject by rememberSaveable { mutableStateOf(Timetable.SUBJECTS.first().name) }
    var daysAgo by rememberSaveable { mutableLongStateOf(0L) }
    val c = RozkladTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("⭐ Нова оцінка", style = MaterialTheme.typography.titleLarge)
            Text("Предмет", style = MaterialTheme.typography.labelLarge, color = c.muted)
            // Три рядки з горизонтальною прокруткою — усі предмети поруч, а оцінки лишаються на екрані.
            LazyHorizontalGrid(
                rows = GridCells.Fixed(SUBJECT_ROWS),
                modifier = Modifier.fillMaxWidth().height((SUBJECT_ROWS * SUBJECT_ROW_DP).dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(Timetable.SUBJECTS, key = { it.name }) { s ->
                    val on = s.name == subject
                    GameSurface(
                        shape = RoundedCornerShape(50),
                        color = if (on) Color(s.color) else c.soft,
                        contentColor = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
                        depth = if (on) 3.dp else 0.dp,
                        borderWidth = if (on) 2.dp else 0.dp,
                        onClick = { subject = s.name },
                        modifier = Modifier.semantics { selected = on },
                    ) {
                        Text(
                            "${s.emoji} ${s.name}",
                            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                    }
                }
            }
            GameSegmented(
                options = listOf(0L to "Сьогодні", 1L to "Вчора", 2L to "Позавчора"),
                selected = daysAgo,
                onSelect = { daysAgo = it },
            )
            Text("Оцінка · ${Timetable.subject(subject).emoji} $subject", style = MaterialTheme.typography.labelLarge, color = c.muted)
            Grade.VALUES.chunked(GRADE_COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { v ->
                        GameSurface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = gradeColor(v),
                            contentColor = Color.White,
                            depth = 4.dp,
                            onClick = { onAdd(subject, v, LocalDate.now().minusDays(daysAgo)) },
                            onClickLabel = "Поставити ${Rewards.gradeLabel(v)}",
                        ) {
                            Text(
                                Rewards.gradeLabel(v),
                                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                fontFamily = GameFont,
                                fontSize = if (v == Grade.PASS) 15.sp else 22.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        }
                    }
                    // Неповний останній рядок — порожні клітинки, щоб плитки мали однакову ширину.
                    repeat(GRADE_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private const val SUBJECT_ROWS = 3
private const val SUBJECT_ROW_DP = 40
private const val GRADE_COLUMNS = 6
