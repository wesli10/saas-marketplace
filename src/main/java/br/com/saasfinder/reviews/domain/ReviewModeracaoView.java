package br.com.saasfinder.reviews.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma review como o curador a ve (spec secao 5.2).
 *
 * <p>Traz o texto completo, o dominio do autor e todo o rastro de moderacao - e
 * <b>nao traz o e-mail</b>. A decisao e sobre o que foi escrito, nao sobre quem
 * escreveu: o dominio ja diz o que importa para o selo, e o endereco completo so
 * aumentaria a exposicao de dado pessoal sem mudar nenhuma decisao.
 *
 * @param outrasDoMesmoDominio quantas <b>outras</b> reviews daquele produto vem do
 *     mesmo dominio. E o sinal que a spec Q-05 pos no lugar do bloqueio por
 *     dominio: cinco reviews de @acme.com.br no mesmo produto merecem um olhar,
 *     mas barrar a segunda calaria o colega de quem ja avaliou.
 */
public record ReviewModeracaoView(
        UUID id,
        String productSlug,
        String productName,
        short rating,
        boolean wouldRecommend,
        String title,
        String body,
        String locale,
        String authorName,
        String authorDomain,
        boolean verified,
        ReviewStatus status,
        long outrasDoMesmoDominio,
        String moderationNote,
        String moderatedBy,
        Instant moderatedAt,
        Instant createdAt,
        Instant confirmedAt,
        Instant publishedAt) {}
