package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.saasfinder.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Criterio de verificacao da tarefa 001-T27 (AC-23).
 *
 * <p>Compara o documento com as rotas realmente registradas, em vez de conferir
 * uma lista escrita a mao: rota nova sem documentacao quebra o build no dia em
 * que e criada, e nao meses depois, quando alguem for usar o Swagger.
 */
class OpenApiIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate http;

    // Qualificado: o actuator registra um segundo RequestMappingHandlerMapping.
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping rotas;

    private JsonNode documento() {
        ResponseEntity<JsonNode> resposta = http.getForEntity("/v3/api-docs", JsonNode.class);
        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        return resposta.getBody();
    }

    /** Rotas da aplicacao, ignorando o que o proprio springdoc e o actuator registram. */
    private Set<String> rotasDaAplicacao() {
        Set<String> encontradas = new LinkedHashSet<>();
        rotas.getHandlerMethods().forEach((info, metodo) -> {
            if (!metodo.getBeanType().getPackageName().startsWith("br.com.saasfinder")) {
                return;
            }
            for (String padrao : caminhosDe(info)) {
                if (padrao.startsWith("/api/v1")) {
                    encontradas.add(padrao);
                }
            }
        });
        return encontradas;
    }

    private List<String> caminhosDe(RequestMappingInfo info) {
        List<String> caminhos = new ArrayList<>();
        if (info.getPathPatternsCondition() != null) {
            info.getPathPatternsCondition().getPatterns().forEach(padrao -> caminhos.add(padrao.getPatternString()));
        }
        return caminhos;
    }

    @Test
    @DisplayName("AC-23: toda rota registrada aparece no documento OpenAPI")
    void nenhumaRotaFicaDeFora() {
        JsonNode caminhosDocumentados = documento().get("paths");

        Set<String> documentados = new LinkedHashSet<>();
        caminhosDocumentados.fieldNames().forEachRemaining(documentados::add);

        assertThat(documentados).containsAll(rotasDaAplicacao());
        // E o inverso: documento que anuncia rota inexistente tambem engana.
        assertThat(rotasDaAplicacao()).containsAll(documentados);
    }

    @Test
    @DisplayName("toda operacao tem resumo e o corpo de erro documentado")
    void toda0peracaoDescrita() {
        JsonNode caminhos = documento().get("paths");

        caminhos.fields()
                .forEachRemaining(caminho -> caminho.getValue().fields().forEachRemaining(operacao -> {
                    String onde = operacao.getKey().toUpperCase() + " " + caminho.getKey();
                    JsonNode detalhe = operacao.getValue();

                    assertThat(detalhe.hasNonNull("summary"))
                            .as("summary em %s", onde)
                            .isTrue();
                    assertThat(detalhe.get("tags")).as("tag em %s", onde).isNotEmpty();
                    assertThat(detalhe.get("responses").has("400"))
                            .as("resposta 400 em %s", onde)
                            .isTrue();
                    assertThat(detalhe.get("responses").has("500"))
                            .as("resposta 500 em %s", onde)
                            .isTrue();
                }));
    }

    @Test
    @DisplayName("as rotas de curadoria exigem o esquema Basic no documento")
    void curadoriaExigeCredencial() {
        JsonNode documento = documento();

        assertThat(documento.get("components").get("securitySchemes").has("curadoria"))
                .isTrue();

        documento.get("paths").fields().forEachRemaining(caminho -> {
            if (!caminho.getKey().startsWith("/api/v1/admin")) {
                return;
            }
            caminho.getValue().fields().forEachRemaining(operacao -> {
                String onde = operacao.getKey().toUpperCase() + " " + caminho.getKey();
                assertThat(operacao.getValue().get("security"))
                        .as("security em %s", onde)
                        .isNotEmpty();
                assertThat(operacao.getValue().get("responses").has("401"))
                        .as("resposta 401 em %s", onde)
                        .isTrue();
            });
        });
    }

    @Test
    @DisplayName("os corpos de escrita trazem exemplo, e nao so o schema")
    void corposComExemplo() {
        JsonNode schemas = documento().get("components").get("schemas");

        for (String corpo :
                List.of("ProductUpsertRequest", "CategoryUpsertRequest", "ProductContentRequest", "TagCreateRequest")) {
            assertThat(schemas.has(corpo)).as("schema %s", corpo).isTrue();
            assertThat(schemas.get(corpo).hasNonNull("example"))
                    .as("exemplo em %s", corpo)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("a Swagger UI responde no caminho que a spec cita")
    void swaggerUiNoAr() {
        assertThat(http.getForEntity("/swagger-ui.html", String.class)
                        .getStatusCode()
                        .value())
                .isIn(200, 302);
        assertThat(http.getForEntity("/swagger-ui/index.html", String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(200);
    }
}
