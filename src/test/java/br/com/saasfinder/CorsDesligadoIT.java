package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.saasfinder.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * Criterio de verificacao da tarefa 002-T08, metade "vazio por padrao".
 *
 * <p>Este e o comportamento de producao: a renderizacao do frontend acontece no
 * servidor (spec 002 secao 6), entao nenhuma pagina chama esta API pelo
 * navegador e nao ha origem a liberar. Liberar `*` como padrao transformaria
 * esquecimento de configuracao em porta aberta.
 */
class CorsDesligadoIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    @DisplayName("sem CORS_ALLOWED_ORIGINS o preflight e recusado")
    void preflightRecusado() {
        ResponseEntity<String> resposta = http.exchange(
                "/api/v1/categories", HttpMethod.OPTIONS, CorsFixture.preflight("http://localhost:3000"), String.class);

        assertThat(resposta.getHeaders().getAccessControlAllowOrigin()).isNull();
        assertThat(resposta.getStatusCode().is2xxSuccessful()).isFalse();
    }
}
