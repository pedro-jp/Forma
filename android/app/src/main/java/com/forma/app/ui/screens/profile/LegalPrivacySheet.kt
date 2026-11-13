package com.forma.app.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forma.app.data.services.AIDataConsentManager
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.FormaModalSheet
import com.forma.app.ui.components.FormaSheetTopBar
import com.forma.app.ui.components.formaPlainClickable
import com.forma.app.ui.components.openUrl
import com.forma.app.ui.components.sfSymbol
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography

private const val PRIVACY_URL = "https://forma-94vp.onrender.com/privacy.html"
private const val TERMS_URL = "https://forma-94vp.onrender.com/terms.html"

/**
 * Política de Privacidade, aviso de saúde/nutrição, LGPD e exclusão de conta
 * (porte de `LegalPrivacySheet` do iOS). Uso: `if (show) LegalPrivacySheet(onDismiss = { show = false })`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LegalPrivacySheet(onDismiss: () -> Unit) {
    val colors = FormaTheme.Colors
    val context = LocalContext.current
    val consent = AIDataConsentManager.get(context)
    val granted by consent.hasGrantedConsent.collectAsState()

    FormaModalSheet(onDismissRequest = onDismiss) {
        FormaSheetTopBar(
            title = "Privacidade & Saúde",
            cancelTitle = null,
            confirmTitle = "Fechar",
            onConfirm = onDismiss
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FormaTheme.Spacing.screenHorizontal, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Privacidade & LGPD
            FormaCard(modifier = Modifier.fillMaxWidth(), padding = 18.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LegalHeader(Icons.Filled.PanTool, colors.brand, "Privacidade & LGPD (Lei 13.709/2018)")
                    Text(
                        "O Forma coleta apenas os dados necessários para calcular suas metas diárias e sincronizar seu progresso: nome, e-mail, peso, altura, objetivo corporal, refeições (incluindo fotos ou áudio enviados por você), ingestão de água, treinos e fichas.",
                        color = colors.secondaryText, style = FormaTypography.subheadline
                    )
                    Text(
                        "Controlador: Forma App (João Pedro) • Contato do Encarregado (DPO): joaopedroc035@gmail.com. Dados criptografados via TLS e senhas protegidas por BCrypt, com armazenamento em nuvem internacional segura (Supabase Inc. e Render Services Inc.).",
                        color = colors.secondaryText, style = FormaTypography.caption
                    )
                    Text(
                        "Seus dados nunca são vendidos a terceiros nem usados para publicidade ou rastreamento entre aplicativos.",
                        color = colors.brand,
                        style = FormaTypography.caption.copy(fontWeight = FontWeight.SemiBold)
                    )
                    FlowRow(
                        modifier = Modifier.padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LegalLabelLink("Política de Privacidade", Icons.Filled.OpenInNew) { openUrl(context, PRIVACY_URL) }
                        LegalLabelLink("Termos de Uso (EULA)", Icons.Filled.Description) { openUrl(context, TERMS_URL) }
                    }
                }
            }

            // IA do Google Gemini
            FormaCard(modifier = Modifier.fillMaxWidth(), padding = 18.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegalHeader(sfSymbol("sparkles"), colors.aiAccent, "IA do Google Gemini (Google LLC)")
                    Text(
                        "O Forma utiliza a inteligência artificial do Google Gemini (Google LLC). Quando você autoriza o uso da IA na nuvem, fotos ou descrições de refeições, descrições de treinos, seu nome de exibição, medidas corporais (peso, altura e objetivo) e histórico recente de progresso são enviados sob demanda à Google Gemini API por conexão criptografada exclusivamente para gerar suas estimativas e análises no app.",
                        color = colors.secondaryText, style = FormaTypography.subheadline
                    )
                    Text(
                        "Seu e-mail, senha e credenciais Apple/Google nunca são compartilhados com a IA do Google Gemini.",
                        color = colors.primaryText,
                        style = FormaTypography.caption.copy(fontWeight = FontWeight.SemiBold)
                    )

                    HorizontalDivider(color = colors.separator)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "Autorizar IA do Google Gemini",
                                color = colors.primaryText,
                                style = FormaTypography.subheadline.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                if (granted) "Ativo • Você pode revogar a qualquer momento."
                                else "Desativado • Apenas cálculos locais e base TACO estão em uso.",
                                color = colors.secondaryText, style = FormaTypography.caption
                            )
                        }
                        Switch(
                            checked = granted,
                            onCheckedChange = { consent.setConsent(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = colors.brand
                            )
                        )
                    }
                }
            }

            // Saúde (Health Connect no Android; Apple Saúde no iOS)
            FormaCard(modifier = Modifier.fillMaxWidth(), padding = 18.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LegalHeader(Icons.Filled.Favorite, colors.energyGreen, "Integração com o Health Connect")
                    Text(
                        "Com sua autorização explícita no Android, o Forma lê seus passos, calorias em movimento e treinos registrados no Health Connect, além de salvar os treinos concluídos no app. Você pode gerenciar ou revogar essas permissões a qualquer momento nas Configurações do Android > Health Connect.",
                        color = colors.secondaryText, style = FormaTypography.subheadline
                    )
                }
            }

            // Aviso de saúde e fontes científicas
            FormaCard(modifier = Modifier.fillMaxWidth(), padding = 18.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LegalHeader(Icons.Filled.MonitorHeart, colors.warning, "Aviso de Saúde & Fontes Científicas")
                    Text(
                        "As estimativas calóricas, cálculos de IMC/TMB, metas diárias e orientações geradas com a IA do Google Gemini têm caráter exclusivamente informativo e educacional. O Forma não realiza diagnóstico médico ou clínico e não substitui médico, nutricionista ou profissional de educação física.",
                        color = colors.secondaryText, style = FormaTypography.subheadline
                    )
                    Column(modifier = Modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Referências Científicas Oficiais:",
                            color = colors.primaryText,
                            style = FormaTypography.caption.copy(fontWeight = FontWeight.Bold)
                        )
                        ReferenceLink("• Taxa Metabólica Basal — Equação de Mifflin-St Jeor (PubMed)", "https://pubmed.ncbi.nlm.nih.gov/2305711/")
                        ReferenceLink("• Composição de Alimentos — Tabela TACO 4ª ed. (NEPA/UNICAMP)", "https://www.cfn.org.br/wp-content/uploads/2017/03/taco_4_edicao_ampliada_e_revisada.pdf")
                        ReferenceLink("• Classificação de IMC & Peso Saudável — OMS (WHO)", "https://www.who.int/news-room/fact-sheets/detail/obesity-and-overweight")
                        ReferenceLink("• Gasto Calórico em Exercícios (METs) — Compendium of Physical Activities", "https://pacompendium.com/")
                        ReferenceLink("• Proteínas e Exercício Físico — ISSN Position Stand (PubMed)", "https://pubmed.ncbi.nlm.nih.gov/28642676/")
                    }
                }
            }

            // Direitos LGPD & exclusão
            FormaCard(modifier = Modifier.fillMaxWidth(), padding = 18.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LegalHeader(Icons.Filled.DeleteForever, colors.danger, "Direitos LGPD & Exclusão de Conta")
                    Text(
                        "Você pode excluir permanentemente sua conta, todos os dados armazenados nos nossos servidores e revogar imediatamente os tokens de acesso do Sign in with Apple e do Google a qualquer momento acessando Perfil > Conta > Excluir Minha Conta.",
                        color = colors.secondaryText, style = FormaTypography.subheadline
                    )
                }
            }
        }
    }
}

@Composable
private fun LegalHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(title, color = FormaTheme.Colors.primaryText, style = FormaTypography.headline)
    }
}

@Composable
private fun LegalLabelLink(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val brand = FormaTheme.Colors.brand
    Row(
        modifier = Modifier.formaPlainClickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = brand, modifier = Modifier.size(13.dp))
        Text(title, color = brand, style = FormaTypography.caption.copy(fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun ReferenceLink(title: String, url: String) {
    val context = LocalContext.current
    Text(
        text = title,
        color = FormaTheme.Colors.brand,
        style = FormaTypography.caption,
        modifier = Modifier.formaPlainClickable { openUrl(context, url) }
    )
}
