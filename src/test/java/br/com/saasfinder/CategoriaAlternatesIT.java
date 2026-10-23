package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.saasfinder.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Criterio de verificacao da tarefa 002-T07: AC-25 da spec 002.
 *
 * <p>O campo existe para que o frontend monte a URL da outra versao de idioma -
 * seletor de idioma e hreflang - sem uma segunda chamada por pagina. Vale so
 * para categoria: slug de produto e o mesmo nos dois idiomas (spec 001 secao
 * 4.2), e la o campo seria a mesma string repetida.
 */
class CategoriaAlternatesIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void prepararCatalogo() {
        jdbc.execute("truncate table category, integration, tag cascade");
        inserirCategoria("atendimento-e-chatbots", "customer-support-and-chatbots");
    }

    private void inserirCategoria(String slugPt, String slugEn) {
        jdbc.update(
                """
                insert into category (id, slug_pt, slug_en, name_pt, name_en, description_pt, description_en,
                                      parent_id, sort_order)
                values (?, ?, ?, 'Atendimento', 'Customer support', 'descricao pt', 'description en', null, 0)
                """,
                UUID.randomUUID(),
                slugPt,
                slugEn);
    }

    @Test
    @DisplayName("AC-25: a ficha da categoria traz o slug nos dois idiomas")
    void alternatesNaFicha() {
        String corpo = http.getForEntity("/api/v1/categories/atendimento-e-chatbots", String.class)
                .getBody();

        assertThat((String) JsonPath.read(corpo, "$.slug")).isEqualTo("atendimento-e-chatbots");
        assertThat((String) JsonPath.read(corpo, "$.alternates.['pt-BR']")).isEqualTo("atendimento-e-chatbots");
        assertThat((String) JsonPath.read(corpo, "$.alternates.en")).isEqualTo("customer-support-and-chatbots");
    }

    @Test
    @DisplayName("AC-25: entrar pelo slug em ingles devolve o mesmo par")
    void alternatesIndependemDaPortaDeEntrada() {
        ResponseEntity<String> resposta =
                http.getForEntity("/api/v1/categories/customer-support-and-chatbots?lang=en", String.class);
        String corpo = resposta.getBody();

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        // O `slug` acompanha o idioma pedido; `alternates` e o par inteiro, sempre igual.
        assertThat((String) JsonPath.read(corpo, "$.slug")).isEqualTo("customer-support-and-chatbots");
        assertThat((String) JsonPath.read(corpo, "$.alternates.['pt-BR']")).isEqualTo("atendimento-e-chatbots");
        assertThat((String) JsonPath.read(corpo, "$.alternates.en")).isEqualTo("customer-support-and-chatbots");
    }

    @Test
    @DisplayName("AC-25: a arvore nao ganha o campo")
    void arvoreSegueSemAlternates() {
        String corpo = http.getForEntity("/api/v1/categories", String.class).getBody();

        // Nem preenchido nem nulo: `default-property-inclusion: always` faria um
        // campo opcional aparecer como "alternates": null em cada no da arvore.
        assertThat(corpo).doesNotContain("alternates");
    }
}
