package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.saasfinder.shared.locale.RequestLocale;
import br.com.saasfinder.support.AbstractIntegrationTest;
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
import org.springframework.web.bind.annotation.RestController;

/** Criterio de verificacao da tarefa 001-T11: AC-08 e AC-09. */
@Import(LocaleResolutionIT.EcoDeIdioma.class)
class LocaleResolutionIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate http;

    /** Devolve o idioma que o filtro resolveu, para o teste enxergar a decisao. */
    @TestConfiguration
    @RestController
    static class EcoDeIdioma {

        @GetMapping("/teste-idioma")
        String eco() {
            return RequestLocale.atual().codigo();
        }
    }

    private ResponseEntity<String> chamar(String caminho, String acceptLanguage) {
        HttpHeaders cabecalhos = new HttpHeaders();
        if (acceptLanguage != null) {
            cabecalhos.set(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage);
        }
        return http.exchange(caminho, HttpMethod.GET, new HttpEntity<>(cabecalhos), String.class);
    }

    @Test
    @DisplayName("AC-08: header e query levam ao mesmo resultado, e a query vence o header")
    void queryVenceHeader() {
        assertThat(chamar("/teste-idioma?lang=en", null).getBody()).isEqualTo("en");
        assertThat(chamar("/teste-idioma", "en").getBody()).isEqualTo("en");

        assertThat(chamar("/teste-idioma?lang=pt-BR", "en").getBody()).isEqualTo("pt-BR");
        assertThat(chamar("/teste-idioma?lang=en", "pt-BR").getBody()).isEqualTo("en");
    }

    @Test
    @DisplayName("AC-09: lang invalido responde 400 em problem+json")
    void langInvalido() {
        ResponseEntity<String> resposta = chamar("/teste-idioma?lang=fr", null);

        assertThat(resposta.getStatusCode().value()).isEqualTo(400);

        // Compatibilidade, e nao igualdade: esta resposta e escrita pelo filtro, e o
        // container anexa charset=UTF-8 ao Content-Type. As respostas que passam pelos
        // conversores do Spring saem sem o charset. O tipo e o mesmo nos dois casos;
        // o charset e detalhe de transporte.
        assertThat(resposta.getHeaders().getContentType())
                .isNotNull()
                .matches(tipo -> tipo.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(resposta.getBody()).contains("/problems/unsupported-locale").contains("fr");
    }

    @Test
    @DisplayName("D-03: header so com idioma nao suportado cai no padrao, sem erro")
    void headerNaoSuportadoCaiNoPadrao() {
        ResponseEntity<String> resposta = chamar("/teste-idioma", "fr-FR,fr;q=0.9");

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(resposta.getBody()).isEqualTo("pt-BR");
    }

    @Test
    @DisplayName("os pesos q sao respeitados, e nao a ordem de escrita")
    void pesosQSaoRespeitados() {
        assertThat(chamar("/teste-idioma", "fr-FR,pt-BR;q=0.3,en;q=0.9").getBody())
                .isEqualTo("en");
        assertThat(chamar("/teste-idioma", "fr-FR,pt-BR;q=0.9,en;q=0.3").getBody())
                .isEqualTo("pt-BR");
    }

    @Test
    @DisplayName("variante regional casa pela lingua: en-US e pt-PT sao aceitos")
    void varianteRegional() {
        assertThat(chamar("/teste-idioma", "en-US").getBody()).isEqualTo("en");
        assertThat(chamar("/teste-idioma", "pt-PT").getBody()).isEqualTo("pt-BR");
    }

    @Test
    @DisplayName("header malformado nao derruba a requisicao")
    void headerMalformado() {
        ResponseEntity<String> resposta = chamar("/teste-idioma", "isto nao e um header valido ;;;");

        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(resposta.getBody()).isEqualTo("pt-BR");
    }

    @Test
    @DisplayName("sem nada informado, vale o padrao")
    void semNadaInformado() {
        assertThat(chamar("/teste-idioma", null).getBody()).isEqualTo("pt-BR");
    }

    @Test
    @DisplayName("toda resposta traz Content-Language, inclusive 404 e erro de idioma")
    void contentLanguageSempre() {
        assertThat(chamar("/teste-idioma?lang=en", null).getHeaders().getFirst(HttpHeaders.CONTENT_LANGUAGE))
                .isEqualTo("en");
        assertThat(chamar("/api/v1/rota-inexistente", "en").getHeaders().getFirst(HttpHeaders.CONTENT_LANGUAGE))
                .isEqualTo("en");
        assertThat(chamar("/teste-idioma?lang=fr", "en").getHeaders().getFirst(HttpHeaders.CONTENT_LANGUAGE))
                .isEqualTo("en");
    }

    @Test
    @DisplayName("o corpo do erro sai no idioma resolvido")
    void erroSaiNoIdiomaResolvido() {
        assertThat(chamar("/api/v1/rota-inexistente?lang=en", null).getBody()).contains("Resource not found");
        assertThat(chamar("/api/v1/rota-inexistente?lang=pt-BR", null).getBody())
                .contains("Recurso nao encontrado");
    }
}
