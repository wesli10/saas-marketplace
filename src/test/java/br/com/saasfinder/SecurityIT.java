package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.saasfinder.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Criterio de verificacao da tarefa 001-T12: AC-17. */
@Import(SecurityIT.RotaDeAdminDeTeste.class)
class SecurityIT extends AbstractIntegrationTest {

    private static final String USUARIO = "curador-teste";
    private static final String SENHA = "senha-de-teste";

    @Autowired
    private TestRestTemplate http;

    /** As rotas de admin de verdade chegam no T21; aqui basta uma para exercitar a regra. */
    @TestConfiguration
    @RestController
    @RequestMapping("/api/v1/admin")
    static class RotaDeAdminDeTeste {

        @GetMapping("/ping")
        String ping() {
            return "pong";
        }

        @PostMapping("/ping")
        String pingPost() {
            return "pong";
        }
    }

    @Test
    @DisplayName("AC-17: rota de admin sem credencial responde 401 em problem+json")
    void semCredencial() {
        ResponseEntity<String> resposta = http.getForEntity("/api/v1/admin/ping", String.class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(401);
        assertThat(resposta.getHeaders().getContentType())
                .isNotNull()
                .matches(tipo -> tipo.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(resposta.getBody()).contains("/problems/unauthorized").contains("\"status\":401");
    }

    @Test
    @DisplayName("AC-17: credencial errada tambem responde 401")
    void credencialErrada() {
        assertThat(http.withBasicAuth(USUARIO, "senha-errada")
                        .getForEntity("/api/v1/admin/ping", String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(401);

        assertThat(http.withBasicAuth("outro-usuario", SENHA)
                        .getForEntity("/api/v1/admin/ping", String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("com a credencial certa, passa")
    void credencialCerta() {
        ResponseEntity<String> resposta =
                http.withBasicAuth(USUARIO, SENHA).getForEntity("/api/v1/admin/ping", String.class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(resposta.getBody()).isEqualTo("pong");
    }

    @Test
    @DisplayName("rota publica continua aberta")
    void rotaPublicaSegueAberta() {
        assertThat(http.getForEntity("/actuator/health", String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(200);
        assertThat(http.getForEntity("/api/v1/rota-inexistente", String.class)
                        .getStatusCode()
                        .value())
                .isEqualTo(404);
    }

    @Test
    @DisplayName("o 401 nao pede caixa de login do navegador")
    void semDesafioDoNavegador() {
        ResponseEntity<String> resposta = http.getForEntity("/api/v1/admin/ping", String.class);

        // WWW-Authenticate faria o navegador abrir o dialogo de usuario e senha, o que
        // nao faz sentido numa API consumida por outro programa.
        assertThat(resposta.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isNull();
    }

    @Test
    @DisplayName("nenhuma sessao e criada: a API e stateless")
    void semSessao() {
        ResponseEntity<String> resposta =
                http.withBasicAuth(USUARIO, SENHA).getForEntity("/api/v1/admin/ping", String.class);

        assertThat(resposta.getHeaders().get(HttpHeaders.SET_COOKIE)).isNullOrEmpty();
    }

    @Test
    @DisplayName("POST autenticado passa: CSRF desligado nao bloqueia a curadoria")
    void postAutenticadoPassa() {
        ResponseEntity<String> resposta =
                http.withBasicAuth(USUARIO, SENHA).postForEntity("/api/v1/admin/ping", null, String.class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("o 401 sai no idioma pedido")
    void mensagemTraduzida() {
        assertThat(http.getForEntity("/api/v1/admin/ping?lang=en", String.class).getBody())
                .contains("Unauthorized");
        assertThat(http.getForEntity("/api/v1/admin/ping?lang=pt-BR", String.class)
                        .getBody())
                .contains("Nao autorizado");
    }
}
