package com.forma.app.data.services

import android.content.Context
import com.forma.app.data.model.MealType
import java.text.Normalizer
import java.util.UUID

/** Resultado de macros para uma porção. */
data class NutrientValues(val calories: Double, val protein: Double, val carbs: Double, val fat: Double)

private fun round1(v: Double): Double = swiftRound(v * 10.0) / 10.0

/**
 * Alimento nutricional por 100 g/ml (iOS: `NutritionalFoodItem`).
 * Calorias zeradas com macros presentes são calculadas pela fórmula Atwater (4-4-9); valores negativos viram 0.
 */
class NutritionalFoodItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val brand: String = "",
    caloriesPer100g: Double,
    proteinPer100g: Double,
    carbsPer100g: Double,
    fatPer100g: Double,
    val imageUrl: String? = null,
    val sourceTag: String = "TACO Brasil"
) {
    val caloriesPer100g: Double
    val proteinPer100g: Double
    val carbsPer100g: Double
    val fatPer100g: Double

    init {
        val p = maxOf(0.0, proteinPer100g)
        val c = maxOf(0.0, carbsPer100g)
        val f = maxOf(0.0, fatPer100g)
        var cal = maxOf(0.0, caloriesPer100g)
        if (cal == 0.0 && (p > 0 || c > 0 || f > 0)) cal = (p * 4.0) + (c * 4.0) + (f * 9.0)
        this.caloriesPer100g = cal
        this.proteinPer100g = p
        this.carbsPer100g = c
        this.fatPer100g = f
    }

    val isLiquid: Boolean
        get() {
            val lower = "$name $brand".lowercase()
            return listOf(
                "leite", "suco", "shake", "café", "cafe", "refrigerante", "água", "agua",
                "vitamina", "bebida", "toddy pronto"
            ).any { lower.contains(it) }
        }

    /** "ml" para líquidos, "g" caso contrário. */
    val defaultUnit: String get() = if (isLiquid) "ml" else "g"

    /** Calorias/macros para [grams] gramas (ou ml), arredondados a 1 casa decimal. */
    fun values(grams: Double): NutrientValues {
        val factor = maxOf(0.0, grams) / 100.0
        return NutrientValues(
            calories = round1(caloriesPer100g * factor),
            protein = round1(proteinPer100g * factor),
            carbs = round1(carbsPer100g * factor),
            fat = round1(fatPer100g * factor)
        )
    }

    override fun equals(other: Any?): Boolean = other is NutritionalFoodItem && other.id == id
    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = "NutritionalFoodItem($name, $brand)"
}

/**
 * Base nutricional local (Tabela TACO Brasil) 100% offline + busca unificada com fallback em nuvem.
 * Porta de `NutritionService` (iOS).
 */
class NutritionService private constructor(context: Context) {

    private val appContext = context.applicationContext

    /** BASE DE DADOS LOCAL INSTANTÂNEA (TABELA TACO BRASIL) - 100% DISPONÍVEL OFFLINE. */
    val popularFoods: List<NutritionalFoodItem> = POPULAR_FOODS

    /** Normaliza acentos, grafias equivalentes e plurais comuns em português para busca local instantânea. */
    private fun normalizeToken(token: String): String {
        var t = foldDiacritics(token).lowercase().replace("mussarela", "mucarela")
        return when (t) {
            "paes" -> "pao"
            "franceses" -> "frances"
            "integrais" -> "integral"
            "naturais" -> "natural"
            else -> {
                if (t.length > 4 && t.endsWith("s") && !t.endsWith("ss") && t != "aveias") t = t.dropLast(1)
                t
            }
        }
    }

    /** 1. Busca local instantânea (0 ms). Query vazia devolve toda a base. */
    fun searchLocal(query: String): List<NutritionalFoodItem> {
        val clean = query.trim().lowercase()
        if (clean.isEmpty()) return popularFoods

        // Normaliza apelidos comuns (ex: mm -> m&m)
        val normalized = if (clean == "mm" || clean == "m&m" || clean == "mms" || clean == "m&ms") "m&m" else clean

        val stopTokens = setOf("de", "da", "do", "com", "cada", "um", "uma")
        val terms = normalized
            .split(Regex("\\s+"))
            .map { normalizeToken(it) }
            .filter { it.isNotEmpty() && it !in stopTokens }

        if (terms.isEmpty()) return emptyList()

        return popularFoods.filter { item ->
            val targetTokens = "${item.name} ${item.brand}"
                .split(Regex("\\s+"))
                .joinToString(" ") { normalizeToken(it) }
            terms.all { targetTokens.contains(it) }
        }
    }

    /**
     * 2. Busca unificada: base local primeiro; se nada for encontrado e houver consentimento de IA,
     * consulta a nuvem (analyze-meal). Nunca gera substituições aleatórias.
     */
    suspend fun searchFoods(query: String): List<NutritionalFoodItem> {
        val clean = query.trim().lowercase()
        if (clean.isEmpty()) return emptyList()
        if (isPureMeasure(clean)) return emptyList()

        val local = searchLocal(clean)
        if (local.isNotEmpty()) return local

        if (AIDataConsentManager.isConsentGranted(appContext)) {
            try {
                val aiMeal = GeminiAIService.get(appContext).analyzeMeal(clean)
                val results = aiMeal.items.map { item ->
                    val portion = maxOf(1.0, item.portionGrams)
                    NutritionalFoodItem(
                        name = item.name,
                        brand = "",
                        caloriesPer100g = (item.calories / portion) * 100.0,
                        proteinPer100g = (item.protein / portion) * 100.0,
                        carbsPer100g = (item.carbohydrates / portion) * 100.0,
                        fatPer100g = (item.fat / portion) * 100.0,
                        sourceTag = "Base Nutricional"
                    )
                }
                if (results.isNotEmpty()) return results
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Consulta fallback falhou -> vazio
            }
        }
        return emptyList()
    }

    private fun isPureMeasure(text: String): Boolean {
        val cleaned = text
            .replace("gramas", "")
            .replace("grama", "")
            .replace("gr", "")
            .replace("g", "")
            .replace("ml", "")
            .replace("mls", "")
            .replace("de", "")
            .trim()
        return cleaned.toDoubleOrNull() != null || cleaned.isEmpty()
    }

    companion object {
        @Volatile
        private var instance: NutritionService? = null

        fun get(context: Context): NutritionService =
            instance ?: synchronized(this) {
                instance ?: NutritionService(context).also { instance = it }
            }

        internal fun foldDiacritics(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

        private val POPULAR_FOODS: List<NutritionalFoodItem> = listOf(
        // Proteínas
        NutritionalFoodItem(name = "Peito de Frango Grelhado", brand = "Proteína Limpa", caloriesPer100g = 165.0, proteinPer100g = 31.0, carbsPer100g = 0.0, fatPer100g = 3.6, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Peito de Frango Cozido Desfiado", brand = "Proteína Limpa", caloriesPer100g = 159.0, proteinPer100g = 30.5, carbsPer100g = 0.0, fatPer100g = 3.2, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Carne Moída Patinho", brand = "Proteína Magra", caloriesPer100g = 187.0, proteinPer100g = 28.0, carbsPer100g = 0.0, fatPer100g = 7.5, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Bife de Alcatra Grelhado", brand = "Carne Vermelha", caloriesPer100g = 219.0, proteinPer100g = 31.0, carbsPer100g = 0.0, fatPer100g = 9.8, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Filé Mignon Grelhado", brand = "Carne Bovina", caloriesPer100g = 190.0, proteinPer100g = 32.0, carbsPer100g = 0.0, fatPer100g = 6.0, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Tilápia Grelhada", brand = "Peixe Branco", caloriesPer100g = 128.0, proteinPer100g = 26.0, carbsPer100g = 0.0, fatPer100g = 2.7, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Salmão Grelhado", brand = "Ômega 3", caloriesPer100g = 208.0, proteinPer100g = 20.0, carbsPer100g = 0.0, fatPer100g = 13.0, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Atum em Lata ao Natural", brand = "Pescado", caloriesPer100g = 116.0, proteinPer100g = 26.0, carbsPer100g = 0.0, fatPer100g = 0.8, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Ovo Cozido Inteiro", brand = "Proteína & Colina", caloriesPer100g = 143.0, proteinPer100g = 13.0, carbsPer100g = 0.7, fatPer100g = 9.5, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Clara de Ovo Cozida", brand = "Proteína Pura", caloriesPer100g = 52.0, proteinPer100g = 11.0, carbsPer100g = 0.7, fatPer100g = 0.2, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Ovo Frito", brand = "Ovo", caloriesPer100g = 240.0, proteinPer100g = 15.6, carbsPer100g = 1.2, fatPer100g = 18.6, sourceTag = "TACO Brasil"),
        // Carboidratos & Grãos
        NutritionalFoodItem(name = "Arroz Branco Cozido", brand = "Carboidrato", caloriesPer100g = 130.0, proteinPer100g = 2.7, carbsPer100g = 28.2, fatPer100g = 0.3, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Arroz Integral Cozido", brand = "Fibras", caloriesPer100g = 124.0, proteinPer100g = 2.6, carbsPer100g = 25.8, fatPer100g = 1.0, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Feijão Carioca Cozido", brand = "Leguminosa", caloriesPer100g = 76.0, proteinPer100g = 4.8, carbsPer100g = 13.6, fatPer100g = 0.5, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Feijão Preto Cozido", brand = "Leguminosa", caloriesPer100g = 77.0, proteinPer100g = 4.5, carbsPer100g = 14.0, fatPer100g = 0.5, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Batata Doce Cozida", brand = "Carboidrato Complexo", caloriesPer100g = 86.0, proteinPer100g = 1.6, carbsPer100g = 20.1, fatPer100g = 0.1, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Batata Inglesa Cozida", brand = "Tubérculo", caloriesPer100g = 77.0, proteinPer100g = 1.9, carbsPer100g = 17.5, fatPer100g = 0.1, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Mandioca / Aipim Cozido", brand = "Energia", caloriesPer100g = 125.0, proteinPer100g = 0.6, carbsPer100g = 30.1, fatPer100g = 0.3, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Macarrão Cozido", brand = "Massa", caloriesPer100g = 141.0, proteinPer100g = 5.0, carbsPer100g = 28.0, fatPer100g = 0.8, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Pão Francês", brand = "Padaria", caloriesPer100g = 290.0, proteinPer100g = 8.0, carbsPer100g = 58.0, fatPer100g = 3.1, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Pão de Forma Integral", brand = "Padaria", caloriesPer100g = 247.0, proteinPer100g = 9.4, carbsPer100g = 49.9, fatPer100g = 3.7, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Tapioca Pronta", brand = "Goma de Mandioca", caloriesPer100g = 240.0, proteinPer100g = 0.4, carbsPer100g = 60.0, fatPer100g = 0.1, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Aveia em Flocos", brand = "Cereal / Fibras", caloriesPer100g = 366.0, proteinPer100g = 14.0, carbsPer100g = 66.0, fatPer100g = 6.9, sourceTag = "TACO Brasil"),
        // Doces & Confeitos (incluindo M&M's)
        NutritionalFoodItem(name = "Chocolate M&M's Confeito", brand = "Mars M&M's", caloriesPer100g = 492.0, proteinPer100g = 4.5, carbsPer100g = 70.0, fatPer100g = 21.0, sourceTag = "Doces"),
        NutritionalFoodItem(name = "Chocolate ao Leite", brand = "Doces", caloriesPer100g = 535.0, proteinPer100g = 7.6, carbsPer100g = 59.4, fatPer100g = 29.7, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Chocolate Meio Amargo", brand = "Doces", caloriesPer100g = 479.0, proteinPer100g = 5.0, carbsPer100g = 55.0, fatPer100g = 30.0, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Doce de Leite", brand = "Doces", caloriesPer100g = 315.0, proteinPer100g = 5.5, carbsPer100g = 55.0, fatPer100g = 7.0, sourceTag = "TACO Brasil"),
        // Pratos Rápidos & Lanches
        NutritionalFoodItem(name = "Pizza de Muçarela", brand = "Massa", caloriesPer100g = 280.0, proteinPer100g = 12.0, carbsPer100g = 30.0, fatPer100g = 12.0, sourceTag = "Lanches"),
        NutritionalFoodItem(name = "Pizza de Calabresa", brand = "Massa", caloriesPer100g = 295.0, proteinPer100g = 13.0, carbsPer100g = 29.0, fatPer100g = 14.0, sourceTag = "Lanches"),
        NutritionalFoodItem(name = "Pão de Queijo Assado", brand = "Padaria", caloriesPer100g = 335.0, proteinPer100g = 6.5, carbsPer100g = 34.2, fatPer100g = 19.0, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Presunto Cozido Magro", brand = "Frios", caloriesPer100g = 115.0, proteinPer100g = 18.0, carbsPer100g = 1.5, fatPer100g = 4.0, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Peito de Peru Defumado", brand = "Frios", caloriesPer100g = 104.0, proteinPer100g = 17.5, carbsPer100g = 1.8, fatPer100g = 2.8, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Hambúrguer Bovino", brand = "Lanches", caloriesPer100g = 250.0, proteinPer100g = 17.0, carbsPer100g = 0.0, fatPer100g = 20.0, sourceTag = "Lanches"),
        NutritionalFoodItem(name = "Batata Frita", brand = "Lanches", caloriesPer100g = 312.0, proteinPer100g = 3.4, carbsPer100g = 41.0, fatPer100g = 15.0, sourceTag = "TACO Brasil"),
        // Frutas
        NutritionalFoodItem(name = "Banana Prata", brand = "Fruta", caloriesPer100g = 98.0, proteinPer100g = 1.3, carbsPer100g = 26.0, fatPer100g = 0.3, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Banana Nanica", brand = "Fruta", caloriesPer100g = 92.0, proteinPer100g = 1.4, carbsPer100g = 23.8, fatPer100g = 0.1, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Maçã Fuji / Gala", brand = "Fruta", caloriesPer100g = 52.0, proteinPer100g = 0.3, carbsPer100g = 13.8, fatPer100g = 0.2, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Abacate", brand = "Gordura Saudável", caloriesPer100g = 160.0, proteinPer100g = 2.0, carbsPer100g = 8.5, fatPer100g = 14.7, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Morango Fresco", brand = "Fruta Vermelha", caloriesPer100g = 30.0, proteinPer100g = 0.7, carbsPer100g = 7.7, fatPer100g = 0.3, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Laranja Pêra", brand = "Vitamina C", caloriesPer100g = 47.0, proteinPer100g = 0.9, carbsPer100g = 11.8, fatPer100g = 0.1, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Mamão Papaia", brand = "Fruta", caloriesPer100g = 45.0, proteinPer100g = 0.5, carbsPer100g = 11.6, fatPer100g = 0.1, sourceTag = "TACO Brasil"),
        // Laticínios & Bebidas
        NutritionalFoodItem(name = "Leite Integral", brand = "Laticínio", caloriesPer100g = 60.0, proteinPer100g = 3.2, carbsPer100g = 4.8, fatPer100g = 3.2, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Leite Desnatado", brand = "Laticínio", caloriesPer100g = 35.0, proteinPer100g = 3.4, carbsPer100g = 5.0, fatPer100g = 0.1, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Café com Leite", brand = "Bebida", caloriesPer100g = 45.0, proteinPer100g = 2.5, carbsPer100g = 3.8, fatPer100g = 2.3, sourceTag = "Bebidas"),
        NutritionalFoodItem(name = "Café Preto Sem Açúcar", brand = "Bebida", caloriesPer100g = 2.0, proteinPer100g = 0.1, carbsPer100g = 0.0, fatPer100g = 0.0, sourceTag = "Bebidas"),
        NutritionalFoodItem(name = "Suco de Laranja Natural", brand = "Bebida", caloriesPer100g = 45.0, proteinPer100g = 0.7, carbsPer100g = 10.4, fatPer100g = 0.2, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Iogurte Natural Desnatado", brand = "Probiótico", caloriesPer100g = 41.0, proteinPer100g = 3.8, carbsPer100g = 5.8, fatPer100g = 0.3, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Iogurte Grego Natural", brand = "Laticínio", caloriesPer100g = 110.0, proteinPer100g = 6.5, carbsPer100g = 4.0, fatPer100g = 7.0, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Queijo Minas Frescal", brand = "Queijo Branco", caloriesPer100g = 243.0, proteinPer100g = 16.0, carbsPer100g = 3.2, fatPer100g = 18.0, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Queijo Cottage", brand = "Queijo Magro", caloriesPer100g = 98.0, proteinPer100g = 11.0, carbsPer100g = 3.4, fatPer100g = 4.3, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Queijo Muçarela", brand = "Laticínio", caloriesPer100g = 280.0, proteinPer100g = 22.0, carbsPer100g = 3.0, fatPer100g = 20.0, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Requeijão Cremoso", brand = "Laticínio", caloriesPer100g = 250.0, proteinPer100g = 8.0, carbsPer100g = 3.0, fatPer100g = 23.0, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Azeite de Oliva Extra Virgem", brand = "Gordura Boa", caloriesPer100g = 884.0, proteinPer100g = 0.0, carbsPer100g = 0.0, fatPer100g = 100.0, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Manteiga", brand = "Laticínio", caloriesPer100g = 717.0, proteinPer100g = 0.9, carbsPer100g = 0.1, fatPer100g = 81.0, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Castanha-do-Pará", brand = "Oleaginosa", caloriesPer100g = 656.0, proteinPer100g = 14.3, carbsPer100g = 12.3, fatPer100g = 66.4, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Castanha de Caju", brand = "Oleaginosa", caloriesPer100g = 553.0, proteinPer100g = 18.2, carbsPer100g = 30.2, fatPer100g = 43.8, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Pasta de Amendoim Integral", brand = "Hipercalórico Limpo", caloriesPer100g = 588.0, proteinPer100g = 25.0, carbsPer100g = 20.0, fatPer100g = 50.0, sourceTag = "TACO Brasil"),
        // Suplementos & Ganho de Peso
        NutritionalFoodItem(name = "Whey Protein Concentrado 80%", brand = "Suplemento", caloriesPer100g = 400.0, proteinPer100g = 80.0, carbsPer100g = 8.0, fatPer100g = 6.0, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Whey Protein Isolado 90%", brand = "Suplemento", caloriesPer100g = 370.0, proteinPer100g = 90.0, carbsPer100g = 1.0, fatPer100g = 1.0, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Hipercalórico Mass Gainer", brand = "Bulking", caloriesPer100g = 380.0, proteinPer100g = 16.0, carbsPer100g = 74.0, fatPer100g = 2.0, sourceTag = "TACO Brasil"),
        NutritionalFoodItem(name = "Hipercalórico Growth Supplements", brand = "Growth", caloriesPer100g = 385.0, proteinPer100g = 20.0, carbsPer100g = 72.0, fatPer100g = 1.8, sourceTag = "Suplementos"),
        NutritionalFoodItem(name = "Achocolatado Toddy em Pó", brand = "Toddy", caloriesPer100g = 400.0, proteinPer100g = 2.0, carbsPer100g = 90.0, fatPer100g = 2.5, sourceTag = "Doces"),
        NutritionalFoodItem(name = "Leite com Toddy", brand = "Bebida", caloriesPer100g = 80.0, proteinPer100g = 3.2, carbsPer100g = 11.5, fatPer100g = 2.4, sourceTag = "Bebidas"),
        NutritionalFoodItem(name = "Creatina Monohidratada", brand = "Força / ATP", caloriesPer100g = 0.0, proteinPer100g = 0.0, carbsPer100g = 0.0, fatPer100g = 0.0, sourceTag = "TACO Brasil"),
        )
    }
}
