package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.saasfinder.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * Criterio de verificacao da tarefa 002-T08, metade "origem listada".
 *
 * <p>Contexto proprio com a variavel preenchida, que e o caso do `next dev`
 * fora do compose - localhost:3000 batendo em localhost:8080.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "ADMIN_USER=curador-teste",
            "ADMIN_PASSWORD=senha-de-teste",
            "CORS_ALLOWED_ORIGINS=http://localhost:3000"
        })
class CorsLigadoIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    @DisplayName("a origem listada passa no preflight")
    void origemListadaPassa() {
        ResponseEntity<String> resposta = http.exchange(
                "/api/v1/categories", HttpMethod.OPTIONS, CorsFixture.preflight("http://localhost:3000"), String.class);

        assertThat(resposta.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resposta.getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://localhost:3000");
    }

    @Test
    @DisplayName("qualquer outra origem continua recusada")
    void outraOrigemRecusada() {
        ResponseEntity<String> resposta = http.exchange(
                "/api/v1/categories",
                HttpMethod.OPTIONS,
                CorsFixture.preflight("http://malicioso.example"),
                String.class);

        assertThat(resposta.getHeaders().getAccessControlAllowOrigin()).isNull();
    }
}
