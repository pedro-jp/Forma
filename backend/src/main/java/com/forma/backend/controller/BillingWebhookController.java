package com.forma.backend.controller;

import com.forma.backend.dto.BillingDto.WebhookEventDto;
import com.forma.backend.service.SubscriptionBillingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
public class BillingWebhookController {

    private final SubscriptionBillingService billingService;

    public BillingWebhookController(SubscriptionBillingService billingService) {
        this.billingService = billingService;
    }

    @PostMapping("/billing")
    public ResponseEntity<WebhookEventDto> receiveBillingWebhook(
        @RequestHeader(value = "X-Forma-Webhook-Signature", required = false) String signature,
        @RequestBody String rawPayload
    ) {
        WebhookEventDto processed = billingService.processWebhook(rawPayload, signature, false);
        return ResponseEntity.ok(processed);
    }
}
