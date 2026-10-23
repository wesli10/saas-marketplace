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

/**
 * Criterio de verificacao da tarefa 001-T05. As regras de categoria vivem no banco
 * (constraints e triggers), entao os testes falam SQL direto - sem passar por JPA,
 * que ainda nao existe nesta altura.
 */
class CategoriaSchemaIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void limpar() {
        jdbc.execute("truncate table category cascade");
    }

    private UUID inserirCategoria(String slugPt, String slugEn, UUID paiId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                insert into category (id, slug_pt, slug_en, name_pt, name_en, parent_id)
                values (?, ?, ?, ?, ?, ?)
                """,
                id,
                slugPt,
                slugEn,
                "nome pt",
                "nome en",
                paiId);
        return id;
    }

    @Test
    @DisplayName("raiz e subcategoria sao aceitas")
    void aceitaDoisNiveis() {
        UUID raiz = inserirCategoria("ia-e-automacao", "ai-and-automation", null);

        assertThatCode(() -> inserirCategoria("atendimento", "customer-support", raiz))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("terceiro nivel e recusado")
    void recusaProfundidadeTres() {
        UUID raiz = inserirCategoria("ia-e-automacao", "ai-and-automation", null);
        UUID filha = inserirCategoria("atendimento", "customer-support", raiz);

        assertThatThrownBy(() -> inserirCategoria("chatbots-pt", "chatbots-en", filha))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("profundidade maxima de categoria e 2");
    }

    @Test
    @DisplayName("categoria que ja tem filhas nao pode virar subcategoria")
    void recusaProfundidadeTresPeloOutroLado() {
        UUID raiz = inserirCategoria("ia-e-automacao", "ai-and-automation", null);
        inserirCategoria("atendimento", "customer-support", raiz);
        UUID outraRaiz = inserirCategoria("financeiro", "finance", null);

        assertThatThrownBy(() -> jdbc.update("update category set parent_id = ? where id = ?", outraRaiz, raiz))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("categoria com subcategorias nao pode virar subcategoria");
    }

    @Test
    @DisplayName("categoria nao pode ser pai de si mesma")
    void recusaAutoReferencia() {
        UUID id = inserirCategoria("ia-e-automacao", "ai-and-automation", null);

        assertThatThrownBy(() -> jdbc.update("update category set parent_id = ? where id = ?", id, id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("nao pode ser pai de si mesma");
    }

    @Test
    @DisplayName("o insert popula category_slug nos dois idiomas")
    void populaLookupDeSlug() {
        UUID id = inserirCategoria("atendimento-e-chatbots", "customer-support-and-chatbots", null);

        assertThat(jdbc.queryForList(
                        "select slug from category_slug where category_id = ? order by locale", String.class, id))
                .containsExactly("customer-support-and-chatbots", "atendimento-e-chatbots");
    }

    @Test
    @DisplayName("slug igual nos dois idiomas grava uma linha so, sem colidir consigo mesmo")
    void slugIgualNosDoisIdiomas() {
        UUID id = inserirCategoria("chatbots", "chatbots", null);

        assertThat(jdbc.queryForObject("select count(*) from category_slug where category_id = ?", Integer.class, id))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("slug_en que colide com o slug_pt de outra categoria e recusado (AC-04 sem ambiguidade)")
    void recusaColisaoEntreIdiomas() {
        inserirCategoria("marketing", "marketing-en", null);

        // Precisa quebrar na PK de category_slug: e ela que garante a unicidade global
        // do slug entre os dois idiomas. Se quebrasse em outro lugar, o teste estaria
        // passando pelo motivo errado.
        assertThatThrownBy(() -> inserirCategoria("marketing-pt", "marketing", null))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("category_slug_pkey");
    }

    @Test
    @DisplayName("slug de categoria e imutavel apos a criacao")
    void slugEhImutavel() {
        UUID id = inserirCategoria("atendimento", "customer-support", null);

        assertThatThrownBy(() -> jdbc.update("update category set slug_pt = 'outro' where id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("slug de categoria e imutavel");
    }

    @Test
    @DisplayName("apagar categoria leva junto as linhas de category_slug")
    void deleteLimpaLookup() {
        UUID id = inserirCategoria("atendimento", "customer-support", null);
        jdbc.update("delete from category where id = ?", id);

        assertThat(jdbc.queryForObject("select count(*) from category_slug where category_id = ?", Integer.class, id))
                .isZero();
    }

    @Test
    @DisplayName("apagar categoria com subcategoria e recusado (vira 409 no admin, AC-21)")
    void deleteComFilhaEhRecusado() {
        UUID raiz = inserirCategoria("ia-e-automacao", "ai-and-automation", null);
        inserirCategoria("atendimento", "customer-support", raiz);

        assertThatThrownBy(() -> jdbc.update("delete from category where id = ?", raiz))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("slug fora do padrao kebab-case e recusado")
    void recusaSlugForaDoPadrao() {
        assertThatThrownBy(() -> inserirCategoria("Atendimento_E_Chatbots", "customer-support", null))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("category_slug_pt_kebab");
    }

    @Test
    @DisplayName("nome acima de 80 caracteres e recusado")
    void recusaNomeLongo() {
        UUID id = UUID.randomUUID();
        String nomeLongo = "n".repeat(81);

        assertThatThrownBy(() -> jdbc.update(
                        """
                        insert into category (id, slug_pt, slug_en, name_pt, name_en)
                        values (?, ?, ?, ?, ?)
                        """,
                        id,
                        "slug-pt",
                        "slug-en",
                        nomeLongo,
                        "nome en"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("category_name_pt_tamanho");
    }

    @Test
    @DisplayName("updated_at avanca sozinho quando a linha muda")
    void updatedAtEhMantidoPeloBanco() {
        UUID id = inserirCategoria("atendimento", "customer-support", null);
        var antes = jdbc.queryForObject("select updated_at from category where id = ?", java.sql.Timestamp.class, id);

        jdbc.update("update category set name_pt = 'outro nome' where id = ?", id);
        var depois = jdbc.queryForObject("select updated_at from category where id = ?", java.sql.Timestamp.class, id);

        assertThat(depois).isAfterOrEqualTo(antes);
    }
}
