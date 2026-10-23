package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.saasfinder.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Criterio de verificacao da tarefa 001-T01: a aplicacao sobe e o health responde UP.
 * Roda no failsafe (001-T02) por subir o contexto inteiro e falar HTTP de verdade.
 * Desde 001-T04 herda o Postgres do AbstractIntegrationTest: o contexto agora tem
 * datasource, e o health passa a incluir o check do banco.
 */
class SaasFinderApplicationIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("a aplicacao sobe e /actuator/health responde UP")
    void healthRespondeUp() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("endpoints de ambiente nao sao expostos")
    void actuatorEnvNaoExposto() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/env", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
