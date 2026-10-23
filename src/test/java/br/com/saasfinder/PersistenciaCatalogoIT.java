package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.saasfinder.catalog.domain.Category;
import br.com.saasfinder.catalog.domain.FaqItem;
import br.com.saasfinder.catalog.domain.Integration;
import br.com.saasfinder.catalog.domain.PricePeriod;
import br.com.saasfinder.catalog.domain.PricingModel;
import br.com.saasfinder.catalog.domain.Product;
import br.com.saasfinder.catalog.domain.ProductContent;
import br.com.saasfinder.catalog.domain.ProductContentId;
import br.com.saasfinder.catalog.domain.ProductStatus;
import br.com.saasfinder.catalog.domain.Tag;
import br.com.saasfinder.catalog.infra.CategoryRepository;
import br.com.saasfinder.catalog.infra.IntegrationRepository;
import br.com.saasfinder.catalog.infra.ProductContentRepository;
import br.com.saasfinder.catalog.infra.ProductRepository;
import br.com.saasfinder.catalog.infra.TagRepository;
import br.com.saasfinder.shared.id.IdGenerator;
import br.com.saasfinder.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Criterio de verificacao da tarefa 001-T09.
 *
 * <p>Vale notar que o simples fato desta classe subir ja e um teste: com
 * ddl-auto=validate, qualquer divergencia entre entidade e migracao derruba o
 * contexto antes do primeiro assert.
 */
class PersistenciaCatalogoIT extends AbstractIntegrationTest {

    @Autowired
    private CategoryRepository categorias;

    @Autowired
    private ProductRepository produtos;

    @Autowired
    private ProductContentRepository conteudos;

    @Autowired
    private IntegrationRepository integracoes;

    @Autowired
    private TagRepository tags;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transacoes;

    private Category categoria;

    @BeforeEach
    void limpar() {
        jdbc.execute("truncate table category, integration, tag cascade");
        categoria = categorias.save(
                new Category("atendimento", "customer-support", "Atendimento", "Customer support", null));
    }

    private Product produtoMinimo(String slug) {
        return new Product(
                slug,
                "Acme Chat",
                categoria,
                "atendimento no whatsapp",
                "whatsapp support",
                "descricao em portugues",
                "description in english",
                "https://acme.com",
                PricingModel.SUBSCRIPTION,
                "https://acme.com/pricing - 2026-08-26");
    }

    @Test
    @DisplayName("produto com array e jsonb volta identico do banco")
    void roundTripCompleto() {
        Product produto = produtoMinimo("acme-chat");
        produto.setPriceMin(new BigDecimal("99.00"));
        produto.setPriceCurrency("BRL");
        produto.setPricePeriod(PricePeriod.MONTH);
        produto.setHasFreeTrial(true);
        produto.setSupportedLanguages(List.of("pt", "en", "es"));
        produto.setHqCountry("BR");
        produtos.saveAndFlush(produto);

        ProductContent conteudo = new ProductContent(new ProductContentId(produto.getId(), "pt-BR"));
        conteudo.setPros(List.of("facil de configurar", "suporte em portugues"));
        conteudo.setCons(List.of("sem app mobile"));
        conteudo.setBestFor("pequenas equipes de atendimento");
        conteudo.setFaq(List.of(new FaqItem("Tem teste gratis?", "Sim, 14 dias."), new FaqItem("Tem API?", "Tem.")));
        conteudos.saveAndFlush(conteudo);

        limparCache();

        Product relido = produtos.findById(produto.getId()).orElseThrow();
        assertThat(relido.getSupportedLanguages()).containsExactly("pt", "en", "es");
        assertThat(relido.getPriceMin()).isEqualByComparingTo("99.00");
        assertThat(relido.getPriceCurrency()).isEqualTo("BRL");
        assertThat(relido.getPricePeriod()).isEqualTo(PricePeriod.MONTH);
        assertThat(relido.getHqCountry()).isEqualTo("BR");
        assertThat(relido.isHasFreeTrial()).isTrue();
        assertThat(relido.getStatus()).isEqualTo(ProductStatus.DRAFT);

        ProductContent conteudoRelido = conteudos
                .findById(new ProductContentId(produto.getId(), "pt-BR"))
                .orElseThrow();
        assertThat(conteudoRelido.getPros()).containsExactly("facil de configurar", "suporte em portugues");
        assertThat(conteudoRelido.getCons()).containsExactly("sem app mobile");
        assertThat(conteudoRelido.getBestFor()).isEqualTo("pequenas equipes de atendimento");
        assertThat(conteudoRelido.getFaq())
                .containsExactly(new FaqItem("Tem teste gratis?", "Sim, 14 dias."), new FaqItem("Tem API?", "Tem."));
    }

    @Test
    @DisplayName("vinculos de tag e integracao vao e voltam pelas N:N")
    void vinculosNN() {
        Product produto = produtoMinimo("acme-chat");
        produto.getIntegrations().add(integracoes.save(new Integration("whatsapp", "WhatsApp")));
        produto.getTags().add(tags.save(new Tag("chatbot", "Chatbot", "Chatbot")));
        produtos.saveAndFlush(produto);

        // As colecoes sao LAZY e open-in-view esta desligado, entao a leitura precisa
        // acontecer dentro de uma transacao. Ler fora daqui estouraria
        // LazyInitializationException - que e exatamente o comportamento desejado em
        // producao: nada de sessao aberta pendurada na requisicao.
        transacoes.executeWithoutResult(status -> {
            Product relido = produtos.findById(produto.getId()).orElseThrow();
            assertThat(relido.getIntegrations())
                    .extracting(Integration::getSlug)
                    .containsExactly("whatsapp");
            assertThat(relido.getTags()).extracting(Tag::getSlug).containsExactly("chatbot");
        });
    }

    @Test
    @DisplayName("os triggers do banco continuam agindo sobre o que o JPA grava")
    void triggersValemParaOJpaTambem() {
        Product produto = produtoMinimo("acme-chat");
        produtos.saveAndFlush(produto);

        Boolean casa = jdbc.queryForObject(
                """
                select search_vector_pt @@ websearch_to_tsquery('portuguese', f_unaccent('atendimento'))
                  from product where id = ?
                """,
                Boolean.class,
                produto.getId());

        assertThat(casa).isTrue();
    }

    @Test
    @DisplayName("createdAt e updatedAt vem do banco, nao da aplicacao")
    void carimbosDeTempoVemDoBanco() {
        Product produto = produtos.saveAndFlush(produtoMinimo("acme-chat"));

        assertThat(produto.getCreatedAt()).isNotNull();
        assertThat(produto.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("ids sao UUID v7: versao correta e ordem crescente")
    void idsSaoUuidV7() {
        List<UUID> ids =
                IntStream.range(0, 50).mapToObj(i -> IdGenerator.novo()).toList();

        assertThat(ids).allMatch(id -> id.version() == 7);

        List<String> comoTexto = ids.stream().map(UUID::toString).toList();
        assertThat(comoTexto).isSorted();
    }

    private void limparCache() {
        produtos.flush();
        conteudos.flush();
    }
}
