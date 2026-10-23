package br.com.saasfinder.reviews.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Token de confirmacao de uma review (spec secao 4.2, plano DT-04).
 *
 * <p>Uma linha por review, e o token guardado <b>so como hash</b>: quem le o banco
 * nao confirma review de outra pessoa. Reenviar substitui o hash e a validade em
 * vez de criar uma segunda linha - dois links validos ao mesmo tempo dobram a
 * janela de ataque sem ajudar ninguem.
 */
@Entity
@Table(name = "review_verification")
public class ReviewVerification {

    /** Validade do token (spec secao 4.2). Depois disso, 410 e a opcao de reenviar. */
    public static final Duration VALIDADE = Duration.ofHours(48);

    /** Chave primaria e estrangeira ao mesmo tempo: uma verificacao por review. */
    @Id
    private UUID reviewId;

    private String tokenHash;
    private Instant expiresAt;
    private Instant usedAt;

    @Generated(event = EventType.INSERT)
    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(insertable = false, updatable = false)
    private Instant updatedAt;

    protected ReviewVerification() {}

    public ReviewVerification(UUID reviewId, String tokenHash, Instant expiraEm) {
        this.reviewId = reviewId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiraEm.truncatedTo(ChronoUnit.MICROS);
    }

    /** Substitui o token no reenvio. O anterior para de valer no mesmo instante. */
    public void substituirToken(String tokenHash, Instant expiraEm) {
        this.tokenHash = tokenHash;
        this.expiresAt = expiraEm.truncatedTo(ChronoUnit.MICROS);
        this.usedAt = null;
    }

    /** Uso unico: a segunda tentativa com o mesmo token vira 410 (AC-03). */
    public void marcarUsado(Instant quando) {
        this.usedAt = quando.truncatedTo(ChronoUnit.MICROS);
    }

    public boolean estaUsado() {
        return usedAt != null;
    }

    public boolean expirouEm(Instant momento) {
        return !momento.isBefore(expiresAt);
    }

    public UUID getReviewId() {
        return reviewId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
