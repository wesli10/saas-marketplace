package br.com.saasfinder.reviews.infra;

import br.com.saasfinder.reviews.domain.Review;
import br.com.saasfinder.reviews.domain.ReviewStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    /**
     * Ja existe review daquele e-mail para aquele produto? (AC-05)
     *
     * <p>O e-mail chega ja em minusculas - a entidade normaliza na criacao -, entao
     * a comparacao exata basta e usa o mesmo indice unico da V7.
     */
    boolean existsByProductIdAndAuthorEmail(UUID productId, String authorEmail);

    Optional<Review> findByProductIdAndAuthorEmail(UUID productId, String authorEmail);

    /**
     * A fila do curador, da mais antiga para a mais recente (spec secao 5.2).
     *
     * <p>Quatro metodos, e nao um com filtros opcionais: em JPQL, comparar um
     * parametro nulo com uma coluna de enum depende de o driver adivinhar o tipo, e
     * "adivinhar" e a palavra errada num gate de build. Quatro assinaturas
     * explicitas custam quatro linhas e nao dependem de sorte.
     *
     * <p>Mais antiga primeiro porque isto e uma fila: quem esperou mais e atendido
     * antes. E o contrario da rota publica, que poe a review mais recente no topo.
     */
    Page<Review> findByOrderByCreatedAtAsc(Pageable paginacao);

    Page<Review> findByStatusOrderByCreatedAtAsc(ReviewStatus status, Pageable paginacao);

    Page<Review> findByProductIdOrderByCreatedAtAsc(UUID productId, Pageable paginacao);

    Page<Review> findByStatusAndProductIdOrderByCreatedAtAsc(ReviewStatus status, UUID productId, Pageable paginacao);

    /**
     * O que o visitante ve: so as publicadas daquele produto, da mais recente para a
     * mais antiga (AC-11).
     *
     * <p>Usa o indice de leitura da V7, {@code (product_id, status, created_at desc)},
     * nas tres colunas e na ordem em que a consulta as pede.
     */
    Page<Review> findByProductIdAndStatusOrderByCreatedAtDesc(UUID productId, ReviewStatus status, Pageable paginacao);

    /** Quantas reviews daquele produto vem daquele dominio (sinal da spec Q-05). */
    long countByProductIdAndAuthorDomain(UUID productId, String authorDomain);

    /**
     * O mesmo sinal para uma pagina inteira, numa consulta so.
     *
     * <p>Perguntar linha a linha custaria uma consulta por review listada - o N+1
     * que o AC-14 proibe do outro lado da mesma fronteira, e que aqui ninguem
     * mediria, porque a fila e do curador e nao do visitante.
     */
    @Query("select r.productId, r.authorDomain, count(r) from Review r"
            + " where r.productId in :produtos group by r.productId, r.authorDomain")
    List<Object[]> contarPorProdutoEDominio(@Param("produtos") Collection<UUID> produtos);

    /**
     * Apaga as reviews que nunca sairam de {@code PENDING_EMAIL} (spec secao 4.2).
     *
     * <p>Dado pessoal que nao virou nada nao fica guardado: o e-mail continua no
     * banco depois de publicada (spec Q-03), e o preco disso e apagar o que nao foi
     * confirmado.
     *
     * <p>A linha de {@code review_verification} vai junto pelo {@code on delete
     * cascade} da V7 - um delete em massa por JPQL nao dispara cascata do JPA, e e
     * o banco que garante que nao sobra token orfao.
     */
    @Modifying
    @Query("delete from Review r where r.status = :status and r.createdAt < :limite")
    int expurgarPorEstadoAnterioresA(@Param("status") ReviewStatus status, @Param("limite") Instant limite);
}
