package com.forma.backend.controller;

import com.forma.backend.dto.AiDto;
import com.forma.backend.security.AuthContextResolver;
import com.forma.backend.service.GeminiAiProxyService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
public class AiProxyController {

    private final GeminiAiProxyService geminiAiProxyService;
    private final AuthContextResolver authContextResolver;

    public AiProxyController(
        GeminiAiProxyService geminiAiProxyService,
        AuthContextResolver authContextResolver
    ) {
        this.geminiAiProxyService = geminiAiProxyService;
        this.authContextResolver = authContextResolver;
    }

    @PostMapping(value = "/analyze-meal", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> analyzeMeal(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestHeader(value = "X-User-Id", required = false) String fallbackUserId,
        @RequestBody AiDto.MealAiRequest request
    ) {
        UUID userId = authContextResolver.validateAiAccess(authHeader, fallbackUserId);
        return ResponseEntity.ok(geminiAiProxyService.analyzeMeal(userId, request));
    }

    @PostMapping(value = "/analyze-workout", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> analyzeWorkout(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestHeader(value = "X-User-Id", required = false) String fallbackUserId,
        @RequestBody AiDto.WorkoutAiRequest request
    ) {
        UUID userId = authContextResolver.validateAiAccess(authHeader, fallbackUserId);
        return ResponseEntity.ok(geminiAiProxyService.analyzeWorkout(userId, request));
    }

    @PostMapping(value = "/analyze-evolution", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> analyzeEvolution(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestHeader(value = "X-User-Id", required = false) String fallbackUserId,
        @RequestBody AiDto.EvolutionAiRequest request
    ) {
        UUID userId = authContextResolver.validateAiAccess(authHeader, fallbackUserId);
        return ResponseEntity.ok(geminiAiProxyService.analyzeEvolution(userId, request));
    }

    @PostMapping(value = "/suggest-initial-goals", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> suggestInitialGoals(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestHeader(value = "X-User-Id", required = false) String fallbackUserId,
        @RequestBody AiDto.InitialGoalsAiRequest request
    ) {
        // Permite cálculo de metas iniciais mesmo se a chave da IA estiver indisponível (fallback fisiológico integrado)
        var user = authContextResolver.requireActiveUser(authHeader, fallbackUserId);
        boolean allowCloudAi = user.isPro() && !user.isAiBlocked();
        return ResponseEntity.ok(geminiAiProxyService.suggestInitialGoals(user.getId(), request, allowCloudAi));
    }
}
