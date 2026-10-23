package br.com.saasfinder.reviews.domain;

import br.com.saasfinder.catalog.domain.CatalogFacade;
import br.com.saasfinder.catalog.domain.ProdutoIdentificado;
import br.com.saasfinder.reviews.infra.ProvedoresGratuitos;
import br.com.saasfinder.reviews.infra.ReviewAggregateJdbc;
import br.com.saasfinder.reviews.infra.ReviewRepository;
import br.com.saasfinder.reviews.infra.ReviewVerificationRepository;
import br.com.saasfinder.shared.error.NotFoundException;
import br.com.saasfinder.shared.locale.AppLocale;
import br.com.saasfinder.shared.web.PagingParams;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Escrita de reviews (spec secao 5.1). */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final CatalogFacade catalogo;
    private final ReviewRepository reviews;
    private final ReviewVerificationRepository verificacoes;
    private final ProvedoresGratuitos provedoresGratuitos;
    private final ReviewAggregateJdbc agregados;
    private final ApplicationEventPublisher eventos;

    /**
     * Quanto tempo uma review nao confirmada fica no banco (spec secao 4.2). Dado
     * pessoal que nao virou nada nao envelhece guardado.
     */
    public static final Duration RETENCAO_SEM_CONFIRMACAO = Duration.ofDays(30);

    public ReviewService(
            CatalogFacade catalogo,
            ReviewRepository reviews,
            ReviewVerificationRepository verificacoes,
            ProvedoresGratuitos provedoresGratuitos,
            ReviewAggregateJdbc agregados,
            ApplicationEventPublisher eventos) {
        this.catalogo = catalogo;
        this.reviews = reviews;
        this.verificacoes = verificacoes;
        this.provedoresGratuitos = provedoresGratuitos;
        this.agregados = agregados;
        this.eventos = eventos;
    }

    /**
     * Registra a review em {@code PENDING_EMAIL} e agenda o e-mail de confirmacao
     * (AC-01).
     *
     * <p><b>Nao devolve nada, e isso e deliberado.</b> A resposta e a mesma tenha a
     * review sido criada agora ou ja existisse antes (AC-05): dizer "voce ja
     * avaliou" contaria, para quem apenas chutou o endereco, que aquele e-mail
     * avaliou aquele produto. Nem o retorno do metodo carrega essa informacao,
     * para que nenhum controller futuro tenha a tentacao de vaza-la.
     */
    @Transactional
    public void registrar(String slugDoProduto, DadosDeReview dados, AppLocale idioma) {
        ProdutoIdentificado produto = catalogo.identificarPublicado(slugDoProduto)
                .orElseThrow(() -> new NotFoundException("problema.produto.nao-encontrado", slugDoProduto));

        String email = dados.authorEmail().trim().toLowerCase(Locale.ROOT);

        if (reviews.existsByProductIdAndAuthorEmail(produto.id(), email)) {
            // Silencio deliberado: mesma resposta do primeiro envio, e nenhuma
            // segunda review. O indice unico da V7 e quem garante isso de verdade
            // quando dois envios chegam ao mesmo tempo.
            log.info(
                    "segundo envio ignorado para o produto {} e o dominio {}", produto.slug(), Review.dominioDe(email));
            return;
        }

        Review review = reviews.save(new Review(
                produto.id(),
                dados.rating(),
                dados.wouldRecommend(),
                dados.title(),
                dados.body(),
                idioma.codigo(),
                dados.authorName(),
                email));

        String token = TokenDeConfirmacao.novo();
        verificacoes.save(new ReviewVerification(
                review.getId(), TokenDeConfirmacao.hashDe(token), Instant.now().plus(ReviewVerification.VALIDADE)));

        // O envio acontece depois do commit (plano DT-05): dentro da transacao, uma
        // falha de SMTP desfaria a review que ja estava boa - e o AC-07 exige o
        // contrario.
        eventos.publishEvent(new ConfirmacaoSolicitada(
                review.getId(), email, idioma, review.getAuthorName(), produto.name(), produto.slug(), token));
    }

    /**
     * O dono do e-mail clicou no link (AC-03).
     *
     * <p>Token usado, expirado ou desconhecido respondem os tres a mesma coisa:
     * 410. Ver {@link LinkNaoValeMaisException}.
     */
    @Transactional
    public void confirmar(String token) {
        ReviewVerification verificacao = verificacoes
                .findByTokenHash(TokenDeConfirmacao.hashDe(token))
                .orElseThrow(LinkNaoValeMaisException::new);

        Instant agora = Instant.now();
        if (verificacao.estaUsado() || verificacao.expirouEm(agora)) {
            throw new LinkNaoValeMaisException();
        }

        Review review = reviews.findById(verificacao.getReviewId()).orElseThrow(LinkNaoValeMaisException::new);

        verificacao.marcarUsado(agora);
        // O selo se decide aqui: confirmou e o dominio nao e de provedor gratuito
        // (spec secao 4.5).
        review.confirmar(provedoresGratuitos.ehGratuito(review.getAuthorDomain()));
    }

    /**
     * Manda um link novo (AC-04).
     *
     * <p>Como o POST de criacao, <b>nao devolve nada e nao falha</b>: produto
     * desconhecido, e-mail que nunca avaliou e review ja confirmada terminam todos
     * em silencio. A resposta uniforme e o que impede a rota de virar sonda de
     * "este e-mail avaliou este produto?".
     */
    @Transactional
    public void reenviar(String slugDoProduto, String email) {
        String normalizado = email.trim().toLowerCase(Locale.ROOT);

        var produto = catalogo.identificarPublicado(slugDoProduto);
        if (produto.isEmpty()) {
            return;
        }

        Review review = reviews.findByProductIdAndAuthorEmail(produto.get().id(), normalizado)
                .orElse(null);
        if (review == null || review.getStatus() != ReviewStatus.PENDING_EMAIL) {
            return;
        }

        String token = TokenDeConfirmacao.novo();
        Instant expiraEm = Instant.now().plus(ReviewVerification.VALIDADE);

        // Substitui o token anterior em vez de criar um segundo (plano DT-04): dois
        // links validos ao mesmo tempo dobram a janela de ataque sem ajudar ninguem.
        ReviewVerification verificacao = verificacoes
                .findById(review.getId())
                .orElseGet(() -> new ReviewVerification(review.getId(), TokenDeConfirmacao.hashDe(token), expiraEm));
        verificacao.substituirToken(TokenDeConfirmacao.hashDe(token), expiraEm);
        verificacoes.save(verificacao);

        eventos.publishEvent(new ConfirmacaoSolicitada(
                review.getId(),
                normalizado,
                AppLocale.porCodigo(review.getLocale()).orElse(AppLocale.PT_BR),
                review.getAuthorName(),
                produto.get().name(),
                produto.get().slug(),
                token));
    }

    /**
     * Apaga o que nunca foi confirmado e ja passou dos 30 dias (spec secao 4.2).
     *
     * <p>Devolve quantas foram apagadas para o agendador registrar no log - e para
     * o teste poder afirmar o numero em vez de inferir do estado.
     */
    @Transactional
    public int expurgarNaoConfirmadas() {
        Instant limite = Instant.now().minus(RETENCAO_SEM_CONFIRMACAO);
        int apagadas = reviews.expurgarPorEstadoAnterioresA(ReviewStatus.PENDING_EMAIL, limite);

        if (apagadas > 0) {
            log.info("expurgo removeu {} reviews sem confirmacao anteriores a {}", apagadas, limite);
        }
        return apagadas;
    }

    /**
     * As reviews publicadas de um produto, com o agregado (AC-11 a AC-13a).
     *
     * <p>Duas consultas, independentemente de quantas reviews a pagina traga: a
     * pagina e o agregado. O agregado nao sai da pagina - ele e de todas as
     * publicadas, e nao das 20 que couberam na tela.
     */
    @Transactional(readOnly = true)
    public PaginaDeReviews publicadasDe(String slugDoProduto, PagingParams paginacao) {
        UUID produtoId = catalogo.identificarPublicado(slugDoProduto)
                .orElseThrow(() -> new NotFoundException("problema.produto.nao-encontrado", slugDoProduto))
                .id();

        Page<Review> pagina = reviews.findByProductIdAndStatusOrderByCreatedAtDesc(
                produtoId, ReviewStatus.PUBLISHED, PageRequest.of(paginacao.page(), paginacao.size()));

        // Produto sem review publicada nao aparece no mapa, e a nota sai null - e nao
        // zero (spec secao 4.4).
        Map<UUID, NotaDoProduto> notas = agregados.notasDe(List.of(produtoId));

        return new PaginaDeReviews(notas.get(produtoId), pagina.getContent(), pagina.getTotalElements());
    }
}
