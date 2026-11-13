package com.forma.app.ui.screens.evolution

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forma.app.data.model.AIRecommendedRoutine
import com.forma.app.data.model.EvolutionAIAnalysis
import com.forma.app.data.model.SuggestedNutritionTargets
import com.forma.app.ui.components.FormaBadge
import com.forma.app.ui.components.FormaBannerStyle
import com.forma.app.ui.components.FormaBanner
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.FormaEmptyState
import com.forma.app.ui.components.FormaTintedCard
import com.forma.app.ui.components.formaPlainClickable
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography

// Porte de Forma/Views/Evolution/EvolutionAICoachSection.swift

/** Abre uma URL (https/mailto) no app externo adequado, ignorando se não houver handler. */
internal fun openExternalUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // sem aplicativo disponível
    }
}

private fun cleanText(text: String): String = text.trim()

@Composable
fun EvolutionAICoachSection(
    analysis: EvolutionAIAnalysis?,
    isAnalyzing: Boolean,
    errorMessage: String?,
    appliedTargetsSuccessfully: Boolean,
    savedRoutineSuccessfully: Boolean,
    onGenerateAnalysis: () -> Unit,
    onApplyTargets: (SuggestedNutritionTargets) -> Unit,
    onSaveRoutine: (AIRecommendedRoutine) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FormaTheme.Colors
    val context = LocalContext.current
    val horizontal = FormaTheme.Spacing.screenHorizontal

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FormaTheme.Spacing.sectionGap)
    ) {
        CoachHeader(modifier = Modifier.padding(horizontal = horizontal))
        ActionButton(
            analysis = analysis,
            isAnalyzing = isAnalyzing,
            onClick = onGenerateAnalysis,
            modifier = Modifier.padding(horizontal = horizontal)
        )

        if (!errorMessage.isNullOrEmpty()) {
            FormaBanner(
                message = errorMessage,
                style = FormaBannerStyle.Error,
                retryTitle = "Tentar novamente",
                onRetry = if (isAnalyzing) null else onGenerateAnalysis,
                modifier = Modifier.padding(horizontal = horizontal)
            )
        }

        if (analysis != null) {
            Column(
                modifier = Modifier.padding(horizontal = horizontal),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DiagnosticCard(analysis)

                analysis.suggestedTargets?.let { SuggestedTargetsCard(it, appliedTargetsSuccessfully, onApplyTargets) }

                analysis.recommendedRoutine?.let { RecommendedRoutineCard(it, savedRoutineSuccessfully, onSaveRoutine) }

                if (analysis.nutritionSuggestions.isNotEmpty()) {
                    SuggestionsListCard(
                        title = "Alimentação & Horários",
                        icon = "fork.knife",
                        tint = colors.energyGreen,
                        bulletIcon = "checkmark.circle.fill",
                        items = analysis.nutritionSuggestions
                    )
                }

                if (analysis.workoutSuggestions.isNotEmpty()) {
                    SuggestionsListCard(
                        title = "Treinos & Recuperação",
                        icon = "dumbbell.fill",
                        tint = colors.warning,
                        bulletIcon = "bolt.fill",
                        items = analysis.workoutSuggestions
                    )
                }

                HydrationCard(analysis.hydrationDiagnostic)
                WeeklyActionPlanCard(analysis.weeklyActionPlan)
            }
        } else if (!isAnalyzing) {
            FormaCard(modifier = Modifier.padding(horizontal = horizontal), padding = 28.dp) {
                FormaEmptyState(
                    icon = symbolFor("brain.head.profile"),
                    title = "Nenhuma análise gerada ainda",
                    subtitle = "Toque no botão acima para analisar seu balanço calórico, treinos, hidratação e metas com a IA do Google Gemini."
                )
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = horizontal),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = symbolFor("info.circle"),
                    contentDescription = null,
                    tint = colors.secondaryText,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(13.dp)
                )
                Text(
                    text = "Análises geradas pela IA do Google Gemini (Google LLC) com caráter exclusivamente informativo e educacional. Não realizam diagnóstico médico/nutricional nem substituem acompanhamento de médico, nutricionista ou profissional de educação física.",
                    color = colors.secondaryText,
                    style = FormaTypography.caption2
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CitationLink("Mifflin-St Jeor (PubMed)", "https://pubmed.ncbi.nlm.nih.gov/2305711/")
                CitationLink(
                    "Tabela TACO",
                    "https://www.cfn.org.br/wp-content/uploads/2017/03/taco_4_edicao_ampliada_e_revisada.pdf"
                )
                CitationLink("OMS (WHO)", "https://www.who.int/news-room/fact-sheets/detail/physical-activity")
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Reportar IA",
                    color = colors.secondaryText,
                    style = FormaTypography.caption2.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.formaPlainClickable {
                        openExternalUrl(context, "mailto:joaopedroc035@gmail.com?subject=Relato%20IA%20Forma")
                    }
                )
            }
        }
    }
}

@Composable
internal fun CitationLink(title: String, url: String) {
    val context = LocalContext.current
    Text(
        text = title,
        color = FormaTheme.Colors.brand,
        style = FormaTypography.caption2.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.formaPlainClickable { openExternalUrl(context, url) }
    )
}

@Composable
private fun CoachHeader(modifier: Modifier = Modifier) {
    val colors = FormaTheme.Colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                imageVector = symbolFor("sparkles"),
                contentDescription = null,
                tint = colors.brand,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Análise de Evolução • IA",
                color = colors.primaryText,
                style = FormaTypography.rounded(18f, FontWeight.Bold),
                modifier = Modifier.weight(1f)
            )
            FormaBadge(text = "Google Gemini", icon = symbolFor("sparkles"), tint = colors.aiAccent)
        }
        Text(
            text = "Utilizamos a IA do Google Gemini para cruzar seu histórico real de refeições, treinos, passos, água e pesagens e calibrar seu plano.",
            color = colors.secondaryText,
            style = FormaTypography.caption
        )
    }
}

@Composable
private fun ActionButton(
    analysis: EvolutionAIAnalysis?,
    isAnalyzing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FormaTheme.Colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isAnalyzing) 0.6f else 1f)
            .shadow(8.dp, shape, ambientColor = colors.brand.copy(alpha = 0.26f), spotColor = colors.brand.copy(alpha = 0.26f))
            .clip(shape)
            .background(colors.brand)
            .formaPlainClickable(enabled = !isAnalyzing, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isAnalyzing) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            Text(
                text = "Analisando sua evolução com Google Gemini...",
                color = Color.White,
                style = FormaTypography.rounded(14f, FontWeight.Bold),
                maxLines = 1
            )
        } else {
            Icon(
                imageVector = symbolFor(if (analysis == null) "wand.and.stars" else "arrow.triangle.2.circlepath"),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = if (analysis == null) "Analisar com IA Google Gemini" else "Recalcular com Google Gemini",
                color = Color.White,
                style = FormaTypography.rounded(14f, FontWeight.Bold),
                maxLines = 1
            )
        }
    }
}

private val SubheadlineLs = FormaTypography.subheadline.copy(lineHeight = 23.sp)

@Composable
private fun DiagnosticCard(analysis: EvolutionAIAnalysis) {
    val colors = FormaTheme.Colors
    val summary = cleanText(analysis.statusSummary)
    val isShortBadge = summary.length <= 58

    FormaCard(modifier = Modifier.fillMaxWidth(), padding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(symbolFor("target"), null, tint = colors.brand, modifier = Modifier.size(14.dp))
                Text(
                    text = "Análise de Balanço Calórico",
                    color = colors.primaryText,
                    style = FormaTypography.system(15f, FontWeight.Bold)
                )
            }

            if (isShortBadge) {
                FormaBadge(text = summary, icon = symbolFor("sparkles"), tint = colors.energyGreen)
            } else {
                FormaTintedCard(
                    tint = colors.brand,
                    modifier = Modifier.fillMaxWidth(),
                    padding = 12.dp,
                    cornerRadius = 12.dp
                ) {
                    Text(
                        text = summary,
                        color = colors.primaryText,
                        style = SubheadlineLs.copy(fontWeight = FontWeight.Medium),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Text(
                text = cleanText(analysis.calorieDiagnostic),
                color = colors.primaryText.copy(alpha = 0.92f),
                style = SubheadlineLs
            )

            if (analysis.macroDiagnostic.isNotEmpty()) {
                HorizontalDivider(color = colors.primary.copy(alpha = 0.06f))
                Text(
                    text = cleanText(analysis.macroDiagnostic),
                    color = colors.secondaryText,
                    style = FormaTypography.caption.copy(lineHeight = 18.sp)
                )
            }
        }
    }
}

@Composable
private fun SuggestedTargetsCard(
    targets: SuggestedNutritionTargets,
    applied: Boolean,
    onApply: (SuggestedNutritionTargets) -> Unit
) {
    val colors = FormaTheme.Colors
    FormaCard(modifier = Modifier.fillMaxWidth(), padding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(symbolFor("bolt.heart.fill"), null, tint = colors.brand, modifier = Modifier.size(15.dp))
                    Text(
                        text = "Metas Diárias Sugeridas",
                        color = colors.primaryText,
                        style = FormaTypography.system(16f, FontWeight.Bold)
                    )
                }
                FormaBadge(text = "Recomendado", tint = colors.brand)
            }

            Text(
                text = cleanText(targets.explanation),
                color = colors.primaryText.copy(alpha = 0.9f),
                style = FormaTypography.subheadline.copy(lineHeight = 22.sp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TargetTile("Calorias Alvo", "${targets.dailyCalories.toIntSafe()} kcal", colors.brand, Modifier.weight(1f))
                    TargetTile("Meta de Água", "${targets.waterMilliliters} ml", colors.water, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TargetTile("Proteína", "${targets.proteinGrams.toIntSafe()}g", colors.protein, Modifier.weight(1f))
                    TargetTile("Carboidrato", "${targets.carbsGrams.toIntSafe()}g", colors.carbs, Modifier.weight(1f))
                    TargetTile("Gordura", "${targets.fatGrams.toIntSafe()}g", colors.fat, Modifier.weight(1f))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(if (applied) colors.energyGreen else colors.brand)
                    .formaPlainClickable { onApply(targets) }
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = symbolFor(if (applied) "checkmark.circle.fill" else "slider.horizontal.3"),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = if (applied) "Metas Aplicadas ao Perfil!" else "Aplicar Novas Metas ao Perfil",
                    color = Color.White,
                    style = FormaTypography.rounded(13f, FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun TargetTile(title: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    val colors = FormaTheme.Colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(tint.copy(alpha = 0.08f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = title,
            color = colors.secondaryText,
            style = FormaTypography.caption2.copy(fontWeight = FontWeight.Medium)
        )
        Text(text = value, color = tint, style = FormaTypography.rounded(15f, FontWeight.Bold), maxLines = 1)
    }
}

@Composable
private fun RecommendedRoutineCard(
    routine: AIRecommendedRoutine,
    saved: Boolean,
    onSave: (AIRecommendedRoutine) -> Unit
) {
    val colors = FormaTheme.Colors
    FormaCard(modifier = Modifier.fillMaxWidth(), padding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FormaBadge(text = "Ficha Recomendada", icon = symbolFor("sparkles"), tint = colors.aiAccent)
                Text(
                    text = routine.title,
                    color = colors.primaryText,
                    style = FormaTypography.rounded(16f, FontWeight.Bold)
                )
                if (routine.targetMuscles.isNotEmpty()) {
                    Text(text = routine.targetMuscles, color = colors.secondaryText, style = FormaTypography.caption)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormaBadge(
                    text = "~${routine.estimatedCalories.toIntSafe()} kcal",
                    icon = symbolFor("flame.fill"),
                    tint = colors.energyGreen
                )
                FormaBadge(
                    text = "${routine.estimatedDurationMinutes} min",
                    icon = symbolFor("clock"),
                    tint = colors.brand
                )
                FormaBadge(
                    text = "${routine.exercises.size} exercícios",
                    icon = symbolFor("dumbbell.fill"),
                    tint = colors.warning
                )
            }

            HorizontalDivider(color = colors.primary.copy(alpha = 0.06f))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                routine.exercises.take(5).forEach { ex ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = ex.name,
                                color = colors.primaryText,
                                style = FormaTypography.system(13f, FontWeight.SemiBold)
                            )
                            Text(
                                text = "${ex.sets}x ${ex.reps} • ${if (ex.suggestedWeight.isEmpty()) "${ex.restSeconds}s descanso" else ex.suggestedWeight}",
                                color = colors.secondaryText,
                                style = FormaTypography.caption2
                            )
                        }
                        if (ex.animationId != null) {
                            Icon(
                                imageVector = symbolFor("play.circle.fill"),
                                contentDescription = null,
                                tint = colors.brand.copy(alpha = 0.75f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(if (saved) colors.energyGreen else colors.warning)
                    .formaPlainClickable { onSave(routine) }
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = symbolFor(if (saved) "checkmark.circle.fill" else "plus.circle.fill"),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = if (saved) "Ficha Salva em Treinos!" else "Adicionar à Aba de Treinos",
                    color = Color.White,
                    style = FormaTypography.rounded(13f, FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun SuggestionsListCard(
    title: String,
    icon: String,
    tint: Color,
    bulletIcon: String,
    items: List<String>
) {
    val colors = FormaTheme.Colors
    FormaCard(modifier = Modifier.fillMaxWidth(), padding = 18.dp) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(symbolFor(icon), null, tint = tint, modifier = Modifier.size(14.dp))
                Text(text = title, color = colors.primaryText, style = FormaTypography.system(15f, FontWeight.Bold))
            }
            items.forEach { tip ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = symbolFor(bulletIcon),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .size(12.dp)
                    )
                    Text(
                        text = cleanText(tip),
                        color = colors.primaryText,
                        style = FormaTypography.subheadline.copy(lineHeight = 22.sp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HydrationCard(text: String) {
    val colors = FormaTheme.Colors
    FormaCard(modifier = Modifier.fillMaxWidth(), padding = 18.dp) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(symbolFor("drop.fill"), null, tint = colors.water, modifier = Modifier.size(14.dp))
                Text(
                    text = "Consumo de Água & Hidratação",
                    color = colors.primaryText,
                    style = FormaTypography.system(15f, FontWeight.Bold)
                )
            }
            Text(
                text = cleanText(text),
                color = colors.primaryText,
                style = FormaTypography.subheadline.copy(lineHeight = 22.sp)
            )
        }
    }
}

@Composable
private fun WeeklyActionPlanCard(text: String) {
    val colors = FormaTheme.Colors
    FormaTintedCard(
        tint = colors.brand,
        modifier = Modifier.fillMaxWidth(),
        padding = 18.dp,
        cornerRadius = FormaTheme.Radius.card
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(symbolFor("flag.checkered"), null, tint = colors.brand, modifier = Modifier.size(14.dp))
                Text(
                    text = "Plano de Ação para os Próximos 7 Dias",
                    color = colors.primaryText,
                    style = FormaTypography.system(15f, FontWeight.Bold)
                )
            }
            Text(text = cleanText(text), color = colors.primaryText, style = SubheadlineLs)
        }
    }
}
