package com.forma.backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class PrivacyDto {

    public record ConsentUpdateRequest(
        Boolean ageConfirmed18Plus,
        Boolean termsAccepted,
        Boolean sensitiveHealthConsentGranted,
        Boolean aiConsentGranted,
        String documentVersion,
        String sourcePlatform
    ) {}

    public record ConsentLedgerItemDto(
        String id,
        String consentType,
        boolean granted,
        String documentVersion,
        String documentSha256,
        String sourcePlatform,
        Instant recordedAtUtc
    ) {}

    public record ConsentStatusResponse(
        UUID userId,
        boolean ageConfirmed18Plus,
        String termsAcceptedVersion,
        Instant termsAcceptedAt,
        boolean sensitiveHealthConsentGranted,
        Instant healthConsentAcceptedAt,
        boolean aiConsentGranted,
        Instant aiConsentUpdatedAt,
        String currentPolicyVersion,
        String currentPolicySha256,
        List<ConsentLedgerItemDto> history
    ) {}
}
