package br.com.saasfinder;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

/** Monta o preflight que os dois ITs de CORS usam. */
final class CorsFixture {

    private CorsFixture() {}

    static HttpEntity<Void> preflight(String origem) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setOrigin(origem);
        cabecalhos.setAccessControlRequestMethod(HttpMethod.GET);
        return new HttpEntity<>(cabecalhos);
    }
}
