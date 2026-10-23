package br.com.saasfinder.reviews.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * O token que vai no link do e-mail, e o hash que fica no banco (plano DT-04).
 *
 * <p>32 bytes de {@link SecureRandom} em Base64url sem padding: sobrevive a
 * qualquer query string sem escape e nao e adivinhavel. No banco fica <b>so o
 * SHA-256</b> - quem le o banco nao confirma review de terceiro, e quem
 * interceptasse o e-mail ja teria o link de qualquer jeito.
 *
 * <p>SHA-256 puro, sem salt e sem KDF, e a escolha certa aqui: o token e aleatorio
 * de 256 bits, entao nao ha o que quebrar por forca bruta ou dicionario - o que um
 * bcrypt da vida protege e senha escolhida por gente.
 */
public final class TokenDeConfirmacao {

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final int BYTES = 32;

    private TokenDeConfirmacao() {}

    public static String novo() {
        byte[] bytes = new byte[BYTES];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 em hexadecimal minusculo - o formato que o CHECK da V7 exige. */
    public static String hashDe(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossivel) {
            // SHA-256 e obrigatorio em toda JVM desde sempre.
            throw new IllegalStateException("SHA-256 indisponivel nesta JVM", impossivel);
        }
    }
}
