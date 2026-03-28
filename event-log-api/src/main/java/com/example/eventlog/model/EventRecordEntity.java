package com.example.eventlog.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "event_records",
        indexes = {
                @Index(name = "ix_event_records_resolved_timestamp", columnList = "resolved_timestamp DESC"),
                @Index(name = "ix_event_records_caller_timestamp", columnList = "caller_id,resolved_timestamp DESC")
        },
        uniqueConstraints = {
                @jakarta.persistence.UniqueConstraint(name = "ux_event_records_dedupe", columnNames = {"caller_id", "resolved_timestamp", "message_hash"})
        }
)
public class EventRecordEntity {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID id;

    @Column(name = "caller_id", nullable = false, length = 128)
    private String callerId;

    @Column(name = "severity", nullable = false, length = 16)
    private String severity;

    @Column(name = "message", nullable = false, length = 4096)
    private String message;

    @Column(name = "metadata_json")
    private String metadataJson;

    @Column(name = "client_timestamp")
    private Instant clientTimestamp;

    @Column(name = "received_timestamp", nullable = false)
    private Instant receivedTimestamp;

    @Column(name = "resolved_timestamp", nullable = false)
    private Instant resolvedTimestamp;

    @Column(name = "timestamp_source", nullable = false, length = 16)
    private String timestampSource;

    @Column(name = "message_hash", nullable = false, length = 96)
    private String messageHash;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected EventRecordEntity() {
        // JPA
    }

    private EventRecordEntity(Builder builder) {
        this.id = builder.id;
        this.callerId = builder.callerId;
        this.severity = builder.severity;
        this.message = builder.message;
        this.metadataJson = builder.metadataJson;
        this.clientTimestamp = builder.clientTimestamp;
        this.receivedTimestamp = builder.receivedTimestamp;
        this.resolvedTimestamp = builder.resolvedTimestamp;
        this.timestampSource = builder.timestampSource;
        this.messageHash = builder.messageHash;
        this.status = builder.status;
        this.correlationId = builder.correlationId;
        this.expiresAt = builder.expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public String getCallerId() {
        return callerId;
    }

    public String getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public Instant getClientTimestamp() {
        return clientTimestamp;
    }

    public Instant getReceivedTimestamp() {
        return receivedTimestamp;
    }

    public Instant getResolvedTimestamp() {
        return resolvedTimestamp;
    }

    public String getTimestampSource() {
        return timestampSource;
    }

    public String getMessageHash() {
        return messageHash;
    }

    public String getStatus() {
        return status;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID id;
        private String callerId;
        private String severity;
        private String message;
        private String metadataJson;
        private Instant clientTimestamp;
        private Instant receivedTimestamp;
        private Instant resolvedTimestamp;
        private String timestampSource;
        private String messageHash;
        private String status;
        private String correlationId;
        private Instant expiresAt;

        private Builder() {
        }

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder callerId(String callerId) {
            this.callerId = callerId;
            return this;
        }

        public Builder severity(String severity) {
            this.severity = severity;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder metadataJson(String metadataJson) {
            this.metadataJson = metadataJson;
            return this;
        }

        public Builder clientTimestamp(Instant clientTimestamp) {
            this.clientTimestamp = clientTimestamp;
            return this;
        }

        public Builder receivedTimestamp(Instant receivedTimestamp) {
            this.receivedTimestamp = receivedTimestamp;
            return this;
        }

        public Builder resolvedTimestamp(Instant resolvedTimestamp) {
            this.resolvedTimestamp = resolvedTimestamp;
            return this;
        }

        public Builder timestampSource(String timestampSource) {
            this.timestampSource = timestampSource;
            return this;
        }

        public Builder messageHash(String messageHash) {
            this.messageHash = messageHash;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public EventRecordEntity build() {
            return new EventRecordEntity(this);
        }
    }
}
