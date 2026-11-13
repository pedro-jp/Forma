package com.forma.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "security_incident_logs")
public class SecurityIncidentLogEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id = UUID.randomUUID().toString();

    @Column(name = "severity", nullable = false, length = 32)
    private String severity;

    @Column(name = "incident_type", nullable = false, length = 80)
    private String incidentType;

    @Column(name = "affected_categories", length = 255)
    private String affectedCategories;

    @Column(name = "affected_count")
    private Integer affectedCount = 0;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "mitigation_actions", columnDefinition = "TEXT")
    private String mitigationActions;

    @Column(name = "anpd_notified", nullable = false)
    private boolean anpdNotified = false;

    @Column(name = "detected_at_utc", nullable = false)
    private Instant detectedAtUtc = Instant.now();

    @Column(name = "retain_until_utc", nullable = false)
    private Instant retainUntilUtc;

    public SecurityIncidentLogEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getIncidentType() { return incidentType; }
    public void setIncidentType(String incidentType) { this.incidentType = incidentType; }

    public String getAffectedCategories() { return affectedCategories; }
    public void setAffectedCategories(String affectedCategories) { this.affectedCategories = affectedCategories; }

    public Integer getAffectedCount() { return affectedCount; }
    public void setAffectedCount(Integer affectedCount) { this.affectedCount = affectedCount; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getMitigationActions() { return mitigationActions; }
    public void setMitigationActions(String mitigationActions) { this.mitigationActions = mitigationActions; }

    public boolean isAnpdNotified() { return anpdNotified; }
    public void setAnpdNotified(boolean anpdNotified) { this.anpdNotified = anpdNotified; }

    public Instant getDetectedAtUtc() { return detectedAtUtc; }
    public void setDetectedAtUtc(Instant detectedAtUtc) { this.detectedAtUtc = detectedAtUtc; }

    public Instant getRetainUntilUtc() { return retainUntilUtc; }
    public void setRetainUntilUtc(Instant retainUntilUtc) { this.retainUntilUtc = retainUntilUtc; }
}
