package com.forma.backend.controller;

import com.forma.backend.dto.PrivacyDto;
import com.forma.backend.entity.User;
import com.forma.backend.security.AuthContextResolver;
import com.forma.backend.service.PrivacyComplianceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/privacy")
public class PrivacyComplianceController {

    private final AuthContextResolver authContextResolver;
    private final PrivacyComplianceService privacyComplianceService;

    public PrivacyComplianceController(
        AuthContextResolver authContextResolver,
        PrivacyComplianceService privacyComplianceService
    ) {
        this.authContextResolver = authContextResolver;
        this.privacyComplianceService = privacyComplianceService;
    }

    @GetMapping("/consents")
    public ResponseEntity<PrivacyDto.ConsentStatusResponse> getConsents(
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        User user = authContextResolver.requireActiveUser(authHeader);
        return ResponseEntity.ok(privacyComplianceService.getConsentStatus(user));
    }

    @PostMapping("/consents")
    public ResponseEntity<PrivacyDto.ConsentStatusResponse> updateConsents(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestBody PrivacyDto.ConsentUpdateRequest request,
        HttpServletRequest httpRequest
    ) {
        User user = authContextResolver.requireActiveUser(authHeader);
        return ResponseEntity.ok(privacyComplianceService.updateConsents(user, request, httpRequest));
    }

    @GetMapping(value = "/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> exportData(
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        User user = authContextResolver.requireActiveUser(authHeader);
        String filename = "forma-dados-lgpd-" + LocalDate.now() + ".json";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .body(privacyComplianceService.exportFullUserData(user));
    }
}
