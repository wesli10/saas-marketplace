package br.com.saasfinder.shared.locale;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Os dois idiomas da API (spec secao 5).
 *
 * <p>Carrega tambem o dicionario de full-text search correspondente, para a busca
 * nao precisar de um `if` de idioma espalhado por ai.
 */
public enum AppLocale {
    PT_BR("pt-BR", "portuguese", Locale.of("pt", "BR")),
    EN("en", "english", Locale.ENGLISH);

    private final String codigo;
    private final String dicionarioFts;
    private final Locale locale;

    AppLocale(String codigo, String dicionarioFts, Locale locale) {
        this.codigo = codigo;
        this.dicionarioFts = dicionarioFts;
        this.locale = locale;
    }

    public String codigo() {
        return codigo;
    }

    /** Nome da text search configuration do Postgres para este idioma. */
    public String dicionarioFts() {
        return dicionarioFts;
    }

    public Locale locale() {
        return locale;
    }

    /** Aceita o codigo exato, sem diferenciar maiusculas: "pt-BR", "PT-br", "en". */
    public static Optional<AppLocale> porCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(valor -> valor.codigo.equalsIgnoreCase(codigo.trim()))
                .findFirst();
    }

    /**
     * Casa pela lingua, ignorando a regiao: pt, pt-BR e pt-PT caem em PT_BR;
     * en, en-US e en-GB caem em EN. E o que faz sentido para negociacao por
     * Accept-Language, onde o navegador manda a variante local do usuario.
     */
    public static Optional<AppLocale> porLingua(String tagDeIdioma) {
        if (tagDeIdioma == null || tagDeIdioma.isBlank()) {
            return Optional.empty();
        }
        String lingua = tagDeIdioma.split("-")[0].toLowerCase(Locale.ROOT);
        return switch (lingua) {
            case "pt" -> Optional.of(PT_BR);
            case "en" -> Optional.of(EN);
            default -> Optional.empty();
        };
    }
}
