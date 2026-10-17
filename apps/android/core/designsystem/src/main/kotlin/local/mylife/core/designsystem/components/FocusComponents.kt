package local.mylife.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import local.mylife.core.designsystem.theme.PaperFocusCardBackground
import local.mylife.core.designsystem.theme.PaperFocusCardButtonBorder
import local.mylife.core.designsystem.theme.PaperFocusCardForeground
import local.mylife.core.designsystem.theme.PaperFocusCardLabel
import local.mylife.core.designsystem.theme.PaperFocusCardSubtext
import local.mylife.core.designsystem.theme.PaperForeground
import local.mylife.core.designsystem.theme.PaperOnSurfaceVariant
import local.mylife.core.designsystem.theme.PaperOutline
import local.mylife.core.designsystem.theme.PaperSurface

/**
 * FocusCard: Выделенная графитовая карточка ближайшего дела в стиле «Бумага / Фокус».
 */
@Composable
fun FocusCard(
    title: String,
    modifier: Modifier = Modifier,
    details: String? = null,
    dueTimeText: String? = null,
    label: String = "БЛИЖАЙШЕЕ ДЕЛО",
    actionText: String = "Открыть",
    onActionClick: () -> Unit = {},
    onCardClick: () -> Unit = {},
) {
    Card(
        shape = RoundedCornerShape(19.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = PaperFocusCardBackground,
                contentColor = PaperFocusCardForeground,
            ),
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onCardClick),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.3.sp,
                    color = PaperFocusCardLabel,
                )
                Text(
                    text = "✓",
                    fontSize = 14.sp,
                    color = PaperFocusCardLabel,
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.7).sp,
                lineHeight = 28.sp,
                color = PaperFocusCardForeground,
            )

            if (!details.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = details,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = PaperFocusCardSubtext,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dueTimeText ?: "Без срока",
                    fontSize = 12.sp,
                    color = PaperFocusCardSubtext,
                )

                OutlinedButton(
                    onClick = onActionClick,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, PaperFocusCardButtonBorder),
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = PaperFocusCardForeground,
                        ),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) {
                    Text(
                        text = actionText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
        }
    }
}

/**
 * SectionHeader: Заголовок раздела с опциональной ссылкой "Все".
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: () -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
            color = PaperForeground,
        )

        if (actionText != null) {
            TextButton(
                onClick = onActionClick,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                Text(
                    text = actionText,
                    fontSize = 12.sp,
                    color = PaperForeground,
                )
            }
        }
    }
}

/**
 * EmptyState: Спокойное честное состояние отсутствия данных (без фейковых заглушек).
 */
@Composable
fun FocusEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    actionText: String? = null,
    onActionClick: () -> Unit = {},
) {
    Card(
        shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, PaperOutline),
        colors = CardDefaults.cardColors(containerColor = PaperSurface),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = PaperForeground,
            )

            if (!description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = PaperOnSurfaceVariant,
                )
            }

            if (actionText != null) {
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = onActionClick,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, PaperForeground),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) {
                    Text(
                        text = actionText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PaperForeground,
                    )
                }
            }
        }
    }
}

/**
 * RatingBadge: Бейдж рейтинга (1..10) в дизайне «Бумага».
 */
@Composable
fun RatingBadge(
    rating: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, PaperOutline),
        color = PaperSurface,
        modifier =
            modifier.semantics {
                contentDescription = "Рейтинг $rating из 10"
            },
    ) {
        Text(
            text = "★ $rating/10",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = PaperForeground,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
