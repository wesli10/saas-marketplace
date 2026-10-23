package br.com.saasfinder.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base dos testes de integracao: Postgres de verdade, nunca H2 (constituicao secao 7).
 *
 * <p>O container e estatico e sobe uma vez so por JVM. Como o Spring reaproveita o
 * contexto entre classes que compartilham a mesma configuracao, a suite inteira
 * roda contra a mesma instancia - subir um Postgres por classe custaria minutos.
 *
 * <p>A imagem e a mesma do docker-compose (pgvector/pgvector:pg16), para o teste
 * exercitar exatamente o banco que roda em producao, com as extensoes disponiveis.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // A aplicacao nao sobe sem credencial de curadoria (001-T12), de proposito:
        // assim nao existe senha padrao esquecida em producao. Nos testes, o valor
        // vem daqui.
        properties = {
            "ADMIN_USER=curador-teste",
            "ADMIN_PASSWORD=senha-de-teste",
            // O limite de producao e 1 por minuto (spec 003, Q-01), e varios testes
            // desta suite mandam dois POSTs no mesmo teste de proposito - o segundo
            // envio do AC-05, por exemplo. Com o limite real aqui, eles receberiam
            // 429 e testariam o limitador em vez do que se propoem a testar.
            //
            // Quem exercita os limites de verdade sao o LimitadorDeTaxaTest, com
            // relogio proprio, e as duas classes ReviewRateLimit*IT, que sobem um
            // contexto proprio com os valores de producao.
            "RATE_LIMIT_POR_MINUTO=1000",
            "RATE_LIMIT_POR_HORA=10000",
            // O Spring guarda em cache um contexto por combinacao de propriedades e de
            // beans substituidos, e nenhum deles e fechado antes do fim da suite. Com o
            // pool padrao de 10 conexoes por contexto, a fatia 003 chegou ao limite de
            // 100 conexoes do Postgres e o boot de um IT falhou com "sorry, too many
            // clients already" - uma quebra por capacidade, que aparece no teste que
            // tiver a ma sorte de rodar por ultimo.
            //
            // Quatro bastam: a suite roda sequencial, e o maximo simultaneo e a conexao
            // do teste mais a da requisicao que ele dispara.
            "spring.datasource.hikari.maximum-pool-size=4"
        })
public abstract class AbstractIntegrationTest {

    private static final DockerImageName IMAGEM =
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres");

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(IMAGEM)
            .withDatabaseName("saasfinder")
            .withUsername("saasfinder")
            // Valor distintivo de proposito: o LogRedactionIT afirma que ele nao aparece
            // em nenhuma linha de log, e uma senha como "teste" poderia casar por acaso.
            .withPassword("db-9f2a5c30-nao-pode-vazar");

    static {
        POSTGRES.start();
    }
}
