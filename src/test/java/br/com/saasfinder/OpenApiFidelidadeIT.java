package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.saasfinder.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * O documento OpenAPI descreve o JSON que a API realmente serve.
 *
 * <p>Nasceu de um defeito encontrado pela spec 002: o springdoc montava os
 * schemas a partir dos nomes dos componentes dos records e ignorava a
 * estrategia de nomes do Jackson, entao o documento anunciava
 * {@code productCount} enquanto a API respondia {@code product_count}. Nada
 * falhava - o Swagger abria, os testes passavam - e so aparecia em quem gerava
 * cliente a partir do contrato, que compilava e lia {@code undefined}.
 *
 * <p>Contrato que mente e pior que contrato ausente, e o unico jeito de ele nao
 * mentir e alguem comparar os dois lados a cada build.
 */
class OpenApiFidelidadeIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void prepararCatalogo() {
        jdbc.execute("truncate table category, integration, tag cascade");
        UUID raiz = UUID.randomUUID();
        jdbc.update(
                """
                insert into category (id, slug_pt, slug_en, name_pt, name_en, description_pt, description_en,
                                      parent_id, sort_order)
                values (?, 'raiz', 'root', 'Raiz', 'Root', 'pt', 'en', null, 0)
                """,
                raiz);
        jdbc.update(
                """
                insert into category (id, slug_pt, slug_en, name_pt, name_en, description_pt, description_en,
                                      parent_id, sort_order)
                values (?, 'filha', 'child', 'Filha', 'Child', 'pt', 'en', ?, 0)
                """,
                UUID.randomUUID(),
                raiz);
    }

    @Test
    @DisplayName("todo campo servido pela arvore de categorias esta declarado no schema")
    void schemaDeCategoriaBateComOCorpo() {
        JsonNode corpo = http.getForObject("/api/v1/categories", JsonNode.class);
        JsonNode schema = propriedadesDe("CategoryNodeView");

        JsonNode raiz = corpo.get("items").get(0);
        assertThat(nomesDe(raiz)).allSatisfy(campo -> assertThat(schema.has(campo))
                .as("campo `%s` e servido mas nao esta no schema", campo)
                .isTrue());

        // `children` e o caso que escapou: swagger-core descarta a propriedade
        // recursiva de um record se ela nao for declarada explicitamente.
        assertThat(nomesDe(raiz)).contains("children");
    }

    @Test
    @DisplayName("os nomes no schema sao os mesmos do corpo, e nao os do codigo Java")
    void schemaUsaSnakeCaseComoOCorpo() {
        JsonNode schema = propriedadesDe("CategoryNodeView");

        assertThat(nomesDe(schema)).contains("product_count", "sort_order");
        assertThat(nomesDe(schema)).doesNotContain("productCount", "sortOrder");
    }

    @Test
    @DisplayName("a ficha de produto tambem descreve o que serve")
    void schemaDeProdutoUsaSnakeCase() {
        JsonNode schema = propriedadesDe("ProductDetailView");

        assertThat(nomesDe(schema)).contains("price_min", "has_free_trial", "website_url");
        assertThat(nomesDe(schema)).doesNotContain("priceMin", "hasFreeTrial", "websiteUrl");
    }

    @Test
    @DisplayName("toda resposta de sucesso declara o schema do corpo, e nao so um exemplo")
    void respostasDeSucessoTemSchema() {
        JsonNode documento = http.getForObject("/v3/api-docs", JsonNode.class);
        List<String> semSchema = new ArrayList<>();

        documento.get("paths").properties().forEach(rota -> rota.getValue()
                .properties()
                .forEach(operacao -> {
                    JsonNode respostas = operacao.getValue().path("responses");
                    for (String codigo : List.of("200", "201")) {
                        JsonNode conteudo = respostas.path(codigo).path("content");
                        conteudo.properties().forEach(tipo -> {
                            if (!tipo.getValue().has("schema")) {
                                semSchema.add("%s %s (%s)"
                                        .formatted(operacao.getKey().toUpperCase(), rota.getKey(), tipo.getKey()));
                            }
                        });
                    }
                }));

        // `@Content` escrito a mao substitui o schema que o springdoc deduziria.
        // Foi assim que `GET /products` - o endpoint mais usado - ficou com
        // exemplo e sem tipo, e o cliente gerado recebeu `unknown`.
        assertThat(semSchema).as("resposta de sucesso sem schema no OpenAPI").isEmpty();
    }

    private JsonNode propriedadesDe(String esquema) {
        JsonNode documento = http.getForObject("/v3/api-docs", JsonNode.class);
        return documento.get("components").get("schemas").get(esquema).get("properties");
    }

    private static List<String> nomesDe(JsonNode no) {
        List<String> nomes = new ArrayList<>();
        no.fieldNames().forEachRemaining(nomes::add);
        return nomes;
    }
}
