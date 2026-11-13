package com.forma.app.data.services

import android.content.Context
import com.forma.app.data.model.MealType
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Calendar
import java.util.Date
import java.util.UUID

/** Item individual identificado pelo analisador semântico (iOS: `ParsedFoodItem`). */
data class ParsedFoodItem(
    val id: String = UUID.randomUUID().toString(),
    val originalPhrase: String,
    val matchedFoodName: String,
    val brand: String = "",
    val portionValue: Double,
    val unit: String = "g",
    val isLiquid: Boolean = false,
    val calories: Double,
    val protein: Double,
    val carbohydrates: Double,
    val fat: Double,
    val confidence: Double = 0.9,
    val source: String = "TACO Brasil"
) {
    /** Compatibilidade com código legado. */
    val portionGrams: Double get() = portionValue

    companion object {
        /** Construtor legado (iOS `init(... portionGrams:)`): infere líquido pelo nome. */
        fun fromGrams(
            originalPhrase: String,
            matchedFoodName: String,
            brand: String = "",
            portionGrams: Double,
            calories: Double,
            protein: Double,
            carbohydrates: Double,
            fat: Double,
            confidence: Double = 0.9,
            source: String = "TACO Brasil"
        ): ParsedFoodItem {
            val lower = matchedFoodName.lowercase()
            val isLiq = lower.contains("leite") || lower.contains("suco") || lower.contains("shake") ||
                lower.contains("café") || lower.contains("refrigerante")
            return ParsedFoodItem(
                originalPhrase = originalPhrase,
                matchedFoodName = matchedFoodName,
                brand = brand,
                portionValue = portionGrams,
                unit = if (isLiq) "ml" else "g",
                isLiquid = isLiq,
                calories = calories,
                protein = protein,
                carbohydrates = carbohydrates,
                fat = fat,
                confidence = confidence,
                source = source
            )
        }
    }
}

/** Resultado consolidado de uma refeição analisada (iOS: `ParsedMealResult`). */
data class ParsedMealResult(
    val rawText: String,
    val mealType: MealType,
    val items: List<ParsedFoodItem>,
    val totalCalories: Double,
    val totalProtein: Double,
    val totalCarbs: Double,
    val totalFat: Double,
    val overallConfidence: Double
)

/**
 * Serviço de processamento de linguagem natural para a culinária brasileira.
 * Porta de `AIFoodParserService` (iOS): 1) base TACO local (instantânea), 2) IA em nuvem quando há consentimento
 * (ou `forceAI`), 3) fallback heurístico offline.
 */
class AIFoodParserService private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val nutrition get() = NutritionService.get(appContext)

    private fun emptyResult(text: String, mealType: MealType) = ParsedMealResult(
        rawText = text, mealType = mealType, items = emptyList(),
        totalCalories = 0.0, totalProtein = 0.0, totalCarbs = 0.0, totalFat = 0.0, overallConfidence = 0.0
    )

    // region API principal

    /**
     * Analisa a frase de refeição.
     * @param forceAI ignora a base local e vai direto à IA.
     * @param allowCloudAI `false` enquanto o usuário ainda digita: nunca dispara a IA.
     * @param onPhaseChange notificado com [AnalysisPhase.TACO_DATABASE] / [AnalysisPhase.ARTIFICIAL_INTELLIGENCE].
     */
    suspend fun parseMeal(
        description: String,
        forceAI: Boolean = false,
        allowCloudAI: Boolean = true,
        onPhaseChange: ((AnalysisPhase) -> Unit)? = null
    ): ParsedMealResult {
        if (allowCloudAI || forceAI) {
            onPhaseChange?.invoke(if (forceAI) AnalysisPhase.ARTIFICIAL_INTELLIGENCE else AnalysisPhase.TACO_DATABASE)
        }

        val cleanedInput = description.trim()
        if (cleanedInput.isEmpty()) return emptyResult("", inferMealType(""))

        val mealType = inferMealType(cleanedInput)
        val sanitizedText = stripConversationalNoise(cleanedInput)
        val rawSegments = segmentDishes(sanitizedText)

        // 1. TENTA PRIMEIRO A BASE LOCAL INSTANTÂNEA (Tabela TACO Brasil) se forceAI for false
        if (!forceAI) {
            val localParsedItems = rawSegments.mapNotNull { processLocalSegment(it) }

            // Se todos os componentes foram identificados na base local offline, retorna de imediato
            if (localParsedItems.isNotEmpty() && localParsedItems.size >= rawSegments.size) {
                return ParsedMealResult(
                    rawText = cleanedInput,
                    mealType = mealType,
                    items = localParsedItems,
                    totalCalories = localParsedItems.sumOf { it.calories },
                    totalProtein = localParsedItems.sumOf { it.protein },
                    totalCarbs = localParsedItems.sumOf { it.carbohydrates },
                    totalFat = localParsedItems.sumOf { it.fat },
                    overallConfidence = 0.98
                )
            }

            // Enquanto o usuário ainda está digitando/editando (allowCloudAI == false), NUNCA dispara a IA
            if (!allowCloudAI) return emptyResult(cleanedInput, mealType)
        }

        // 2. ANÁLISE COM IA (forçada pelo usuário ou fallback quando item não consta na TACO após finalizar entrada)
        if (AIDataConsentManager.isConsentGranted(appContext) && (allowCloudAI || forceAI)) {
            onPhaseChange?.invoke(AnalysisPhase.ARTIFICIAL_INTELLIGENCE)
            // Intervalo suave para percepção visual da transição
            if (!forceAI) delay(120)
            if (!currentCoroutineContext().isActive) return emptyResult(cleanedInput, mealType)

            try {
                val aiMeal = GeminiAIService.get(appContext).analyzeMeal(cleanedInput)
                val detectedMealType = MealType.values().firstOrNull { it.title == aiMeal.mealType } ?: mealType
                val items = aiMeal.items.map { item ->
                    ParsedFoodItem(
                        originalPhrase = item.name,
                        matchedFoodName = item.name,
                        brand = "",
                        portionValue = item.portionValue,
                        unit = item.unit,
                        isLiquid = item.isLiquid,
                        calories = item.calories,
                        protein = item.protein,
                        carbohydrates = item.carbohydrates,
                        fat = item.fat,
                        confidence = 0.99,
                        source = "IA Gemini"
                    )
                }
                return ParsedMealResult(
                    rawText = cleanedInput,
                    mealType = detectedMealType,
                    items = items,
                    totalCalories = aiMeal.totalCalories,
                    totalProtein = aiMeal.totalProtein,
                    totalCarbs = aiMeal.totalCarbs,
                    totalFat = aiMeal.totalFat,
                    overallConfidence = 0.99
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Análise IA em nuvem indisponível -> cai no fallback local
            }
        }

        // 3. Fallback heurístico local final (sem novas chamadas de rede)
        val parsedItems = rawSegments.mapNotNull { processOfflineFallbackSegment(it) }.toMutableList()
        if (parsedItems.isEmpty()) {
            processOfflineFallbackSegment(sanitizedText.ifEmpty { cleanedInput })?.let { parsedItems.add(it) }
        }

        return ParsedMealResult(
            rawText = cleanedInput,
            mealType = mealType,
            items = parsedItems,
            totalCalories = parsedItems.sumOf { it.calories },
            totalProtein = parsedItems.sumOf { it.protein },
            totalCarbs = parsedItems.sumOf { it.carbohydrates },
            totalFat = parsedItems.sumOf { it.fat },
            overallConfidence = if (parsedItems.isEmpty()) 0.0 else parsedItems.sumOf { it.confidence } / parsedItems.size
        )
    }

    // endregion

    // region Processamento local

    private fun processLocalSegment(segment: String): ParsedFoodItem? {
        val trimmed = segment.trim()
        if (trimmed.isEmpty()) return null

        val portion = extractGrams(trimmed)
        val foodQuery = extractFoodName(trimmed)
        val queryToUse = foodQuery.ifEmpty { trimmed }

        val best = nutrition.searchLocal(queryToUse).firstOrNull() ?: return null
        val values = best.values(portion)
        return ParsedFoodItem(
            originalPhrase = trimmed,
            matchedFoodName = best.name,
            brand = best.brand,
            portionValue = portion,
            unit = best.defaultUnit,
            isLiquid = best.isLiquid,
            calories = values.calories,
            protein = values.protein,
            carbohydrates = values.carbs,
            fat = values.fat,
            confidence = 0.98,
            source = best.sourceTag
        )
    }

    private fun processOfflineFallbackSegment(segment: String): ParsedFoodItem? {
        val trimmed = segment.trim()
        if (trimmed.isEmpty()) return null

        processLocalSegment(trimmed)?.let { return it }

        val portionGrams = extractGrams(trimmed)
        val foodQuery = extractFoodName(trimmed)
        val queryToUse = foodQuery.ifEmpty { trimmed }

        val estimated = estimateNutrients(queryToUse, portionGrams)
        return ParsedFoodItem.fromGrams(
            originalPhrase = trimmed,
            matchedFoodName = swiftCapitalized(queryToUse),
            brand = "Estimativa Inteligente",
            portionGrams = portionGrams,
            calories = estimated.calories,
            protein = estimated.protein,
            carbohydrates = estimated.carbs,
            fat = estimated.fat,
            confidence = 0.70,
            source = "Estimativa Heurística"
        )
    }

    // endregion

    // region Segmentação de pratos

    private fun segmentDishes(text: String): List<String> {
        var normalized = text
            .replace(";", ",")
            .replace("\n", ",")
            .replace(" com mais ", ",")
            .replace(" acompanhado de ", ",")
            .replace(" junto com ", ",")
            .replace(" + ", ",")
            .replace(" mais ", ",")

        // Mantém apenas termos indivisíveis da culinária brasileira
        val unifiedExpressions = listOf(
            "café com leite", "cafe com leite",
            "pão de queijo", "pao de queijo",
            "batata doce", "peito de frango",
            "queijo minas", "queijo prato"
        )

        val placeholders = LinkedHashMap<String, String>()
        var counter = 0
        for (expr in unifiedExpressions) {
            if (normalized.contains(expr, ignoreCase = true)) {
                val tag = "__EXPR_${counter}__"
                placeholders[tag] = expr
                val pattern = Regex("""\b""" + Regex.escape(expr) + """\b""", RX_OPTIONS)
                normalized = normalized.replace(pattern, Regex.escapeReplacement(tag))
                counter++
            }
        }

        // Agora que expressões compostas estão protegidas, quebra por " e " e " com "
        normalized = normalized.replace(Regex("""\s+\be\b\s+"""), ",")
        normalized = normalized.replace(Regex("""\s+\bcom\b\s+"""), ",")

        // Restaura as expressões compostas
        for ((tag, original) in placeholders) {
            normalized = normalized.replace(tag, original)
        }

        return normalized.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.length > 1 }
    }

    // endregion

    // region Extração de gramas e porções típicas brasileiras

    fun extractGrams(text: String): Double {
        val lower = text.lowercase()

        // 0. Multiplicador com "cada" (ex: "Dois pães francês de 76 g cada" -> 2 * 76 = 152g)
        RX_EACH_GRAM.find(lower)?.let { m ->
            val grams = m.groupValues[2].replace(",", ".").toDoubleOrNull()
            if (grams != null && grams > 0) {
                val qty = m.groups[1]?.value
                if (qty != null) return parseQuantity(qty) * grams
                return grams
            }
        }

        // 1. Gramas explícitas (ex: 150g, 200 gramas, 350 gr)
        RX_GRAM.find(lower)?.let { m ->
            val grams = m.groupValues[1].replace(",", ".").toDoubleOrNull()
            if (grams != null && grams > 0) return grams
        }

        // 2. Mililitros (ex: 200ml, 300 ml)
        RX_ML.find(lower)?.let { m ->
            val ml = m.groupValues[1].replace(",", ".").toDoubleOrNull()
            if (ml != null && ml > 0) return ml
        }

        // 3. Pratão / Prato
        if (lower.contains("pratão") || lower.contains("pratao")) return 250.0
        if (lower.contains("prato fundo")) return 220.0
        if (lower.contains("prato") || lower.contains("pratinho")) return 180.0

        // 4. Concha (feijão, sopa, caldo)
        RX_CONCHA.find(lower)?.let { return parseQuantity(it.groupValues[1]) * 130.0 }
        if (lower.contains("concha")) return 130.0

        // 5. Escumadeira (arroz)
        RX_ESCUMADEIRA.find(lower)?.let { return parseQuantity(it.groupValues[1]) * 100.0 }
        if (lower.contains("escumadeira")) return 100.0

        // 6. Colheres de sopa / colheres
        RX_COLHER.find(lower)?.let { m ->
            val count = parseQuantity(m.groupValues[1])
            // Arroz e massas: ~25g por colher; líquidos/pastas: ~15g
            if (lower.contains("arroz") || lower.contains("purê") || lower.contains("pure")) return count * 25.0
            return count * 15.0
        }

        // 6b. Quantidade implícita de colheres (ex: "2 de feijão", "3 de arroz")
        RX_IMPLICIT_SPOON.find(lower)?.let { m ->
            return parseQuantity(m.groupValues[1]) * 25.0
        }

        // 7. Fatias (pão, queijo, presunto, carne assada)
        RX_FATIA.find(lower)?.let { return parseQuantity(it.groupValues[1]) * 35.0 }

        // 8. Ovos (inteiros ou mexidos)
        RX_OVO.find(lower)?.let { return parseQuantity(it.groupValues[1]) * 50.0 }
        if (lower.contains("ovo") || lower.contains("ovos")) return 100.0 // 2 ovos padrão

        // 9. Bife / Filé / Pedaço
        RX_BIFE.find(lower)?.let { return parseQuantity(it.groupValues[1]) * 150.0 }
        if (lower.contains("bife") || lower.contains("filé") || lower.contains("file")) return 150.0

        // 10. Copo / Xícara
        RX_COPO.find(lower)?.let { return parseQuantity(it.groupValues[1]) * 200.0 }

        // 11. Scoop / Medidor (Whey / Suplementos)
        RX_SCOOP.find(lower)?.let { return parseQuantity(it.groupValues[1]) * 30.0 }
        if (lower.contains("whey") || lower.contains("proteína em pó")) return 30.0

        // 12. Frutas específicas por unidade
        if (lower.contains("banana")) return parseQuantityInContext(lower, "banana") * 90.0
        if (lower.contains("maçã") || lower.contains("maca")) return parseQuantityInContext(lower, "maç") * 130.0

        // 13. Pão Francês
        if (lower.contains("pão francês") || lower.contains("pao frances") || lower.contains("pão de sal")) {
            return parseQuantityInContext(lower, "pão") * 50.0
        }

        // 14. Padrões por tipo de alimento
        if (lower.contains("arroz")) return 150.0
        if (lower.contains("feijão") || lower.contains("feijao")) return 130.0
        if (lower.contains("frango") || lower.contains("carne") || lower.contains("peixe")) return 150.0
        if (lower.contains("azeite")) return 10.0

        return 100.0
    }

    // endregion

    // region Limpeza e extração do nome do alimento

    private fun extractFoodName(segment: String): String {
        var clean = segment.lowercase()
        for (rx in RX_REMOVAL) clean = clean.replace(rx, "")

        clean = clean
            .replace(Regex("""\bcada(?:\s+uma?)?\b""", RX_OPTIONS), " ")
            .replace(" de ", " ")
            .replace(" da ", " ")
            .replace(" do ", " ")
            .trim()
        return clean
    }

    private fun stripConversationalNoise(text: String): String {
        var clean = text.lowercase()
        val noisePhrases = listOf(
            "comi", "acabei de comer", "registre para mim", "registre", "adicione aí", "adicione",
            "anote aí", "anote", "coloque", "manda aí", "tomei", "bebi", "consumi",
            "no café da manhã comi", "no café comi", "no almoço comi", "no jantar comi", "na janta comi",
            "para o almoço", "para o café", "para o jantar", "para a janta",
            "meu almoço foi", "meu café foi", "meu jantar foi", "minha janta foi", "meu lanche foi",
            "de sobremesa comi", "de pré treino tomei", "de pós treino tomei"
        )
        for (phrase in noisePhrases) clean = clean.replace(phrase, "")
        return clean.trim()
    }

    // endregion

    // region Inferência do tipo de refeição

    /** Retorna a refeição principal do dia pelo horário (Café da Manhã 5-11h, Almoço 11-18h, Jantar demais). */
    fun inferPrimaryMealType(date: Date = Date()): MealType {
        val hour = Calendar.getInstance().apply { time = date }.get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5 until 11 -> MealType.BREAKFAST
            in 11 until 18 -> MealType.LUNCH
            else -> MealType.DINNER
        }
    }

    fun inferMealType(text: String): MealType {
        val lower = text.lowercase()

        if (lower.contains("café da manhã") || lower.contains("cafe da manha") ||
            lower.contains("desjejum") || lower.contains("no café") || lower.contains("no cafe") ||
            lower.contains("pela manhã")
        ) return MealType.BREAKFAST

        if (lower.contains("almoço") || lower.contains("almoco") ||
            lower.contains("almocei") || lower.contains("no almoço") || lower.contains("no almoco")
        ) return MealType.LUNCH

        if (lower.contains("jantar") || lower.contains("janta") ||
            lower.contains("jantei") || lower.contains("no jantar") || lower.contains("na janta")
        ) return MealType.DINNER

        if (lower.contains("lanche") || lower.contains("lanchei") ||
            lower.contains("merenda") || lower.contains("ceia") ||
            lower.contains("da tarde") || lower.contains("pré treino") || lower.contains("pós treino")
        ) return MealType.SNACKS

        // Padrão: uma das principais refeições pelo horário
        return inferPrimaryMealType()
    }

    // endregion

    // region Helpers numéricos

    private fun parseQuantity(word: String): Double {
        val w = word.trim().lowercase()
        w.toDoubleOrNull()?.let { return it }
        return when (w) {
            "meio", "meia" -> 0.5
            "um", "uma" -> 1.0
            "dois", "duas" -> 2.0
            "três", "tres" -> 3.0
            "quatro" -> 4.0
            "cinco" -> 5.0
            else -> 1.0
        }
    }

    private fun parseQuantityInContext(text: String, keyword: String): Double {
        val regex = Regex("""(\d+|meio|meia|uma?|duas?|três|tres)\s*""" + keyword, RX_OPTIONS)
        regex.find(text)?.let { return parseQuantity(it.groupValues[1]) }
        return 1.0
    }

    // endregion

    // region Estimativa heurística nutricional

    private data class Estimate(val calories: Double, val protein: Double, val carbs: Double, val fat: Double)

    private fun estimateNutrients(food: String, grams: Double): Estimate {
        val lower = food.lowercase()
        val factor = maxOf(0.0, grams) / 100.0

        // Padrões proteicos
        if (lower.contains("frango") || lower.contains("carne") || lower.contains("bife") || lower.contains("peixe")) {
            return Estimate(180 * factor, 28 * factor, 0.0, 5 * factor)
        }
        // Padrões carboidratos
        if (lower.contains("arroz") || lower.contains("macarrão") || lower.contains("batata")) {
            return Estimate(130 * factor, 2.5 * factor, 28 * factor, 0.5 * factor)
        }
        // Leguminosas
        if (lower.contains("feijão") || lower.contains("feijao") || lower.contains("lentilha")) {
            return Estimate(80 * factor, 5 * factor, 14 * factor, 0.5 * factor)
        }
        // Frutas
        if (lower.contains("banana") || lower.contains("maçã") || lower.contains("fruta")) {
            return Estimate(70 * factor, 1 * factor, 18 * factor, 0.2 * factor)
        }
        // Pães e massas
        if (lower.contains("pão") || lower.contains("pao") || lower.contains("torrada")) {
            return Estimate(270 * factor, 8 * factor, 52 * factor, 3 * factor)
        }
        // Gorduras
        if (lower.contains("azeite") || lower.contains("óleo") || lower.contains("manteiga")) {
            return Estimate(800 * factor, 0.0, 0.0, 90 * factor)
        }
        // Padrão geral
        return Estimate(120 * factor, 4 * factor, 18 * factor, 3 * factor)
    }

    // endregion

    companion object {
        private val RX_OPTIONS = setOf(RegexOption.IGNORE_CASE)

        private fun rx(p: String) = Regex(p, RX_OPTIONS)

        private val RX_EACH_GRAM = rx("""^(?:(\d+|um|uma|dois|duas|três|tres|quatro|cinco)\s+.+?\s+)?(\d+(?:[.,]\d+)?)\s*(?:gramas?|g\b|gr\b)\s+cada\b""")
        private val RX_GRAM = rx("""(\d+(?:[.,]\d+)?)\s*(?:gramas?|g\b|gr\b)""")
        private val RX_ML = rx("""(\d+(?:[.,]\d+)?)\s*(?:ml\b|mls\b)""")
        private val RX_CONCHA = rx("""(\d+|uma?|duas?|três|tres)\s*concha[s]?""")
        private val RX_ESCUMADEIRA = rx("""(\d+|uma?|duas?|três|tres)\s*escumadeira[s]?""")
        private val RX_COLHER = rx("""(\d+|uma?|duas?|três|tres|quatro|cinco)\s*colher(?:es)?(?:\s*de\s*sopa)?""")
        private val RX_IMPLICIT_SPOON = rx("""^(\d+|uma?|duas?|três|tres|quatro)\s*(?:de\s+)?(?:feij|arroz)""")
        private val RX_FATIA = rx("""(\d+|uma?|duas?|três|tres|quatro)\s*fatia[s]?""")
        private val RX_OVO = rx("""(\d+|um|dois|duas|três|tres|quatro|cinco)\s*ovo[s]?""")
        private val RX_BIFE = rx("""(\d+|um|dois|duas|três|tres)\s*(?:bife|filé|file|pedaço|pedaco)[s]?""")
        private val RX_COPO = rx("""(\d+|um|dois|duas)\s*(?:copo|xícara|xicara|caneca)[s]?""")
        private val RX_SCOOP = rx("""(\d+|um|dois|duas)\s*(?:scoop|dose|medidor)[s]?""")

        private val RX_REMOVAL: List<Regex> = listOf(
            """(\d+(?:[.,]\d+)?)\s*(?:gramas?|g\b|gr\b|ml\b|mls\b)""",
            """(\d+|uma?|duas?|três|tres|quatro|cinco)\s*colher(?:es)?(?:\s*de\s*sopa)?""",
            """(\d+|uma?|duas?|três|tres)\s*concha[s]?""",
            """(\d+|uma?|duas?|três|tres)\s*escumadeira[s]?""",
            """(\d+|uma?|duas?|três|tres|quatro)\s*fatia[s]?""",
            """(\d+|um|dois|duas|três|tres|quatro)\s*ovo[s]?""",
            """(\d+|um|dois|duas|três|tres)\s*(?:bife|filé|file|pedaço|pedaco)[s]?""",
            """(\d+|um|dois|duas)\s*(?:copo|xícara|xicara|caneca)[s]?""",
            """(\d+|um|dois|duas)\s*(?:scoop|dose|medidor)[s]?""",
            """^um\s+pratão\s+de\s+""",
            """^um\s+pratao\s+de\s+""",
            """^um\s+prato\s+de\s+""",
            """^pratão\s+de\s+""",
            """^pratao\s+de\s+""",
            """^prato\s+de\s+""",
            """^um\s+pouco\s+de\s+""",
            """^uma\s+porção\s+de\s+""",
            """^uma\s+porcao\s+de\s+""",
            """^bastante\s+""",
            """^bastante\s+de\s+""",
            """^\d+\s*(?:de\s+)?""",
            """^(?:um|dois|duas|três|tres|quatro)\s*(?:de\s+)?""",
            """^dois\s+""",
            """^duas\s+""",
            """^três\s+""",
            """^tres\s+""",
            """^um\s+""",
            """^uma\s+"""
        ).map { rx(it) }

        /** Capitaliza cada palavra (igual a `String.capitalized` do Swift). */
        internal fun swiftCapitalized(text: String): String {
            val sb = StringBuilder()
            var capitalizeNext = true
            for (ch in text) {
                if (ch.isLetter()) {
                    sb.append(if (capitalizeNext) ch.uppercaseChar() else ch.lowercaseChar())
                    capitalizeNext = false
                } else {
                    sb.append(ch)
                    capitalizeNext = !ch.isDigit() && ch != '\'' && ch != '’'
                }
            }
            return sb.toString()
        }

        @Volatile
        private var instance: AIFoodParserService? = null

        fun get(context: Context): AIFoodParserService =
            instance ?: synchronized(this) {
                instance ?: AIFoodParserService(context).also { instance = it }
            }
    }
}
