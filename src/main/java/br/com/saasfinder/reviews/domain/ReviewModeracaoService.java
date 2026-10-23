package br.com.saasfinder.reviews.domain;

import br.com.saasfinder.catalog.domain.CatalogFacade;
import br.com.saasfinder.catalog.domain.ProdutoIdentificado;
import br.com.saasfinder.reviews.infra.ReviewRepository;
import br.com.saasfinder.shared.error.InvalidParameterException;
import br.com.saasfinder.shared.error.NotFoundException;
import br.com.saasfinder.shared.locale.AppLocale;
import br.com.saasfinder.shared.web.PagingParams;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A curadoria de reviews (spec secao 5.2).
 *
 * <p>Separado do {@link ReviewService} de proposito: um serve a quem escreve e
 * responde sempre igual para nao vazar nada; este serve a quem decide, e responde
 * com tudo. Misturar os dois faria um metodo publico e um de admin dividirem
 * caminho, que e como um campo de moderacao acaba num corpo publico.
 */
@Service
public class ReviewModeracaoService {

    private final CatalogFacade catalogo;
    private final ReviewRepository reviews;
    private final ApplicationEventPublisher eventos;

    public ReviewModeracaoService(CatalogFacade catalogo, ReviewRepository reviews, ApplicationEventPublisher eventos) {
        this.catalogo = catalogo;
        this.reviews = reviews;
        this.eventos = eventos;
    }

    /** A fila, filtrada por estado e por produto (spec secao 5.2). */
    @Transactional(readOnly = true)
    public PaginaDeModeracao fila(ReviewStatus status, String produtoSlug, PagingParams paginacao) {
        UUID produtoId = produtoSlug == null ? null : idDoProduto(produtoSlug);
        PageRequest pagina = PageRequest.of(paginacao.page(), paginacao.size());

        Page<Review> encontradas = buscar(status, produtoId, pagina);
        return new PaginaDeModeracao(paraViews(encontradas.getContent()), encontradas.getTotalElements());
    }

    private Page<Review> buscar(ReviewStatus status, UUID produtoId, PageRequest pagina) {
        if (status != null && produtoId != null) {
            return reviews.findByStatusAndProductIdOrderByCreatedAtAsc(status, produtoId, pagina);
        }
        if (status != null) {
            return reviews.findByStatusOrderByCreatedAtAsc(status, pagina);
        }
        if (produtoId != null) {
            return reviews.findByProductIdOrderByCreatedAtAsc(produtoId, pagina);
        }
        return reviews.findByOrderByCreatedAtAsc(pagina);
    }

    /**
     * Aprova e publica (AC-08).
     *
     * <p>Review que ainda nao confirmou o e-mail responde 409, e nao 400: e a
     * transicao invalida de {@link Review#publicar}, e o pedido nao esta errado - o
     * estado e que nao permite.
     */
    @Transactional
    public ReviewModeracaoView aprovar(UUID id, String curador) {
        Review review = carregar(id);
        review.publicar(curador);

        ProdutoIdentificado produto = produtoDe(review);
        // Avisa quem escreveu, e so quando publica (spec Q-02). Depois do commit,
        // como todo e-mail desta fatia.
        eventos.publishEvent(new ReviewPublicada(
                review.getId(),
                review.getAuthorEmail(),
                AppLocale.porCodigo(review.getLocale()).orElse(AppLocale.PT_BR),
                review.getAuthorName(),
                produto.name(),
                produto.slug()));

        return paraView(review, produto);
    }

    /** Rejeita, sempre com motivo (AC-08). Nao avisa o autor (spec Q-02). */
    @Transactional
    public ReviewModeracaoView rejeitar(UUID id, String motivo, String curador) {
        Review review = carregar(id);
        review.rejeitar(motivo, curador);
        return paraView(review, produtoDe(review));
    }

    /** Registra a decisao de manter depois de uma contestacao (spec secao 7.1, AC-21c). */
    @Transactional
    public ReviewModeracaoView registrarDecisaoDeManter(UUID id, String motivo, String curador) {
        Review review = carregar(id);
        review.registrarDecisaoDeManter(motivo, curador);
        return paraView(review, produtoDe(review));
    }

    private Review carregar(UUID id) {
        return reviews.findById(id).orElseThrow(() -> new NotFoundException("problema.review.nao-encontrada", id));
    }

    private UUID idDoProduto(String slug) {
        // Slug desconhecido no filtro vira 400, e nao lista vazia: erro de digitacao
        // deve aparecer como tal, e nao virar "nenhuma review na fila".
        return catalogo.identificarPublicado(slug)
                .orElseThrow(() -> new InvalidParameterException("problema.invalid-parameter.detalhe", "product"))
                .id();
    }

    private ProdutoIdentificado produtoDe(Review review) {
        return catalogo.identificarPorIds(List.of(review.getProductId())).get(review.getProductId());
    }

    private ReviewModeracaoView paraView(Review review, ProdutoIdentificado produto) {
        long doMesmoDominio = reviews.countByProductIdAndAuthorDomain(review.getProductId(), review.getAuthorDomain());
        return montar(review, produto, doMesmoDominio - 1);
    }

    /** A pagina inteira em duas consultas: os produtos e a contagem por dominio. */
    private List<ReviewModeracaoView> paraViews(List<Review> encontradas) {
        Set<UUID> produtos = new LinkedHashSet<>(
                encontradas.stream().map(Review::getProductId).toList());
        Map<UUID, ProdutoIdentificado> porId = catalogo.identificarPorIds(produtos);

        Map<String, Long> porProdutoEDominio = new HashMap<>();
        for (Object[] linha : reviews.contarPorProdutoEDominio(produtos)) {
            porProdutoEDominio.put(chave((UUID) linha[0], (String) linha[1]), (Long) linha[2]);
        }

        return encontradas.stream()
                .map(review -> montar(
                        review,
                        porId.get(review.getProductId()),
                        porProdutoEDominio.getOrDefault(chave(review.getProductId(), review.getAuthorDomain()), 1L)
                                - 1))
                .toList();
    }

    private static String chave(UUID produto, String dominio) {
        return produto + "|" + dominio;
    }

    private static ReviewModeracaoView montar(Review review, ProdutoIdentificado produto, long outrasDoMesmoDominio) {
        return new ReviewModeracaoView(
                review.getId(),
                produto == null ? null : produto.slug(),
                produto == null ? null : produto.name(),
                review.getRating(),
                review.isWouldRecommend(),
                review.getTitle(),
                review.getBody(),
                review.getLocale(),
                review.getAuthorName(),
                review.getAuthorDomain(),
                review.isVerified(),
                review.getStatus(),
                outrasDoMesmoDominio,
                review.getModerationNote(),
                review.getModeratedBy(),
                review.getModeratedAt(),
                review.getCreatedAt(),
                review.getConfirmedAt(),
                review.getPublishedAt());
    }
}
