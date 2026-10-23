package br.com.saasfinder.shared.id;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import java.util.UUID;

/**
 * Gera identificadores UUID v7 (constituicao secao 5).
 *
 * <p>A v7 carrega o timestamp nos bits mais significativos, entao ids gerados em
 * sequencia sao crescentes. Isso mantem a insercao no fim do indice em vez de
 * espalhada, ao contrario da v4.
 *
 * <p>O id e atribuido pela aplicacao antes do persist, e nao por
 * {@code @GeneratedValue}: assim a entidade ja nasce com identidade e nao e
 * preciso um SELECT de retorno.
 */
public final class IdGenerator {

    private static final TimeBasedEpochGenerator GERADOR = Generators.timeBasedEpochGenerator();

    private IdGenerator() {}

    public static UUID novo() {
        return GERADOR.generate();
    }
}
