package com.forma.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Guardrail fisiológico determinístico (CFN Lei 8.234/91, CONFEF Lei 9.696/98 e CFM):
 * impede metas calóricas perigosas (< 1200 kcal), protege IMC < 18.5, detecta menções clínicas
 * sensíveis e remove prescrições de carga em kg geradas automaticamente por IA.
 */
@Service
public class PhysiologicalSafetyGuard {

    public static final double MIN_SAFE_DAILY_CALORIES = 1200.0;
    public static final double MIN_HEALTHY_BMI = 18.5;

    private static final List<String> CLINICAL_RED_FLAGS = List.of(
        "gravidez", "grávida", "gestante", "gestação", "amamenta", "lactante",
        "diabetes", "insulina", "hipoglicemia", "hipertensão", "pressão alta",
        "cardiopatia", "insuficiência renal", "doença renal", "nefropatia",
        "anorexia", "bulimia", "compulsão alimentar", "transtorno alimentar",
        "lesão aguda", "fratura", "hérnia", "cirurgia recente"
    );

    private static final Set<String> HEALTH_DISCLAIMER_KEY = Set.of("disclaimer");
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String sanitizeInitialGoalsJson(String rawJson, double currentWeightKg, double heightMeters) {
        if (rawJson == null || rawJson.isBlank()) return rawJson;
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if (!(root instanceof ObjectNode obj)) return rawJson;

            double rawCal = obj.path("daily_calories").asDouble(MIN_SAFE_DAILY_CALORIES);
            double clampedCal = Math.max(MIN_SAFE_DAILY_CALORIES, Math.round(rawCal / 10.0) * 10.0);
            boolean wasClamped = rawCal < MIN_SAFE_DAILY_CALORIES;
            obj.put("daily_calories", clampedCal);

            if (heightMeters > 0.8 && heightMeters < 2.6) {
                double minHealthyWeight = Math.round((MIN_HEALTHY_BMI * heightMeters * heightMeters) * 10.0) / 10.0;
                double rawTargetWeight = obj.path("suggested_target_weight").asDouble(currentWeightKg);
                if (rawTargetWeight > 0 && rawTargetWeight < minHealthyWeight) {
                    obj.put("suggested_target_weight", minHealthyWeight);
                    wasClamped = true;
                }
            }

            if (wasClamped) {
                String existingExp = obj.path("explanation").asText("");
                obj.put("explanation", existingExp + " (Ajustado ao piso fisiológico mínimo de segurança; consulte um nutricionista/médico.)");
            }
            obj.put("disclaimer", "Estimativa educacional de bem-estar. Não substitui acompanhamento de médico, nutricionista (CRN) ou profissional de Educação Física (CREF).");
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return rawJson;
        }
    }

    public String sanitizeWorkoutJson(String rawJson, String userContextText) {
        if (rawJson == null || rawJson.isBlank()) return rawJson;
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if (!(root instanceof ObjectNode obj)) return rawJson;

            if (obj.get("exercises") instanceof ArrayNode arr) {
                stripKgLoadRecommendations(arr);
            }
            if (containsClinicalRedFlag(userContextText)) {
                String existing = obj.path("ai_feedback").asText("");
                obj.put("ai_feedback", existing + " Atenção: ao mencionar condição clínica ou lesão, realize avaliação presencial com médico/CREF antes de iniciar ou adaptar treinos.");
            }
            obj.put("disclaimer", "Sugestão educacional de organização de treino. Ajuste cargas e execução presencialmente com profissional de Educação Física (CREF).");
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return rawJson;
        }
    }

    public String sanitizeEvolutionJson(String rawJson, String contextText, double currentWeightKg, double heightMeters) {
        if (rawJson == null || rawJson.isBlank()) return rawJson;
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if (!(root instanceof ObjectNode obj)) return rawJson;

            boolean lowBmi = heightMeters > 0.8 && (currentWeightKg / (heightMeters * heightMeters)) < MIN_HEALTHY_BMI;
            boolean hasClinicalFlag = containsClinicalRedFlag(contextText);

            if (obj.get("suggested_targets") instanceof ObjectNode targets) {
                double cal = targets.path("daily_calories").asDouble(MIN_SAFE_DAILY_CALORIES);
                double safeFloor = (lowBmi || hasClinicalFlag) ? Math.max(1600.0, MIN_SAFE_DAILY_CALORIES) : MIN_SAFE_DAILY_CALORIES;
                if (cal < safeFloor) {
                    targets.put("daily_calories", safeFloor);
                }
            }

            if (obj.get("recommended_routine") instanceof ObjectNode rec && rec.get("exercises") instanceof ArrayNode arr) {
                stripKgLoadRecommendations(arr);
            }

            if (hasClinicalFlag || lowBmi) {
                String summary = obj.path("status_summary").asText("");
                obj.put("status_summary", summary + " Aviso de segurança: para quadros clínicos, restrições ou IMC abaixo de 18,5, consulte presencialmente médico (CRM) ou nutricionista (CRN).");
            }
            obj.put("disclaimer", "Panorama informativo gerado por IA educacional (não constitui diagnóstico médico nem prescrição nutricional/esportiva).");
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return rawJson;
        }
    }

    private void stripKgLoadRecommendations(ArrayNode exercises) {
        for (JsonNode item : exercises) {
            if (item instanceof ObjectNode exObj) {
                // Substitui qualquer prescrição de kg por orientação neutra de percepção de esforço (RPE)
                exObj.put("suggested_weight", "Esforço moderado (ajuste presencial com CREF)");
            }
        }
    }

    public boolean containsClinicalRedFlag(String text) {
        if (text == null || text.isBlank()) return false;
        String lower = text.toLowerCase(Locale.forLanguageTag("pt-BR"));
        for (String flag : CLINICAL_RED_FLAGS) {
            if (lower.contains(flag)) {
                return true;
            }
        }
        return false;
    }
}
