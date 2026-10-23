package br.com.saasfinder.catalog.infra.seed;

/**
 * Arquivo de seed que nao da para aplicar.
 *
 * <p>Derruba o boot de proposito (plan.md DT-15): seed quebrado que sobe em
 * silencio vira catalogo incompleto em producao, e o erro custa menos aqui.
 * A mensagem sempre nomeia o arquivo - e o unico jeito de saber o que corrigir.
 */
public class SeedInvalidoException extends IllegalStateException {

    public SeedInvalidoException(String arquivo, String motivo) {
        super("seed invalido em %s: %s".formatted(arquivo, motivo));
    }
}
