package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.saasfinder.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Criterio de verificacao da tarefa 001-T08. A consistencia do vetor e
 * responsabilidade do banco (DT-06), entao e no banco que ela e verificada -
 * inclusive AC-11 (busca sem acento) e a base do AC-22 (edicao reflete na busca).
 */
class BuscaVetorSchemaIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private UUID categoria;

    @BeforeEach
    void limpar() {
        jdbc.execute("truncate table category, integration, tag cascade");
        categoria = UUID.randomUUID();
        jdbc.update(
                """
                insert into category (id, slug_pt, slug_en, name_pt, name_en)
                values (?, 'atendimento', 'customer-support', 'Atendimento', 'Customer support')
                """,
                categoria);
    }

    private UUID inserirProduto(String slug, String nome, String taglinePt, String taglineEn, String descPt) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into product (
                    id, slug, category_id, name, tagline_pt, tagline_en,
                    description_pt, description_en, website_url, pricing_model, source_note)
                values (?, ?, ?, ?, ?, ?, ?, 'english description', 'https://acme.com',
                        'SUBSCRIPTION', 'https://acme.com/pricing')
                """,
                id,
                slug,
                categoria,
                nome,
                taglinePt,
                taglineEn,
                descPt);
        return id;
    }

    /** Casa a consulta contra o vetor em portugues, do mesmo jeito que a busca vai fazer. */
    private boolean casaEmPortugues(UUID produto, String consulta) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                """
                select search_vector_pt @@ websearch_to_tsquery('portuguese', f_unaccent(?))
                  from product where id = ?
                """,
                Boolean.class,
                consulta,
                produto));
    }

    private boolean casaEmIngles(UUID produto, String consulta) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                """
                select search_vector_en @@ websearch_to_tsquery('english', f_unaccent(?))
                  from product where id = ?
                """,
                Boolean.class,
                consulta,
                produto));
    }

    @Test
    @DisplayName("o insert ja deixa os dois vetores preenchidos")
    void insertPreencheOsDoisVetores() {
        UUID id = inserirProduto("acme-chat", "Acme Chat", "atendimento no whatsapp", "whatsapp support", "descricao");

        assertThat(casaEmPortugues(id, "atendimento")).isTrue();
        assertThat(casaEmIngles(id, "support")).isTrue();
    }

    @Test
    @DisplayName("a busca ignora acento nos dois sentidos (AC-11)")
    void buscaIgnoraAcento() {
        UUID id = inserirProduto("acme-auto", "Acme", "automação de processos", "automation", "descricao");

        assertThat(casaEmPortugues(id, "automacao")).isTrue();
        assertThat(casaEmPortugues(id, "automação")).isTrue();
    }

    @Test
    @DisplayName("cada vetor usa o dicionario do seu idioma")
    void cadaVetorUsaSeuDicionario() {
        UUID id = inserirProduto("acme-chat", "Acme", "atendimento ao cliente", "customer supports", "descricao");

        // Radical do portugues: "atendimentos" casa com "atendimento".
        assertThat(casaEmPortugues(id, "atendimentos")).isTrue();
        // Radical do ingles: "support" casa com "supports".
        assertThat(casaEmIngles(id, "support")).isTrue();
    }

    @Test
    @DisplayName("nome pesa mais que descricao na ordenacao")
    void nomePesaMaisQueDescricao() {
        UUID noNome = inserirProduto("zeta-chat", "Chatbot", "outra coisa", "other", "sem relacao");
        UUID naDescricao = inserirProduto("acme-chat", "Acme", "outra coisa", "other", "um chatbot qualquer");

        Double rankNome = jdbc.queryForObject(
                "select ts_rank_cd(search_vector_pt, websearch_to_tsquery('portuguese', 'chatbot'), 32) from product where id = ?",
                Double.class,
                noNome);
        Double rankDescricao = jdbc.queryForObject(
                "select ts_rank_cd(search_vector_pt, websearch_to_tsquery('portuguese', 'chatbot'), 32) from product where id = ?",
                Double.class,
                naDescricao);

        assertThat(rankNome).isGreaterThan(rankDescricao);
    }

    @Test
    @DisplayName("vincular tag entra no vetor; desvincular sai")
    void vinculoDeTagRecalculaOVetor() {
        UUID produto = inserirProduto("acme-chat", "Acme", "tagline", "tagline en", "descricao");
        UUID tag = UUID.randomUUID();
        jdbc.update(
                "insert into tag (id, slug, name_pt, name_en) values (?, 'ia-generativa', 'inteligencia artificial', 'artificial intelligence')",
                tag);

        assertThat(casaEmPortugues(produto, "inteligencia")).isFalse();

        jdbc.update("insert into product_tag values (?, ?)", produto, tag);
        assertThat(casaEmPortugues(produto, "inteligencia")).isTrue();

        jdbc.update("delete from product_tag where product_id = ? and tag_id = ?", produto, tag);
        assertThat(casaEmPortugues(produto, "inteligencia")).isFalse();
    }

    @Test
    @DisplayName("vincular integracao entra no vetor")
    void vinculoDeIntegracaoRecalculaOVetor() {
        UUID produto = inserirProduto("acme-chat", "Acme", "tagline", "tagline en", "descricao");
        UUID integracao = UUID.randomUUID();
        jdbc.update("insert into integration (id, slug, name) values (?, 'whatsapp', 'WhatsApp')", integracao);

        assertThat(casaEmPortugues(produto, "whatsapp")).isFalse();

        jdbc.update("insert into product_integration values (?, ?)", produto, integracao);
        assertThat(casaEmPortugues(produto, "whatsapp")).isTrue();
    }

    @Test
    @DisplayName("renomear a tag reflete em todos os produtos ligados (base do AC-22)")
    void renomearTagRecalculaTodosOsProdutos() {
        UUID a = inserirProduto("acme-a", "Acme A", "tagline", "tagline en", "descricao");
        UUID b = inserirProduto("acme-b", "Acme B", "tagline", "tagline en", "descricao");
        UUID tag = UUID.randomUUID();
        jdbc.update("insert into tag (id, slug, name_pt, name_en) values (?, 'chatbot', 'chatbot', 'chatbot')", tag);
        jdbc.update("insert into product_tag values (?, ?)", a, tag);
        jdbc.update("insert into product_tag values (?, ?)", b, tag);

        jdbc.update("update tag set name_pt = 'assistente virtual' where id = ?", tag);

        assertThat(casaEmPortugues(a, "assistente")).isTrue();
        assertThat(casaEmPortugues(b, "assistente")).isTrue();

        // O termo antigo sai do vetor em portugues, mas continua no de ingles: so o
        // name_pt da tag mudou. E o que garante que cada vetor carrega o seu idioma.
        assertThat(casaEmPortugues(a, "chatbot")).isFalse();
        assertThat(casaEmIngles(a, "chatbot")).isTrue();
    }

    @Test
    @DisplayName("renomear a integracao reflete nos produtos ligados")
    void renomearIntegracaoRecalcula() {
        UUID produto = inserirProduto("acme-chat", "Acme", "tagline", "tagline en", "descricao");
        UUID integracao = UUID.randomUUID();
        jdbc.update("insert into integration (id, slug, name) values (?, 'zap', 'Zap')", integracao);
        jdbc.update("insert into product_integration values (?, ?)", produto, integracao);

        jdbc.update("update integration set name = 'WhatsApp Business' where id = ?", integracao);

        assertThat(casaEmPortugues(produto, "whatsapp")).isTrue();
    }

    @Test
    @DisplayName("editar a descricao reflete na busca sem passo de aplicacao (AC-22)")
    void editarDescricaoRecalcula() {
        UUID produto = inserirProduto("acme-chat", "Acme", "tagline", "tagline en", "texto antigo");

        assertThat(casaEmPortugues(produto, "orcamento")).isFalse();

        jdbc.update("update product set description_pt = 'agora fala de orçamento' where id = ?", produto);

        assertThat(casaEmPortugues(produto, "orcamento")).isTrue();
    }

    @Test
    @DisplayName("erro de digitacao de um caractere ainda passa do limiar de similaridade (base do AC-12)")
    void similaridadeToleraErroDeDigitacao() {
        inserirProduto("acme-chat", "Chatbot Maker", "tagline", "tagline en", "descricao");

        Double similaridade = jdbc.queryForObject(
                "select similarity(lower(f_unaccent(name)), lower(f_unaccent(?))) from product where slug = 'acme-chat'",
                Double.class,
                "Chatbot Maler");

        assertThat(similaridade).isGreaterThan(0.3);
    }

    @Test
    @DisplayName("os tres indices de busca existem")
    void indicesDeBuscaExistem() {
        var indices = jdbc.queryForList("select indexname from pg_indexes where tablename = 'product'", String.class);

        assertThat(indices).contains("product_search_pt_idx", "product_search_en_idx", "product_name_trgm_idx");
    }
}
