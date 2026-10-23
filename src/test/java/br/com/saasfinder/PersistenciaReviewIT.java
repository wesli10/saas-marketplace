package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.saasfinder.catalog.domain.Category;
import br.com.saasfinder.catalog.domain.PricingModel;
import br.com.saasfinder.catalog.domain.Product;
import br.com.saasfinder.catalog.infra.CategoryRepository;
import br.com.saasfinder.catalog.infra.ProductRepository;
import br.com.saasfinder.reviews.domain.Review;
import br.com.saasfinder.reviews.domain.ReviewStatus;
import br.com.saasfinder.reviews.domain.ReviewVerification;
import br.com.saasfinder.reviews.infra.ReviewRepository;
import br.com.saasfinder.reviews.infra.ReviewVerificationRepository;
import br.com.saasfinder.support.AbstractIntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Criterio de verificacao da tarefa 003-T01: a V7 no banco de verdade.
 *
 * <p>Como no 001, o simples fato desta classe subir ja e um teste: com
 * {@code ddl-auto=validate}, qualquer divergencia entre as entidades novas e a
 * migracao derruba o contexto antes do primeiro assert.
 */
class PersistenciaReviewIT extends AbstractIntegrationTest {

    private static final String CORPO =
            "Usamos ha oito meses no time de suporte e o tempo de primeira resposta caiu pela metade.";

    @Autowired
    private CategoryRepository categorias;

    @Autowired
    private ProductRepository produtos;

    @Autowired
    private ReviewRepository reviews;

    @Autowired
    private ReviewVerificationRepository verificacoes;

    @Autowired
    private JdbcTemplate jdbc;

    private Product acmeChat;
    private Product acmeCrm;

    @BeforeEach
    void limpar() {
        // O cascade leva product junto, e product leva review: a V7 declara
        // on delete cascade a partir do produto.
        jdbc.execute("truncate table category cascade");
        Category categoria = categorias.save(
                new Category("atendimento", "customer-support", "Atendimento", "Customer support", null));
        acmeChat = produtos.save(produto("acme-chat", categoria));
        acmeCrm = produtos.save(produto("acme-crm", categoria));
    }

    private Product produto(String slug, Category categoria) {
        return new Product(
                slug,
                "Acme",
                categoria,
                "atendimento no whatsapp",
                "whatsapp support",
                "descricao em portugues",
                "description in english",
                "https://acme.com",
                PricingModel.SUBSCRIPTION,
                "https://acme.com/pricing - 2026-08-27");
    }

    private Review review(UUID produtoId, String email) {
        return new Review(produtoId, 4, true, "Resolveu o atendimento", CORPO, "pt-BR", "Ana Ribeiro", email);
    }

    @Test
    @DisplayName("review vai e volta do banco com os carimbos vindos de la")
    void roundTrip() {
        Review salva = reviews.saveAndFlush(review(acmeChat.getId(), "ana@acme.com.br"));

        Review relida = reviews.findById(salva.getId()).orElseThrow();
        assertThat(relida.getProductId()).isEqualTo(acmeChat.getId());
        assertThat(relida.getRating()).isEqualTo((short) 4);
        assertThat(relida.isWouldRecommend()).isTrue();
        assertThat(relida.getLocale()).isEqualTo("pt-BR");
        assertThat(relida.getAuthorDomain()).isEqualTo("acme.com.br");
        assertThat(relida.getStatus()).isEqualTo(ReviewStatus.PENDING_EMAIL);
        assertThat(relida.isVerified()).isFalse();
        assertThat(relida.getCreatedAt()).isNotNull();
        assertThat(relida.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("o indice unico barra a segunda review do mesmo e-mail no mesmo produto (AC-05)")
    void umaReviewPorProdutoEEmail() {
        reviews.saveAndFlush(review(acmeChat.getId(), "ana@acme.com.br"));

        assertThatThrownBy(() -> reviews.saveAndFlush(review(acmeChat.getId(), "ana@acme.com.br")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a unicidade ignora maiusculas: ANA@ACME e o mesmo e-mail que ana@acme")
    void unicidadeNaoDependeDeCaixa() {
        reviews.saveAndFlush(review(acmeChat.getId(), "ana@acme.com.br"));

        assertThatThrownBy(() -> reviews.saveAndFlush(review(acmeChat.getId(), "ANA@Acme.COM.BR")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("o mesmo e-mail avalia produtos diferentes")
    void mesmoEmailEmProdutosDiferentes() {
        reviews.saveAndFlush(review(acmeChat.getId(), "ana@acme.com.br"));
        reviews.saveAndFlush(review(acmeCrm.getId(), "ana@acme.com.br"));

        assertThat(reviews.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("as transicoes atravessam o banco: confirmada e publicada passam pelos CHECKs da V7")
    void transicoesPersistem() {
        Review review = reviews.saveAndFlush(review(acmeChat.getId(), "ana@acme.com.br"));

        review.confirmar(false);
        reviews.saveAndFlush(review);
        assertThat(reviews.findById(review.getId()).orElseThrow().getConfirmedAt())
                .isNotNull();

        review.publicar("curador-teste");
        reviews.saveAndFlush(review);

        Review publicada = reviews.findById(review.getId()).orElseThrow();
        assertThat(publicada.getStatus()).isEqualTo(ReviewStatus.PUBLISHED);
        assertThat(publicada.getPublishedAt()).isNotNull();
        assertThat(publicada.getModeratedBy()).isEqualTo("curador-teste");
        assertThat(publicada.isVerified()).isTrue();
    }

    @Test
    @DisplayName("o banco recusa selo sem confirmacao, mesmo que a aplicacao erre")
    void seloExigeConfirmacao() {
        Review review = reviews.saveAndFlush(review(acmeChat.getId(), "ana@acme.com.br"));

        assertThatThrownBy(() -> jdbc.update("update review set verified = true where id = ?", review.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a verificacao guarda o hash, e dois tokens iguais nao coexistem")
    void tokenHashEUnico() {
        Review primeira = reviews.saveAndFlush(review(acmeChat.getId(), "ana@acme.com.br"));
        Review segunda = reviews.saveAndFlush(review(acmeCrm.getId(), "ana@acme.com.br"));

        String hash = "a".repeat(64);
        Instant expira = Instant.now().plus(48, ChronoUnit.HOURS);

        verificacoes.saveAndFlush(new ReviewVerification(primeira.getId(), hash, expira));

        assertThatThrownBy(() -> verificacoes.saveAndFlush(new ReviewVerification(segunda.getId(), hash, expira)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("o banco so aceita SHA-256 em hexadecimal no token_hash")
    void tokenHashPrecisaSerSha256() {
        Review review = reviews.saveAndFlush(review(acmeChat.getId(), "ana@acme.com.br"));

        assertThatThrownBy(() -> verificacoes.saveAndFlush(new ReviewVerification(
                        review.getId(), "token-em-claro", Instant.now().plus(48, ChronoUnit.HOURS))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
