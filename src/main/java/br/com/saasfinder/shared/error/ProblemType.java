package br.com.saasfinder.shared.error;

import java.net.URI;
import org.springframework.http.HttpStatus;

/**
 * Tipo de problema conforme RFC 9457: um identificador estavel (o slug) e o
 * status HTTP correspondente.
 *
 * <p>O {@code type} e uma URI para o cliente reconhecer a classe do erro sem
 * depender do texto, que muda com o idioma.
 */
public record ProblemType(String slug, HttpStatus status) {

    private static final String BASE = "https://saasfinder.com.br/problems/";

    public URI uri() {
        return URI.create(BASE + slug);
    }

    /** Chave da mensagem de titulo no MessageSource. */
    public String chaveTitulo() {
        return "problema." + slug + ".titulo";
    }
}
