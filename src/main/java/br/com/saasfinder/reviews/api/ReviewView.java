package br.com.saasfinder.reviews.api;

import br.com.saasfinder.reviews.domain.Review;
import java.time.Instant;
import java.util.UUID;

/**
 * Uma review como o visitante a ve (spec secao 5.1).
 *
 * <p>O que importa aqui e o que <b>nao</b> existe: nem {@code author_email}, nem
 * {@code moderation_note}, nem {@code moderated_by}, nem nada de
 * {@code review_verification} (AC-10). O record e a fronteira - campo que nao
 * esta declarado nao vaza por descuido de serializacao.
 *
 * <p>{@code locale} sai de proposito: e o idioma em que a review foi escrita, e e
 * ele que a ficha usa para marcar "escrita em ingles" (AC-15).
 */
public record ReviewView(
        UUID id,
        short rating,
        boolean wouldRecommend,
        String title,
        String body,
        String locale,
        String authorName,
        String authorDomain,
        boolean verified,
        Instant publishedAt) {

    public static ReviewView de(Review review) {
        return new ReviewView(
                review.getId(),
                review.getRating(),
                review.isWouldRecommend(),
                review.getTitle(),
                review.getBody(),
                review.getLocale(),
                review.getAuthorName(),
                review.getAuthorDomain(),
                review.isVerified(),
                review.getPublishedAt());
    }
}
