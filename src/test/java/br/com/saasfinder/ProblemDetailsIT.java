package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.saasfinder.shared.error.ApiFieldError;
import br.com.saasfinder.shared.error.ConflictException;
import br.com.saasfinder.shared.error.NotFoundException;
import br.com.saasfinder.shared.error.NotPublishableException;
import br.com.saasfinder.shared.error.UnsupportedLocaleException;
import br.com.saasfinder.support.AbstractIntegrationTest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Criterio de verificacao da tarefa 001-T10: todo erro sai em RFC 9457. */
@Import(ProblemDetailsIT.RotasDeTeste.class)
class ProblemDetailsIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate http;

    /**
     * Rotas que existem so nos testes, para exercitar cada ramo do handler sem
     * depender de endpoints reais - que ainda nao existem nesta altura.
     */
    @TestConfiguration
    @RestController
    @RequestMapping("/teste-erros")
    static class RotasDeTeste {

        record CorpoDeTeste(@NotBlank String taglineEn) {}

        @GetMapping("/nao-encontrado")
        void naoEncontrado() {
            throw new NotFoundException("problema.not-found.detalhe");
        }

        @GetMapping("/conflito")
        void conflito() {
            throw new ConflictException("problema.conflict.detalhe");
        }

        @GetMapping("/idioma")
        void idioma() {
            throw new UnsupportedLocaleException("fr");
        }

        @GetMapping("/nao-publicavel")
        void naoPublicavel() {
            throw new NotPublishableException(
                    List.of(new ApiFieldError("product_content.en", "Missing", "conteudo em ingles ausente")));
        }

        @GetMapping("/explode")
        void explode() {
            throw new IllegalStateException("detalhe interno que nao pode vazar");
        }

        @PostMapping("/corpo")
        void corpo(@Valid @RequestBody CorpoDeTeste corpo) {
            // sem efeito: interessa so a validacao
        }
    }

    private ResponseEntity<String> get(String caminho, String idioma) {
        HttpHeaders cabecalhos = new HttpHeaders();
        if (idioma != null) {
            cabecalhos.set(HttpHeaders.ACCEPT_LANGUAGE, idioma);
        }
        return http.exchange(caminho, HttpMethod.GET, new HttpEntity<>(cabecalhos), String.class);
    }

    @Test
    @DisplayName("rota inexistente responde 404 em problem+json com os campos da RFC")
    void rotaInexistente() {
        ResponseEntity<String> resposta = get("/api/v1/rota-inexistente", null);

        assertThat(resposta.getStatusCode().value()).isEqualTo(404);
        assertThat(resposta.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(resposta.getBody())
                .contains("\"type\":\"https://saasfinder.com.br/problems/not-found\"")
                .contains("\"title\":")
                .contains("\"status\":404")
                .contains("\"instance\":\"/api/v1/rota-inexistente\"");
    }

    @Test
    @DisplayName("excecoes de dominio viram o type e o status certos")
    void excecoesDeDominio() {
        assertThat(get("/teste-erros/nao-encontrado", null).getStatusCode().value())
                .isEqualTo(404);
        assertThat(get("/teste-erros/conflito", null).getBody()).contains("/problems/conflict");
        assertThat(get("/teste-erros/conflito", null).getStatusCode().value()).isEqualTo(409);
        assertThat(get("/teste-erros/idioma", null).getStatusCode().value()).isEqualTo(400);
        assertThat(get("/teste-erros/nao-publicavel", null).getStatusCode().value())
                .isEqualTo(422);
    }

    @Test
    @DisplayName("o titulo sai no idioma pedido")
    void tituloTraduzido() {
        assertThat(get("/teste-erros/nao-encontrado", "en").getBody()).contains("Resource not found");
        assertThat(get("/teste-erros/nao-encontrado", "pt-BR").getBody()).contains("Recurso nao encontrado");
    }

    @Test
    @DisplayName("o detalhe aceita argumento: o idioma recusado aparece na mensagem")
    void detalheComArgumento() {
        assertThat(get("/teste-erros/idioma", "en").getBody()).contains("The language \\\"fr\\\" is not supported");
    }

    @Test
    @DisplayName("pendencias de publicacao saem em errors[]")
    void pendenciasEmErrors() {
        assertThat(get("/teste-erros/nao-publicavel", null).getBody())
                .contains("\"errors\":[")
                .contains("\"field\":\"product_content.en\"")
                .contains("\"code\":\"Missing\"");
    }

    @Test
    @DisplayName("corpo invalido responde 400 com o campo em errors[] (mecanismo do AC-18)")
    void corpoInvalido() {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resposta =
                http.postForEntity("/teste-erros/corpo", new HttpEntity<>("{}", cabecalhos), String.class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(400);
        // O campo sai como o cliente o escreveu, em snake_case (D-07), e nao com o
        // nome da propriedade Java: corrigido no 001-T21, quando o admin passou a
        // devolver errors[] de verdade.
        assertThat(resposta.getBody()).contains("/problems/validation-failed").contains("\"field\":\"tagline_en\"");
    }

    @Test
    @DisplayName("erro inesperado nao vaza detalhe interno")
    void erroInesperadoNaoVaza() {
        ResponseEntity<String> resposta = get("/teste-erros/explode", null);

        assertThat(resposta.getStatusCode().value()).isEqualTo(500);
        assertThat(resposta.getBody())
                .contains("/problems/internal-error")
                .doesNotContain("detalhe interno que nao pode vazar")
                .doesNotContain("IllegalStateException");
    }
}
