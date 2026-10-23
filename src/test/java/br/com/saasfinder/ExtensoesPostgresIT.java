package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.saasfinder.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Criterio de verificacao da tarefa 001-T04: a migracao roda contra um Postgres real
 * e deixa as tres extensoes disponiveis.
 */
class ExtensoesPostgresIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("V1 habilita unaccent, pg_trgm e vector")
    void extensoesHabilitadas() {
        List<String> extensoes = jdbc.queryForList("select extname from pg_extension", String.class);

        assertThat(extensoes).contains("unaccent", "pg_trgm", "vector");
    }

    @Test
    @DisplayName("o Flyway registrou a migracao V1 como aplicada com sucesso")
    void migracaoRegistrada() {
        List<String> versoes = jdbc.queryForList(
                "select version from flyway_schema_history where success = true order by installed_rank", String.class);

        assertThat(versoes).contains("1");
    }

    @Test
    @DisplayName("unaccent funciona de verdade, nao so esta instalada")
    void unaccentFunciona() {
        String semAcento = jdbc.queryForObject("select unaccent('automação')", String.class);

        assertThat(semAcento).isEqualTo("automacao");
    }
}
