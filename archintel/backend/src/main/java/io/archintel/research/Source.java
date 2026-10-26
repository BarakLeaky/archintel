package io.archintel.research;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "sources")
public class Source {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private SourceType sourceType;

    @Column(columnDefinition = "text")
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String url;

    @Column(name = "canonical_url", nullable = false, columnDefinition = "text")
    private String canonicalUrl;

    @Column(length = 255)
    private String domain;

    @Column(name = "external_id", length = 255)
    private String externalId;

    @Column(length = 255)
    private String author;

    @Column(length = 255)
    private String publisher;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(columnDefinition = "text")
    private String excerpt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    protected Source() {
    }

    public Source(
            SourceType sourceType,
            String title,
            String url,
            String canonicalUrl,
            String domain,
            String externalId,
            String author,
            String publisher,
            Instant publishedAt,
            Instant updatedAt,
            Instant firstSeenAt,
            String contentHash,
            String excerpt,
            Map<String, Object> metadata) {

        this.id = UUID.randomUUID();
        this.sourceType = sourceType;
        this.title = title;
        this.url = url;
        this.canonicalUrl = canonicalUrl;
        this.domain = domain;
        this.externalId = externalId;
        this.author = author;
        this.publisher = publisher;
        this.publishedAt = publishedAt;
        this.updatedAt = updatedAt;
        this.firstSeenAt = firstSeenAt;
        this.lastSeenAt = firstSeenAt;
        this.contentHash = contentHash;
        this.excerpt = excerpt;
        this.metadata = metadata;
    }

    public UUID getId() { return id; }
    public SourceType getSourceType() { return sourceType; }
    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public String getCanonicalUrl() { return canonicalUrl; }
    public String getDomain() { return domain; }
    public String getExternalId() { return externalId; }
    public String getAuthor() { return author; }
    public String getPublisher() { return publisher; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public String getContentHash() { return contentHash; }
    public String getExcerpt() { return excerpt; }
    public Map<String, Object> getMetadata() { return metadata; }

    public void refresh(
            String title,
            String url,
            String domain,
            String externalId,
            String author,
            String publisher,
            Instant publishedAt,
            Instant updatedAt,
            String contentHash,
            String excerpt,
            Map<String, Object> metadata,
            Instant seenAt) {

        this.title = title;
        this.url = url;
        this.domain = domain;
        this.externalId = externalId;
        this.author = author;
        this.publisher = publisher;
        this.publishedAt = publishedAt;
        this.updatedAt = updatedAt;
        this.contentHash = contentHash;
        this.excerpt = excerpt;
        this.metadata = metadata;
        this.lastSeenAt = seenAt;
    }
}
