package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.saasfinder.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Criterio de verificacao da tarefa 001-T07: vocabulario de integracoes e tags, e as duas N:N. */
class IntegracaoTagSchemaIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private UUID produto;

    @BeforeEach
    void limpar() {
        jdbc.execute("truncate table category, integration, tag cascade");

        UUID categoria = UUID.randomUUID();
        jdbc.update(
                """
                insert into category (id, slug_pt, slug_en, name_pt, name_en)
                values (?, 'atendimento', 'customer-support', 'Atendimento', 'Customer support')
                """,
                categoria);

        produto = UUID.randomUUID();
        jdbc.update(
                """
                insert into product (
                    id, slug, category_id, name, tagline_pt, tagline_en,
                    description_pt, description_en, website_url, pricing_model, source_note)
                values (?, 'acme-chat', ?, 'Acme', 'tagline pt', 'tagline en', 'descricao pt',
                        'descricao en', 'https://acme.com', 'SUBSCRIPTION', 'https://acme.com/pricing')
                """,
                produto,
                categoria);
    }

    private UUID inserirIntegracao(String slug, String nome) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into integration (id, slug, name) values (?, ?, ?)", id, slug, nome);
        return id;
    }

    private UUID inserirTag(String slug) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into tag (id, slug, name_pt, name_en) values (?, ?, ?, ?)", id, slug, "nome pt", "nome en");
        return id;
    }

    @Test
    @DisplayName("vincular a mesma integracao duas vezes e recusado")
    void vinculoDeIntegracaoNaoDuplica() {
        UUID whatsapp = inserirIntegracao("whatsapp", "WhatsApp");
        jdbc.update("insert into product_integration values (?, ?)", produto, whatsapp);

        assertThatThrownBy(() -> jdbc.update("insert into product_integration values (?, ?)", produto, whatsapp))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("vincular a mesma tag duas vezes e recusado")
    void vinculoDeTagNaoDuplica() {
        UUID chatbot = inserirTag("chatbot");
        jdbc.update("insert into product_tag values (?, ?)", produto, chatbot);

        assertThatThrownBy(() -> jdbc.update("insert into product_tag values (?, ?)", produto, chatbot))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("apagar o produto leva junto os vinculos das duas N:N")
    void vinculosCaemComOProduto() {
        jdbc.update(
                "insert into product_integration values (?, ?)", produto, inserirIntegracao("whatsapp", "WhatsApp"));
        jdbc.update("insert into product_tag values (?, ?)", produto, inserirTag("chatbot"));

        jdbc.update("delete from product where id = ?", produto);

        assertThat(jdbc.queryForObject("select count(*) from product_integration", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from product_tag", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("apagar integracao ainda vinculada e recusado; sem vinculo, apaga")
    void integracaoEmUsoNaoEhApagada() {
        UUID whatsapp = inserirIntegracao("whatsapp", "WhatsApp");
        UUID solta = inserirIntegracao("zapier", "Zapier");
        jdbc.update("insert into product_integration values (?, ?)", produto, whatsapp);

        assertThatThrownBy(() -> jdbc.update("delete from integration where id = ?", whatsapp))
                .isInstanceOf(DataAccessException.class);

        assertThatCode(() -> jdbc.update("delete from integration where id = ?", solta))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("apagar tag ainda vinculada e recusado")
    void tagEmUsoNaoEhApagada() {
        UUID chatbot = inserirTag("chatbot");
        jdbc.update("insert into product_tag values (?, ?)", produto, chatbot);

        assertThatThrownBy(() -> jdbc.update("delete from tag where id = ?", chatbot))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("slug de integracao e de tag sao unicos")
    void slugsSaoUnicos() {
        inserirIntegracao("whatsapp", "WhatsApp");
        inserirTag("chatbot");

        assertThatThrownBy(() -> inserirIntegracao("whatsapp", "Outro"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("integration_slug_uq");

        assertThatThrownBy(() -> inserirTag("chatbot"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("tag_slug_uq");
    }

    @Test
    @DisplayName("slug fora do padrao kebab-case e recusado nos dois vocabularios")
    void slugsSeguemKebabCase() {
        assertThatThrownBy(() -> inserirIntegracao("WhatsApp", "WhatsApp"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("integration_slug_kebab");

        assertThatThrownBy(() -> inserirTag("IA_Generativa"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("tag_slug_kebab");
    }

    @Test
    @DisplayName("vinculo apontando para produto inexistente e recusado")
    void vinculoExigeProdutoExistente() {
        UUID whatsapp = inserirIntegracao("whatsapp", "WhatsApp");

        assertThatThrownBy(
                        () -> jdbc.update("insert into product_integration values (?, ?)", UUID.randomUUID(), whatsapp))
                .isInstanceOf(DataAccessException.class);
    }
}
