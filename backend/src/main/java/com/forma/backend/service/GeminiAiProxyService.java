package com.forma.backend.service;

import com.forma.backend.dto.AiDto;
import com.forma.backend.entity.AiRequestLogEntity;
import com.forma.backend.exception.DomainExceptions.AiUpstreamException;
import com.forma.backend.repository.AiRequestLogRepository;
import com.forma.backend.websocket.AdminEventPublisherService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.text.Normalizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import javax.imageio.ImageIO;

@Service
public class GeminiAiProxyService {

    private static final List<String> FAILOVER_MODELS = List.of(
        "gemini-3.1-flash-lite",
        "gemini-3.6-flash",
        "gemini-3-flash-preview",
        "gemini-3.5-flash",
        "gemini-3.8-flash"
    );

    private final AppConfigService appConfigService;
    private final AiRequestLogRepository aiRequestLogRepository;
    private final AdminEventPublisherService adminEventPublisherService;
    private final PhysiologicalSafetyGuard physiologicalSafetyGuard;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public GeminiAiProxyService(
        AppConfigService appConfigService,
        AiRequestLogRepository aiRequestLogRepository,
        AdminEventPublisherService adminEventPublisherService,
        PhysiologicalSafetyGuard physiologicalSafetyGuard
    ) {
        this(
            appConfigService,
            aiRequestLogRepository,
            adminEventPublisherService,
            physiologicalSafetyGuard,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
        );
    }

    public GeminiAiProxyService(
        AppConfigService appConfigService,
        AiRequestLogRepository aiRequestLogRepository,
        AdminEventPublisherService adminEventPublisherService,
        HttpClient httpClient
    ) {
        this(
            appConfigService,
            aiRequestLogRepository,
            adminEventPublisherService,
            new PhysiologicalSafetyGuard(),
            httpClient
        );
    }

    public GeminiAiProxyService(
        AppConfigService appConfigService,
        AiRequestLogRepository aiRequestLogRepository,
        AdminEventPublisherService adminEventPublisherService,
        PhysiologicalSafetyGuard physiologicalSafetyGuard,
        HttpClient httpClient
    ) {
        this.appConfigService = appConfigService;
        this.aiRequestLogRepository = aiRequestLogRepository;
        this.adminEventPublisherService = adminEventPublisherService;
        this.physiologicalSafetyGuard = physiologicalSafetyGuard != null
            ? physiologicalSafetyGuard
            : new PhysiologicalSafetyGuard();
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }

    public String analyzeMeal(UUID userId, AiDto.MealAiRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Dados da refeição não informados.");
        }
        long startTime = System.currentTimeMillis();
        boolean hasImage = request.imageBase64() != null && !request.imageBase64().isBlank();
        String userDescription = request.description() != null ? request.description().trim() : "";

        if (!hasImage && userDescription.isEmpty()) {
            throw new IllegalArgumentException("Informe a descrição ou envie uma foto da refeição.");
        }

        String prompt = hasImage
            ? buildMealImagePrompt(userDescription)
            : buildMealTextPrompt(userDescription);

        String logSummary = hasImage
            ? "[FOTO] " + (userDescription.isEmpty() ? "Análise visual de refeição" : userDescription)
            : userDescription;

        return executeAndLog(
            userId,
            "/api/ai/analyze-meal",
            logSummary,
            startTime,
            prompt,
            request.imageBase64(),
            request.imageMimeType(),
            "Não foi possível analisar a refeição no momento. Tente novamente em instantes."
        );
    }

    public String analyzeWorkout(UUID userId, AiDto.WorkoutAiRequest request) {
        if (request == null || request.description() == null || request.description().isBlank()) {
            throw new IllegalArgumentException("Informe a descrição do treino para análise.");
        }
        long startTime = System.currentTimeMillis();
        double weight = (request.userWeight() != null && request.userWeight() > 0) ? request.userWeight() : 72.0;
        String description = request.description().trim();
        String prompt = buildWorkoutPrompt(weight, description);

        String raw = executeAndLog(
            userId,
            "/api/ai/analyze-workout",
            description,
            startTime,
            prompt,
            null,
            null,
            "Não foi possível gerar ou calcular o treino agora. Tente novamente em instantes."
        );
        String withVideos = ensureExerciseVideoIds(raw);
        return physiologicalSafetyGuard.sanitizeWorkoutJson(withVideos, description);
    }

    public String analyzeEvolution(UUID userId, AiDto.EvolutionAiRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Dados de evolução não informados.");
        }
        long startTime = System.currentTimeMillis();
        String prompt = buildEvolutionPrompt(request);
        double weight = request.currentWeight() != null && request.currentWeight() > 30.0 ? request.currentWeight() : 72.0;
        double heightM = request.heightMeters() != null && request.heightMeters() >= 1.0 ? request.heightMeters() : 1.78;
        String safetyContext = (request.userGoal() != null ? request.userGoal() + " " : "")
            + (request.activityLogJson() != null ? request.activityLogJson() : "");

        String raw = executeAndLog(
            userId,
            "/api/ai/analyze-evolution",
            "Panorama de evolução física",
            startTime,
            prompt,
            null,
            null,
            "A análise inteligente de evolução está temporariamente ocupada. Tente atualizar em alguns segundos."
        );
        String withVideos = ensureExerciseVideoIds(raw);
        return physiologicalSafetyGuard.sanitizeEvolutionJson(withVideos, safetyContext, weight, heightM);
    }

    public String suggestInitialGoals(UUID userId, AiDto.InitialGoalsAiRequest request) {
        return suggestInitialGoals(userId, request, true);
    }

    public String suggestInitialGoals(UUID userId, AiDto.InitialGoalsAiRequest request, boolean allowCloudAi) {
        if (request == null || request.currentWeight() == null || request.currentWeight() <= 20.0
            || request.heightMeters() == null || request.heightMeters() < 0.8) {
            throw new IllegalArgumentException("Informe peso atual e altura válidos para sugerir as metas.");
        }
        long startTime = System.currentTimeMillis();
        String goal = (request.goal() != null && !request.goal().isBlank()) ? request.goal().trim() : "Ganhar peso";
        double weight = request.currentWeight();
        double heightM = request.heightMeters() > 3.0 ? request.heightMeters() / 100.0 : request.heightMeters();
        String activity = (request.activityLevel() != null && !request.activityLevel().isBlank())
            ? request.activityLevel().trim()
            : "Moderado (3-4x/semana)";

        Map<String, Object> baseline = computePhysiologicalInitialTargets(goal, weight, heightM, request.targetWeight(), activity);

        if (allowCloudAi) {
            try {
                if (!appConfigService.isAiBlocked()) {
                    String prompt = buildInitialGoalsPrompt(goal, weight, heightM, request.targetWeight(), activity, baseline);
                    String result = callGeminiWithFailover(prompt, null, null);
                    if (result != null && !result.isBlank()) {
                        JsonNode node = objectMapper.readTree(result);
                        if (node.has("daily_calories") && node.path("daily_calories").asDouble(0) >= 800) {
                            long duration = System.currentTimeMillis() - startTime;
                            logAiRequest(userId, "/api/ai/suggest-initial-goals", "Onboarding (" + goal + ")", "SUCCESS", duration);
                            return physiologicalSafetyGuard.sanitizeInitialGoalsJson(result, weight, heightM);
                        }
                    }
                }
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                logAiRequest(userId, "/api/ai/suggest-initial-goals", "Onboarding fallback", "FALLBACK_LOCAL", duration);
            }
        }

        try {
            String baselineJson = objectMapper.writeValueAsString(baseline);
            return physiologicalSafetyGuard.sanitizeInitialGoalsJson(baselineJson, weight, heightM);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao calcular sugestão inicial de metas.");
        }
    }

    private Map<String, Object> computePhysiologicalInitialTargets(
        String goal,
        double weight,
        double heightM,
        Double userTargetWeight,
        String activityLevel
    ) {
        double heightCm = heightM * 100.0;
        double tmb = (10.0 * weight) + (6.25 * heightCm) - (5.0 * 26.0) + 5.0;

        String lowerAct = activityLevel.toLowerCase();
        double activityFactor;
        double targetBurn;
        double targetSteps;
        if (lowerAct.contains("intenso") || lowerAct.contains("5") || lowerAct.contains("6") || lowerAct.contains("atleta")) {
            activityFactor = 1.62;
            targetBurn = 650.0;
            targetSteps = 10000.0;
        } else if (lowerAct.contains("sedent") || lowerAct.contains("leve") || lowerAct.contains("1") || lowerAct.contains("2")) {
            activityFactor = 1.35;
            targetBurn = 350.0;
            targetSteps = 7000.0;
        } else {
            activityFactor = 1.48;
            targetBurn = 500.0;
            targetSteps = 8500.0;
        }

        double tdee = tmb * activityFactor;
        String lowerGoal = goal.toLowerCase();
        boolean isLose = lowerGoal.contains("perd") || lowerGoal.contains("lose") || lowerGoal.contains("emagrec") || lowerGoal.contains("sec");
        boolean isMaintain = lowerGoal.contains("mant") || lowerGoal.contains("maintain");

        double suggestedTargetWeight;
        if (userTargetWeight != null && userTargetWeight >= 30.0) {
            suggestedTargetWeight = Math.round(userTargetWeight * 10.0) / 10.0;
        } else {
            double idealBmiWeight = 22.5 * heightM * heightM;
            if (isLose) {
                suggestedTargetWeight = Math.round(Math.max(45.0, Math.min(weight - 4.0, idealBmiWeight)) * 10.0) / 10.0;
            } else if (isMaintain) {
                suggestedTargetWeight = Math.round(weight * 10.0) / 10.0;
            } else {
                suggestedTargetWeight = Math.round(Math.max(weight + 5.0, idealBmiWeight) * 10.0) / 10.0;
            }
        }

        double dailyCalories;
        double proteinPerKg;
        double fatPerKg;
        String strategyLabel;
        if (isLose) {
            dailyCalories = Math.max(1350.0, Math.round((tdee - 450.0) / 10.0) * 10.0);
            proteinPerKg = 2.1;
            fatPerKg = 0.85;
            strategyLabel = "déficit calórico controlado com alta proteção de massa magra";
        } else if (isMaintain) {
            dailyCalories = Math.max(1500.0, Math.round(tdee / 10.0) * 10.0);
            proteinPerKg = 1.9;
            fatPerKg = 0.95;
            strategyLabel = "equilíbrio energético para recomposição corporal e performance";
        } else {
            dailyCalories = Math.max(1800.0, Math.round((tdee + 380.0) / 10.0) * 10.0);
            proteinPerKg = 2.0;
            fatPerKg = 1.0;
            strategyLabel = "superávit calórico limpo focado em hipertrofia muscular";
        }

        double proteinGrams = Math.round(weight * proteinPerKg);
        double fatGrams = Math.round(weight * fatPerKg);
        double remainingKcal = Math.max(240.0, dailyCalories - (proteinGrams * 4.0) - (fatGrams * 9.0));
        double carbsGrams = Math.round(remainingKcal / 4.0);
        int waterMl = (int) (Math.round((weight * 38.0) / 100.0) * 100);
        waterMl = Math.max(2000, Math.min(5000, waterMl));

        String explanation = String.format(
            Locale.forLanguageTag("pt-BR"),
            "Calculamos uma estimativa educacional com Taxa Basal (~%.0f kcal) e gasto diário (~%.0f kcal) para %.1f kg e %.2f m, aplicando %s rumo aos %.1f kg.",
            tmb, tdee, weight, heightM, strategyLabel, suggestedTargetWeight
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("suggested_target_weight", suggestedTargetWeight);
        result.put("daily_calories", dailyCalories);
        result.put("protein_grams", proteinGrams);
        result.put("carbs_grams", carbsGrams);
        result.put("fat_grams", fatGrams);
        result.put("water_milliliters", waterMl);
        result.put("target_calorie_burn", targetBurn);
        result.put("target_steps", targetSteps);
        result.put("explanation", explanation);
        return result;
    }

    private static String buildInitialGoalsPrompt(
        String goal,
        double weight,
        double heightM,
        Double userTargetWeight,
        String activityLevel,
        Map<String, Object> baseline
    ) {
        String targetWeightText = (userTargetWeight != null && userTargetWeight >= 30.0)
            ? String.format(Locale.US, "%.1f kg (definido pelo usuário, mantenha este valor em suggested_target_weight)", userTargetWeight)
            : String.format(Locale.US, "não informado (sugira um peso-alvo saudável e realista próximo de %s kg)", baseline.get("suggested_target_weight"));

        return """
        Você é o assistente educacional de bem-estar, nutrição e condicionamento físico do aplicativo Forma.
        Caráter estritamente informativo e educacional: NÃO emita diagnósticos médicos, NÃO prescreva dietas clínicas ou tratamentos e NÃO mencione dados pessoais nominativos.
        O usuário está preenchendo a configuração inicial de metas no app Forma.
        
        DADOS BIOMÉTRICOS ANONIMIZADOS:
        - Objetivo principal: %s
        - Peso atual: %.1f kg
        - Altura: %.2f m
        - Peso meta: %s
        - Nível de atividade física: %s
        
        REFERÊNCIA ESTIMADA BASE (Mifflin-St Jeor + nível de atividade):
        - Calorias sugeridas base: %s kcal/dia
        - Proteínas base: %s g | Carboidratos base: %s g | Gorduras base: %s g
        - Água diária base: %s ml | Queima ativa em treino: %s kcal | Passos: %s
        
        Calcule estimativas diárias educativas, seguras (mínimo 1200 kcal/dia) e coerentes (4*proteína + 4*carboidratos + 9*gorduras compatível com daily_calories).
        Escreva em `explanation` uma mensagem acolhedora e objetiva (máximo 2 frases curtas em português) explicando a estimativa educacional adotada pelo Forma.
        
        Responda ESTRITAMENTE em JSON válido com estas chaves exatas:
        {
          "suggested_target_weight": 78.0,
          "daily_calories": 2750.0,
          "protein_grams": 145.0,
          "carbs_grams": 355.0,
          "fat_grams": 75.0,
          "water_milliliters": 2800,
          "target_calorie_burn": 500.0,
          "target_steps": 8500.0,
          "explanation": "Explicação educacional curta e motivadora em até 2 frases."
        }
        """.formatted(
            goal,
            weight,
            heightM,
            targetWeightText,
            activityLevel,
            baseline.get("daily_calories"),
            baseline.get("protein_grams"),
            baseline.get("carbs_grams"),
            baseline.get("fat_grams"),
            baseline.get("water_milliliters"),
            baseline.get("target_calorie_burn"),
            baseline.get("target_steps")
        );
    }

    private String executeAndLog(
        UUID userId,
        String endpoint,
        String logSummary,
        long startTime,
        String prompt,
        String imageBase64,
        String imageMimeType,
        String friendlyFallbackError
    ) {
        try {
            String result = callGeminiWithFailover(prompt, imageBase64, imageMimeType);
            long duration = System.currentTimeMillis() - startTime;
            logAiRequest(userId, endpoint, logSummary, "SUCCESS", duration);
            return result;
        } catch (IllegalStateException e) {
            long duration = System.currentTimeMillis() - startTime;
            logAiRequest(userId, endpoint, logSummary, "CONFIG_ERROR: " + e.getMessage(), duration);
            throw e;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logAiRequest(userId, endpoint, logSummary, "ERROR: " + e.getMessage(), duration);
            throw new AiUpstreamException(friendlyFallbackError, e.getMessage(), e);
        }
    }

    private String callGeminiWithFailover(String prompt, String imageBase64, String imageMimeType) throws Exception {
        String apiKey = appConfigService.getGeminiApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Chave de API do Gemini não configurada no Painel de Administração.");
        }

        Set<String> modelsToTry = new LinkedHashSet<>();
        String primaryModel = appConfigService.getGeminiModel();
        if (primaryModel != null && !primaryModel.isBlank()) {
            modelsToTry.add(primaryModel);
        }
        modelsToTry.addAll(FAILOVER_MODELS);

        String requestBody = buildGeminiPayload(prompt, imageBase64, imageMimeType);
        Exception lastException = null;

        boolean isVertexExpressKey = apiKey.trim().startsWith("AQ.");
        for (String model : modelsToTry) {
            List<String> endpointUrls = new ArrayList<>();
            if (isVertexExpressKey) {
                endpointUrls.add("https://aiplatform.googleapis.com/v1/publishers/google/models/" + model
                    + ":generateContent?key=" + URLEncoder.encode(apiKey.trim(), StandardCharsets.UTF_8));
            }
            endpointUrls.add("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent");

            for (String url : endpointUrls) {
                try {
                    HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .timeout(Duration.ofSeconds(25));
                    if (!url.contains("aiplatform.googleapis.com")) {
                        reqBuilder.header("x-goog-api-key", apiKey.trim());
                    }

                    HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200) {
                        String extracted = extractResponseText(response.body());
                        if (extracted != null && !extracted.isBlank()) {
                            return extracted;
                        }
                    }

                    if (isRetryableUpstreamStatus(response.statusCode())) {
                        lastException = new RuntimeException("Gemini (" + model + " - HTTP " + response.statusCode() + "): " + response.body());
                        continue;
                    }

                    if (url.contains("aiplatform.googleapis.com")) {
                        lastException = new RuntimeException("Vertex (" + model + " - HTTP " + response.statusCode() + ")");
                        continue;
                    }

                    throw new RuntimeException("Gemini (" + model + " - HTTP " + response.statusCode() + "): " + response.body());
                } catch (Exception e) {
                    lastException = e;
                }
            }
        }

        throw (lastException != null) ? lastException : new RuntimeException("Falha ao comunicar com os modelos Gemini.");
    }

    private String buildGeminiPayload(String prompt, String imageBase64, String imageMimeType) throws Exception {
        List<Object> parts = new ArrayList<>();
        if (imageBase64 != null && !imageBase64.isBlank()) {
            String rawCleanBase64 = stripDataUriPrefix(imageBase64.trim());
            String mimeType = (imageMimeType != null && !imageMimeType.isBlank())
                ? imageMimeType.trim()
                : "image/jpeg";
            String sanitizedBase64 = stripExifMetadataIfRealImage(rawCleanBase64, mimeType);
            parts.add(Map.of(
                "inlineData", Map.of(
                    "mimeType", mimeType,
                    "data", sanitizedBase64
                )
            ));
        }
        parts.add(Map.of("text", prompt));

        return objectMapper.writeValueAsString(Map.of(
            "contents", List.of(Map.of("parts", parts)),
            "generationConfig", Map.of(
                "responseMimeType", "application/json",
                "temperature", 0.2
            )
        ));
    }

    private static String stripExifMetadataIfRealImage(String cleanBase64, String mimeType) {
        try {
            byte[] decoded = Base64.getDecoder().decode(cleanBase64);
            if (decoded.length <= 128) {
                return cleanBase64;
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(decoded));
            if (image == null) {
                return cleanBase64;
            }
            String format = (mimeType != null && mimeType.toLowerCase(Locale.ROOT).contains("png")) ? "png" : "jpg";
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            if ("jpg".equals(format) && image.getColorModel().hasAlpha()) {
                BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                rgb.getGraphics().drawImage(image, 0, 0, null);
                image = rgb;
            }
            ImageIO.write(image, format, baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception ignored) {
            return cleanBase64;
        }
    }

    private String extractResponseText(String rawBody) throws Exception {
        JsonNode root = objectMapper.readTree(rawBody);
        JsonNode partsArray = root.path("candidates").path(0).path("content").path("parts");
        if (partsArray.isArray()) {
            for (JsonNode partNode : partsArray) {
                if (!partNode.path("thought").asBoolean(false)) {
                    JsonNode textNode = partNode.path("text");
                    if (!textNode.isMissingNode() && !textNode.asText().isBlank()) {
                        return textNode.asText();
                    }
                }
            }
            JsonNode fallbackText = partsArray.path(0).path("text");
            if (!fallbackText.isMissingNode() && !fallbackText.asText().isBlank()) {
                return fallbackText.asText();
            }
        }
        return null;
    }

    private static boolean isRetryableUpstreamStatus(int statusCode) {
        return statusCode == 429 || statusCode == 503 || statusCode == 404 || statusCode >= 500;
    }

    private static String stripDataUriPrefix(String base64) {
        int commaIndex = base64.indexOf(',');
        if (base64.startsWith("data:") && commaIndex > 0) {
            return base64.substring(commaIndex + 1).trim();
        }
        return base64;
    }

    private static String sanitizeUserName(String raw) {
        return (raw != null && !raw.isBlank()) ? raw.trim() : "Usuário";
    }

    private static String buildMealImagePrompt(String userDescription) {
        String extraNote = userDescription.isEmpty()
            ? "Nenhuma observação escrita adicional; baseie-se integralmente na análise visual da imagem."
            : "Observação adicional do usuário para complementar a imagem: \"" + userDescription + "\"";

        return """
        Você é o assistente educacional de visão computacional e estimativa nutricional de bem-estar do app Forma.
        Caráter estritamente informativo (não substitui acompanhamento de nutricionista CRN ou médico).
        Analise cuidadosamente a FOTO enviada da refeição, prato, bebida, embalagem ou tabela nutricional.
        %s
        
        DIRETRIZES DE IDENTIFICAÇÃO VISUAL E ESTIMATIVA DE QUANTIDADE:
        1. IDENTIFICAÇÃO COMPLETA DO PRATO: Identifique CADA alimento ou bebida visível separadamente na lista "items" (ex: Arroz Branco Cozido, Feijão Carioca, Peito de Frango Grelhado, Batata Frita, Salada de Alface e Tomate, Suco de Laranja, etc.) para que o usuário possa ajustar a quantidade individualmente no app.
        2. ESTIMATIVA PRECISA DE PORÇÃO (GRAMAS / ML):
           - Use referências visuais de escala (diâmetro médio de prato raso ~26cm, prato fundo ~22cm, talheres, copos de 200ml/300ml, altura/volume das porções e espessura de carnes/fatias).
           - Se a foto mostrar uma embalagem ou rótulo/tabela nutricional, leia o nome exato do produto, o peso/porção indicada e os macronutrientes reais do rótulo.
           - Se o usuário informou alguma quantidade ou detalhe na observação adicional, respeite essa informação.
        3. UNIDADES (ML VS G): Para líquidos/bebidas/sopas líquidas, use "unit": "ml" e "is_liquid": true. Para alimentos sólidos ou em pó, use "unit": "g" e "is_liquid": false.
        4. CÁLCULO NUTRICIONAL REALISTA: Calcule calories, protein, carbohydrates e fat estimados para o portion_value de cada item (baseando-se na Tabela TACO / USDA) e some tudo em total_calories, total_protein, total_carbs e total_fat.
        
        Responda ESTRITAMENTE em formato JSON com a seguinte estrutura:
        {
          "meal_type": "Café da Manhã" | "Almoço" | "Jantar" | "Lanches",
          "items": [
            {
              "name": "Nome claro do alimento em português",
              "portion_value": 150.0,
              "unit": "g" | "ml",
              "is_liquid": false,
              "calories": 240.0,
              "protein": 46.5,
              "carbohydrates": 0.0,
              "fat": 5.4
            }
          ],
          "total_calories": 240.0,
          "total_protein": 46.5,
          "total_carbs": 0.0,
          "total_fat": 5.4,
          "ai_summary": "Breve resumo visual em 1 frase explicando os itens e porções estimadas na foto"
        }
        """.formatted(extraNote);
    }

    private static String buildMealTextPrompt(String userDescription) {
        return """
        Você é o assistente educacional de registro nutricional e bem-estar do app Forma (caráter estritamente informativo).
        Analise a descrição informada pelo usuário (pode conter um ou múltiplos alimentos, pratos compostos, shakes ou porções coloquiais brasileiras como 'colher de sopa', 'concha', 'copo grande', 'pratão', 'dose', etc.).
        
        Descrição do usuário: "%s"
        
        DIRETRIZES DE DECOMPOSIÇÃO E MEDIDAS:
        1. MÚLTIPLOS ALIMENTOS: Separe cada alimento individualmente na lista "items" (ex: "4 colheres de sopa de arroz e 2 de feijão" gera 2 itens: Arroz ~100g e Feijão ~60g).
        2. SHAKES E BEBIDAS HIPERCALÓRICAS: Quando o usuário citar um shake (ex: "400 ml de shake hipercalorico growth"), compile em subitens ajustáveis para compor o prato:
           - Base líquida: "Leite Integral" (com o volume do shake, ex: 350ml a 400ml, unit: "ml", is_liquid: true)
           - Suplemento: "Hipercalórico Growth" (ou a marca citada) com a dose padrão recomendada pelo fabricante (ex: 100g a 120g de hipercalórico, unit: "g", is_liquid: false).
        3. PRATOS COMPOSTOS CASEIROS: Ex: "dois pães franceses médios com manteiga e 1 copo grande de leite com toddy":
           - "Pão Francês" (2 un = ~100g, unit: "g", is_liquid: false)
           - "Manteiga" (~15g, unit: "g", is_liquid: false)
           - "Leite Integral" (1 copo grande = ~300ml, unit: "ml", is_liquid: true)
           - "Achocolatado Toddy" (~20g, unit: "g", is_liquid: false)
        4. UNIDADES (ML VS G): Para qualquer líquido ou bebida, use "unit": "ml" e "is_liquid": true. Para sólidos ou pós, use "unit": "g" e "is_liquid": false.
        5. SOMA TOTAL: Calcule a soma exata de todos os itens em total_calories, total_protein, total_carbs e total_fat.
        
        Responda ESTRITAMENTE em formato JSON com a seguinte estrutura:
        {
          "meal_type": "Café da Manhã" | "Almoço" | "Jantar" | "Lanches",
          "items": [
            {
              "name": "Nome do alimento em português",
              "portion_value": 100.0,
              "unit": "g" | "ml",
              "is_liquid": false,
              "calories": 130.0,
              "protein": 2.7,
              "carbohydrates": 28.2,
              "fat": 0.3
            }
          ],
          "total_calories": 130.0,
          "total_protein": 2.7,
          "total_carbs": 28.2,
          "total_fat": 0.3,
          "ai_summary": "Breve resumo nutricional objetivo em 1 frase"
        }
        """.formatted(userDescription);
    }

    private static final String CANONICAL_VIDEO_IDS_GUIDE = """
        CATÁLOGO OFICIAL DE VÍDEOS 3D DE EXECUÇÃO (10s) - OBRIGATÓRIO ASSOCIAR UM animation_id VÁLIDO EM CADA EXERCÍCIO (NUNCA null):
        - Peitoral: supino_reto, supino_inclinado, supino_declinado, supino_reto_halteres, supino_inclinado_halteres, crucifixo, crucifixo_inclinado, crossover_polia, voador_peck_deck, flexao_de_braco, paralelas_peito
        - Costas: puxada_alta, puxada_triangulo, puxada_articulada, pulldown_corda, pull_over, barra_fixa, chin_up, remada_curvada, remada_baixa, remada_unilateral, remada_cavalinho, levantamento_terra
        - Pernas: agachamento_livre, agachamento_frontal, agachamento_sumo, agachamento_hack, leg_press, afundo, agachamento_bulgaro, stiff, elevacao_pelvica, cadeira_extensora, mesa_flexora, cadeira_flexora, panturrilha, panturrilha_sentado
        - Ombros: desenvolvimento, desenvolvimento_barra, desenvolvimento_arnold, elevacao_lateral, elevacao_lateral_polia, elevacao_lateral_inclinado, elevacao_frontal, crucifixo_invertido, crucifixo_inverso_maquina, face_pull, encolhimento, remada_alta
        - Bíceps: rosca_direta, rosca_alternada, rosca_inversa, rosca_21, rosca_martelo, rosca_martelo_corda, rosca_scott, rosca_concentrada, rosca_inclinada, rosca_spider, rosca_bayesian
        - Tríceps: triceps_corda, triceps_barra_reta, triceps_barra_v, triceps_testa, supino_fechado, triceps_frances, triceps_frances_polia, triceps_coice, triceps_kickback_cabo, triceps_paralelas, mergulho_banco
        - Abdômen: abdominal_crunch, abdominal_polia, abdominal_remador, abdominal_bicicleta, abdominal_declinado, abdominal_infra, elevacao_pernas_barra, roda_abdominal, prancha, prancha_lateral, vacuum_abdominal
        """;

    private static final Set<String> VALID_VIDEO_IDS = Set.of(
        "supino_reto", "supino_inclinado", "supino_declinado", "supino_reto_halteres", "supino_inclinado_halteres",
        "crucifixo", "crucifixo_inclinado", "crossover_polia", "voador_peck_deck", "flexao_de_braco", "paralelas_peito",
        "puxada_alta", "puxada_triangulo", "puxada_articulada", "pulldown_corda", "pull_over", "barra_fixa", "chin_up",
        "remada_curvada", "remada_baixa", "remada_unilateral", "remada_cavalinho", "levantamento_terra",
        "agachamento_livre", "agachamento_frontal", "agachamento_sumo", "agachamento_hack", "leg_press", "afundo",
        "agachamento_bulgaro", "stiff", "elevacao_pelvica", "cadeira_extensora", "mesa_flexora", "cadeira_flexora",
        "panturrilha", "panturrilha_sentado",
        "desenvolvimento", "desenvolvimento_barra", "desenvolvimento_arnold", "elevacao_lateral", "elevacao_lateral_polia",
        "elevacao_lateral_inclinado", "elevacao_frontal", "crucifixo_invertido", "crucifixo_inverso_maquina", "face_pull",
        "encolhimento", "remada_alta",
        "rosca_direta", "rosca_alternada", "rosca_inversa", "rosca_21", "rosca_martelo", "rosca_martelo_corda",
        "rosca_scott", "rosca_concentrada", "rosca_inclinada", "rosca_spider", "rosca_bayesian",
        "triceps_corda", "triceps_barra_reta", "triceps_barra_v", "triceps_testa", "supino_fechado", "triceps_frances",
        "triceps_frances_polia", "triceps_coice", "triceps_kickback_cabo", "triceps_paralelas", "mergulho_banco",
        "abdominal_crunch", "abdominal_polia", "abdominal_remador", "abdominal_bicicleta", "abdominal_declinado",
        "abdominal_infra", "elevacao_pernas_barra", "roda_abdominal", "prancha", "prancha_lateral", "vacuum_abdominal"
    );

    private String ensureExerciseVideoIds(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return rawJson;
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if (!(root instanceof ObjectNode rootObj)) return rawJson;
            boolean changed = false;
            if (rootObj.get("exercises") instanceof ArrayNode arr) {
                changed |= enrichExercisesArray(arr);
            }
            if (rootObj.get("recommended_routine") instanceof ObjectNode recObj
                && recObj.get("exercises") instanceof ArrayNode recArr) {
                changed |= enrichExercisesArray(recArr);
            }
            return changed ? objectMapper.writeValueAsString(rootObj) : rawJson;
        } catch (Exception ignored) {
            return rawJson;
        }
    }

    private static boolean enrichExercisesArray(ArrayNode arr) {
        boolean modified = false;
        for (JsonNode item : arr) {
            if (item instanceof ObjectNode exObj) {
                String currentId = exObj.path("animation_id").asText("").trim().toLowerCase(Locale.ROOT);
                if (!VALID_VIDEO_IDS.contains(currentId)) {
                    String name = exObj.path("name").asText("");
                    String muscle = exObj.path("target_muscle").asText("");
                    String resolved = resolveBestVideoId(name + " " + currentId, muscle);
                    exObj.put("animation_id", resolved);
                    modified = true;
                }
            }
        }
        return modified;
    }

    private static String resolveBestVideoId(String rawName, String rawMuscle) {
        String text = Normalizer.normalize((rawName + " " + rawMuscle).toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");

        // Regras específicas das variações mais ricas antes dos movimentos principais
        if (text.contains("supino") && text.contains("inclinado") && text.contains("halter")) return "supino_inclinado_halteres";
        if (text.contains("supino") && text.contains("inclinado")) return "supino_inclinado";
        if (text.contains("supino") && text.contains("declinado")) return "supino_declinado";
        if (text.contains("supino") && text.contains("halter")) return "supino_reto_halteres";
        if (text.contains("supino") && text.contains("fechado")) return "supino_fechado";
        if (text.contains("supino")) return "supino_reto";
        if (text.contains("crucifixo") && (text.contains("invers") || text.contains("posterior"))) {
            return (text.contains("maquina") || text.contains("peck") || text.contains("voador")) ? "crucifixo_inverso_maquina" : "crucifixo_invertido";
        }
        if (text.contains("voador") && (text.contains("invers") || text.contains("costas"))) return "crucifixo_inverso_maquina";
        if (text.contains("crossover") || (text.contains("crucifixo") && text.contains("polia"))) return "crossover_polia";
        if (text.contains("peck") || text.contains("voador")) return "voador_peck_deck";
        if (text.contains("crucifixo") && text.contains("inclinado")) return "crucifixo_inclinado";
        if (text.contains("crucifixo")) return "crucifixo";
        if (text.contains("paralela") && text.contains("peit")) return "paralelas_peito";
        if (text.contains("flexao")) return "flexao_de_braco";

        if (text.contains("pulldown")) return "pulldown_corda";
        if (text.contains("pull") && text.contains("over")) return "pull_over";
        if (text.contains("chin") || (text.contains("barra fixa") && text.contains("supin"))) return "chin_up";
        if (text.contains("barra fixa")) return "barra_fixa";
        if (text.contains("puxada") && (text.contains("triangulo") || text.contains("neutra") || text.contains("fechada"))) return "puxada_triangulo";
        if (text.contains("puxada") && (text.contains("articulada") || text.contains("maquina"))) return "puxada_articulada";
        if (text.contains("puxada") || text.contains("pulley")) return "puxada_alta";
        if (text.contains("remada") && text.contains("alta")) return "remada_alta";
        if (text.contains("remada") && (text.contains("unilateral") || text.contains("serrote"))) return "remada_unilateral";
        if (text.contains("remada") && text.contains("cavalinho")) return "remada_cavalinho";
        if (text.contains("remada") && (text.contains("baixa") || text.contains("polia") || text.contains("triangulo"))) return "remada_baixa";
        if (text.contains("remada")) return "remada_curvada";
        if (text.contains("levantamento terra") || text.contains("deadlift")) return "levantamento_terra";

        if (text.contains("leg press")) return "leg_press";
        if (text.contains("bulgaro")) return "agachamento_bulgaro";
        if (text.contains("afundo") || text.contains("passada") || text.contains("avanco")) return "afundo";
        if (text.contains("agachamento") && text.contains("frontal")) return "agachamento_frontal";
        if (text.contains("agachamento") && text.contains("sumo") || text.contains("sumo")) return "agachamento_sumo";
        if (text.contains("hack")) return "agachamento_hack";
        if (text.contains("agachamento")) return "agachamento_livre";
        if (text.contains("stiff") || text.contains("romeno") || text.contains("rdl")) return "stiff";
        if (text.contains("pelvica") || text.contains("hip thrust")) return "elevacao_pelvica";
        if (text.contains("extensora")) return "cadeira_extensora";
        if (text.contains("flexora") && (text.contains("sentad") || text.contains("cadeira"))) return "cadeira_flexora";
        if (text.contains("flexora")) return "mesa_flexora";
        if ((text.contains("panturrilha") || text.contains("gemeos") || text.contains("soleo")) && text.contains("sentad")) return "panturrilha_sentado";
        if (text.contains("panturrilha") || text.contains("gemeos") || text.contains("soleo")) return "panturrilha";

        if (text.contains("arnold")) return "desenvolvimento_arnold";
        if (text.contains("desenvolvimento") && (text.contains("barra") || text.contains("militar"))) return "desenvolvimento_barra";
        if (text.contains("desenvolvimento")) return "desenvolvimento";
        if (text.contains("elevacao") && text.contains("frontal")) return "elevacao_frontal";
        if (text.contains("elevacao") && text.contains("lateral") && text.contains("inclinad")) return "elevacao_lateral_inclinado";
        if (text.contains("elevacao") && text.contains("lateral") && (text.contains("polia") || text.contains("cabo"))) return "elevacao_lateral_polia";
        if (text.contains("elevacao") && text.contains("lateral")) return "elevacao_lateral";
        if (text.contains("face pull")) return "face_pull";
        if (text.contains("encolhimento")) return "encolhimento";

        if (text.contains("bayesian")) return "rosca_bayesian";
        if (text.contains("spider")) return "rosca_spider";
        if (text.contains("scott")) return "rosca_scott";
        if (text.contains("concentrad")) return "rosca_concentrada";
        if (text.contains("rosca") && text.contains("inclinad")) return "rosca_inclinada";
        if (text.contains("martelo") && (text.contains("corda") || text.contains("polia"))) return "rosca_martelo_corda";
        if (text.contains("martelo")) return "rosca_martelo";
        if (text.contains("rosca") && text.contains("inversa")) return "rosca_inversa";
        if (text.contains("rosca") && text.contains("21")) return "rosca_21";
        if (text.contains("rosca") && text.contains("alternad")) return "rosca_alternada";
        if (text.contains("rosca")) return "rosca_direta";

        if (text.contains("triceps") && text.contains("testa")) return "triceps_testa";
        if (text.contains("frances") && (text.contains("polia") || text.contains("corda"))) return "triceps_frances_polia";
        if (text.contains("frances")) return "triceps_frances";
        if ((text.contains("coice") || text.contains("kickback")) && (text.contains("cabo") || text.contains("polia"))) return "triceps_kickback_cabo";
        if (text.contains("coice") || text.contains("kickback")) return "triceps_coice";
        if (text.contains("mergulho")) return "mergulho_banco";
        if (text.contains("paralela")) return "triceps_paralelas";
        if (text.contains("triceps") && text.contains("barra v")) return "triceps_barra_v";
        if (text.contains("triceps") && text.contains("barra")) return "triceps_barra_reta";
        if (text.contains("triceps")) return "triceps_corda";

        if (text.contains("roda abdominal") || text.contains("ab wheel")) return "roda_abdominal";
        if (text.contains("vacuum") || text.contains("vacuo")) return "vacuum_abdominal";
        if (text.contains("prancha") && text.contains("lateral")) return "prancha_lateral";
        if (text.contains("prancha")) return "prancha";
        if (text.contains("elevacao") && text.contains("perna") && text.contains("barra")) return "elevacao_pernas_barra";
        if (text.contains("infra") || (text.contains("elevacao") && text.contains("perna"))) return "abdominal_infra";
        if (text.contains("abdominal") && (text.contains("polia") || text.contains("corda"))) return "abdominal_polia";
        if (text.contains("remador")) return "abdominal_remador";
        if (text.contains("bicicleta")) return "abdominal_bicicleta";
        if (text.contains("abdominal") && text.contains("declinado")) return "abdominal_declinado";
        if (text.contains("abdom") || text.contains("core")) return "abdominal_crunch";

        if (text.contains("peito") || text.contains("peitoral")) return "supino_reto";
        if (text.contains("costa") || text.contains("dorsal")) return "puxada_alta";
        if (text.contains("ombro") || text.contains("deltoide")) return "desenvolvimento";
        if (text.contains("biceps")) return "rosca_direta";
        return "agachamento_livre";
    }

    private static String buildWorkoutPrompt(double weight, String description) {
        return """
        Você é o assistente educacional de organização de treinos e condicionamento físico do aplicativo Forma.
        Caráter estritamente informativo: NÃO prescreva cargas máximas em kg nem substitua orientação presencial de profissional de Educação Física (CREF).
        O usuário descreveu seu treino físico ou os exercícios realizados/planejados.
        Peso corporal anonimizado: %.1fkg.
        
        Descrição informada pelo usuário: "%s"
        
        DIRETRIZES TÉCNICAS:
        1. GASTO CALÓRICO (MET): Estime a queima calórica aproximada e duração baseando-se no peso corporal de %.1fkg.
        2. ANÁLISE EDUCATIVA: Informe target_muscles, hypertrophy_focus, recommended_rest_seconds e ai_feedback informativo.
        3. VÍDEO DE EXECUÇÃO OBRIGATÓRIO: Estruture cada exercício com name, sets, reps, suggested_weight ("Esforço moderado (ajuste presencial com CREF)"), target_muscle, execution_tip, rest_seconds e SEMPRE preencha "animation_id" (NUNCA retorne null) usando o ID exato do catálogo oficial de vídeos 3D abaixo.
        
        %s
        
        Responda ESTRITAMENTE em formato JSON com a seguinte estrutura:
        {
          "title": "Título conciso do treino",
          "workout_type": "Musculação" | "Corrida" | "Caminhada" | "Ciclismo" | "Funcional / HIIT",
          "duration_minutes": 50,
          "calories_burned": 380.0,
          "intensity": "Leve" | "Moderada" | "Alta" | "Intensa",
          "target_muscles": "Músculos principais",
          "hypertrophy_focus": "Foco do estímulo",
          "recommended_rest_seconds": 90,
          "ai_feedback": "Feedback educativo objetivo",
          "exercises": [
            {
              "name": "Supino Inclinado com Halteres",
              "sets": 4,
              "reps": "8-10",
              "suggested_weight": "Esforço moderado (ajuste presencial com CREF)",
              "target_muscle": "Peitoral Superior",
              "execution_tip": "Desça controlando o alongamento e mantenha postura estável.",
              "rest_seconds": 90,
              "animation_id": "supino_inclinado_halteres"
            }
          ]
        }
        """.formatted(weight, description, weight, CANONICAL_VIDEO_IDS_GUIDE);
    }

    private static String buildEvolutionPrompt(AiDto.EvolutionAiRequest request) {
        String goal = request.userGoal() != null ? request.userGoal() : "Ganhar peso";
        double weight = request.currentWeight() != null && request.currentWeight() > 30.0 ? request.currentWeight() : 72.0;
        double targetWeight = request.targetWeight() != null && request.targetWeight() > 30.0 ? request.targetWeight() : 80.0;
        double heightM = request.heightMeters() != null && request.heightMeters() >= 1.0 ? request.heightMeters() : 1.78;
        double targetBurn = request.targetCalorieBurn() != null && request.targetCalorieBurn() > 0 ? request.targetCalorieBurn() : 600.0;
        double targetSteps = request.targetSteps() != null && request.targetSteps() > 0 ? request.targetSteps() : 8000.0;
        double avgWorkoutBurn = request.avgDailyWorkoutBurn() != null ? request.avgDailyWorkoutBurn() : 0.0;

        double tmb = request.calculatedTmb() != null && request.calculatedTmb() > 0
            ? request.calculatedTmb()
            : (10.0 * weight) + (6.25 * heightM * 100.0) - (5.0 * 25.0) + 5.0;
        double neatFactor = 1.25 + Math.min(0.25, Math.max(0.08, ((targetSteps * 0.85) / 10000.0) * 0.15));
        double dailyExercise = Math.max(avgWorkoutBurn, targetBurn * 0.65);
        double contextualTdee = request.contextualTdee() != null && request.contextualTdee() > 0
            ? request.contextualTdee()
            : (tmb * neatFactor) + dailyExercise;
        double suggestedBase = request.suggestedBaseCalories() != null && request.suggestedBaseCalories() > 0
            ? request.suggestedBaseCalories()
            : (request.targetCalories() != null ? request.targetCalories() : Math.round((contextualTdee + 400.0) / 10.0) * 10.0);
        double currentTargetCal = request.targetCalories() != null ? request.targetCalories() : suggestedBase;

        return """
        Você é o assistente educacional de bem-estar, organização nutricional e condicionamento físico do aplicativo Forma (operado via Google Gemini).
        Mantenha linguagem estritamente informativa e educacional (NÃO emita diagnósticos clínicos/médicos, NÃO prescreva dietas terapêuticas, medicamentos ou cargas em kg e NÃO utilize dados pessoais nominativos).
        Analise o contexto fisiológico anonimizado e o arquivo de log de atividades dos últimos dias. Seja direto, técnico e conciso (2 a 3 frases objetivas por análise, exatamente 3 itens práticos nas listas de sugestões e 4 a 5 exercícios principais na ficha sugerida).
        
        CONTEXTO FISIOLÓGICO ANONIMIZADO (MOTOR FORMA):
        - Objetivo Principal: %s
        - Peso Atual: %.1f kg | Peso Alvo: %.1f kg | Altura: %.2f m
        - Taxa Metabólica Basal (TMB): %.0f kcal/dia
        - Passos Alvo: %.0f passos/dia | Meta de Gasto Ativo em Treino: %.0f kcal | Média Diária de Treinos Registrados: %.0f kcal/dia
        - Gasto Energético Total Contextual (TDEE Real = TMB + NEAT + Treinos): %.0f kcal/dia
        - Meta Contextual Calculada pelo App: %.0f kcal/dia (Meta salva atual no perfil: %.0f kcal/dia)
        
        DIRETRIZ DE CONSISTÊNCIA DE METAS (OBRIGATÓRIA):
        Ancore `suggested_targets` (`daily_calories` nunca abaixo de 1200 kcal, `protein_grams`, `carbs_grams`, `fat_grams`, `water_milliliters`) nesse contexto fisiológico (TDEE Contextual de %.0f kcal e Meta Contextual Base de %.0f kcal), explicando claramente na chave `explanation`.
        
        ARQUIVO DE LOG BRUTO ANONIMIZADO:
        %s
        
        %s
        
        Retorne ESTRITAMENTE um JSON válido com status_summary, calorie_diagnostic, macro_diagnostic, hydration_diagnostic, workout_suggestions (array de 3 strings), nutrition_suggestions (array de 3 strings), weekly_action_plan, suggested_targets (daily_calories, protein_grams, carbs_grams, fat_grams, water_milliliters, explanation) e recommended_routine (title, workout_type, target_muscles, hypertrophy_focus, estimated_duration_minutes, estimated_calories, recommended_rest_seconds, exercises com name, sets, reps, suggested_weight sempre como 'Esforço moderado (ajuste presencial com CREF)', target_muscle, execution_tip, rest_seconds e animation_id OBRIGATORIAMENTE preenchido com um ID válido do catálogo acima, nunca null).
        """.formatted(
            goal,
            weight, targetWeight, heightM,
            tmb,
            targetSteps, targetBurn, avgWorkoutBurn,
            contextualTdee,
            suggestedBase, currentTargetCal,
            contextualTdee, suggestedBase,
            request.activityLogJson() != null ? request.activityLogJson() : "[]",
            CANONICAL_VIDEO_IDS_GUIDE
        );
    }

    private void logAiRequest(UUID userId, String endpoint, String promptSummary, String status, long durationMs) {
        try {
            String summary = promptSummary != null && promptSummary.length() > 490
                ? promptSummary.substring(0, 490) + "..."
                : promptSummary;
            AiRequestLogEntity log = new AiRequestLogEntity(userId, endpoint, summary, status, durationMs);
            AiRequestLogEntity saved = aiRequestLogRepository.save(log);
            long total = aiRequestLogRepository.count();
            adminEventPublisherService.publishAiRequest(saved, total);
        } catch (Exception ignored) {
        }
    }
}
