package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.saasfinder.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Criterio de verificacao da tarefa 001-T28 (AC-24): o boot nao vaza segredo e escreve JSON.
 *
 * <p>O boot roda num processo separado de proposito. So assim as senhas entram como variaveis de
 * ambiente de verdade, e a captura do stdout fica imune a ordem em que o failsafe executa os outros
 * ITs - o Spring Boot configura o Logback uma unica vez por JVM e os contextos de @SpringBootTest
 * ficam em cache, abertos ate o fim da suite.
 *
 * <p>Estende a base so para reaproveitar o Postgres do Testcontainers: desde o 001-T04 a aplicacao
 * nao sobe sem banco, e um boot que falha nao prova nada sobre o que ele loga.
 */
class LogRedactionIT extends AbstractIntegrationTest {

    private static final String ADMIN_PASSWORD = "adm-4bd8c1e7-nao-pode-vazar";

    private static final String MARCA_DE_BOOT_PRONTO = "Started SaasFinderApplication";

    private static final ObjectMapper JSON = new ObjectMapper();

    private static String saidaDoBoot;

    @BeforeAll
    static void bootaAplicacaoCapturandoASaida() throws Exception {
        Path stdout = Files.createTempFile("boot-stdout", ".log");
        Path stderr = Files.createTempFile("boot-stderr", ".log");

        ProcessBuilder processo = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp",
                System.getProperty("java.class.path"),
                SaasFinderApplication.class.getName(),
                // Porta zero para nao brigar com a aplicacao dos outros ITs. A app sobe
                // inteira, com web e security: `web-application-type=none` faria o boot
                // falhar por falta de HttpSecurity, e boot que falha nao prova nada.
                "--server.port=0",
                // Sem banner o stdout passa a ser exatamente o que o Logback escreveu.
                "--spring.main.banner-mode=off");
        // A app nao sobe sem credencial de curadoria (001-T12), entao as duas vao aqui.
        processo.environment().put("ADMIN_USER", "curador-do-teste-de-log");
        processo.environment().put("ADMIN_PASSWORD", ADMIN_PASSWORD);
        processo.environment().put("DB_URL", POSTGRES.getJdbcUrl());
        processo.environment().put("DB_USER", POSTGRES.getUsername());
        processo.environment().put("DB_PASSWORD", POSTGRES.getPassword());
        processo.redirectOutput(stdout.toFile());
        processo.redirectError(stderr.toFile());

        Process boot = processo.start();
        try {
            saidaDoBoot = esperarOBootTerminar(boot, stdout);
        } finally {
            boot.destroy();
            if (!boot.waitFor(20, TimeUnit.SECONDS)) {
                boot.destroyForcibly();
            }
        }

        String erros = Files.readString(stderr, StandardCharsets.UTF_8);
        Files.deleteIfExists(stdout);
        Files.deleteIfExists(stderr);

        // Esta e a guarda contra o teste passar de graca: um assert de "nao contem"
        // e sempre verdadeiro quando a captura veio vazia.
        assertThat(saidaDoBoot)
                .as("o boot precisa chegar ao ar.%nstderr:%n%s%nstdout:%n%s", erros, saidaDoBoot)
                .contains(MARCA_DE_BOOT_PRONTO);
    }

    /**
     * Le a saida ate a aplicacao anunciar que subiu, e corta na quebra de linha
     * seguinte: assim o que sobra sao linhas inteiras, sem um JSON pela metade que
     * ainda estava sendo escrito quando o processo foi encerrado.
     */
    private static String esperarOBootTerminar(Process boot, Path stdout) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        while (System.nanoTime() < limite) {
            String parcial = Files.readString(stdout, StandardCharsets.UTF_8);
            int marca = parcial.indexOf(MARCA_DE_BOOT_PRONTO);
            if (marca >= 0) {
                int quebra = parcial.indexOf('\n', marca);
                return quebra < 0 ? parcial : parcial.substring(0, quebra + 1);
            }
            if (!boot.isAlive()) {
                return parcial;
            }
            Thread.sleep(200);
        }
        return Files.readString(stdout, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("nenhum segredo do ambiente aparece na saida do boot")
    void nenhumSegredoNaSaidaDoBoot() {
        assertThat(saidaDoBoot).doesNotContain(ADMIN_PASSWORD).doesNotContain(POSTGRES.getPassword());
    }

    @Test
    @DisplayName("toda linha do log e JSON valido com timestamp, level, logger e message")
    void todaLinhaEJsonEstruturado() {
        var linhas = saidaDoBoot.lines().filter(linha -> !linha.isBlank()).toList();

        assertThat(linhas).isNotEmpty();
        for (String linha : linhas) {
            JsonNode evento = lerJson(linha);
            assertThat(evento.hasNonNull("timestamp"))
                    .as("timestamp em: %s", linha)
                    .isTrue();
            assertThat(evento.hasNonNull("level")).as("level em: %s", linha).isTrue();
            assertThat(evento.hasNonNull("logger")).as("logger em: %s", linha).isTrue();
            assertThat(evento.hasNonNull("message")).as("message em: %s", linha).isTrue();
        }
    }

    private static JsonNode lerJson(String linha) {
        try {
            return JSON.readTree(linha);
        } catch (Exception e) {
            throw new AssertionError("linha de log nao e JSON valido: " + linha, e);
        }
    }
}
