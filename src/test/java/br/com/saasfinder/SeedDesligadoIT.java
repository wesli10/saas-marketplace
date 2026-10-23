package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.saasfinder.catalog.infra.CategoryRepository;
import br.com.saasfinder.catalog.infra.ProductRepository;
import br.com.saasfinder.catalog.infra.seed.SeedRunner;
import br.com.saasfinder.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Criterio de verificacao da tarefa 001-T25: sem SEED_ENABLED, o boot nao
 * escreve nada.
 *
 * <p>Usa o contexto padrao da suite, onde a flag esta desligada - que e o
 * cenario de qualquer ambiente que nao pediu seed explicitamente.
 */
class SeedDesligadoIT extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private CategoryRepository categorias;

    @Autowired
    private ProductRepository produtos;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("sem a flag, o SeedRunner nem entra no contexto")
    void semRunner() {
        assertThat(contexto.getBeanNamesForType(SeedRunner.class)).isEmpty();
    }

    @Test
    @DisplayName("sem a flag, o banco continua como estava")
    void nadaEscrito() {
        jdbc.execute("truncate table category, integration, tag cascade");

        assertThat(categorias.count()).isZero();
        assertThat(produtos.count()).isZero();
    }
}
