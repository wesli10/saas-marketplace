package br.com.saasfinder.catalog.infra.seed;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Le os arquivos YAML de seed.
 *
 * <p>Fica separado do {@link SeedRunner} pelo mesmo motivo que o SearchSqlBuilder
 * fica separado do repositorio: da para testar sem banco, e e aqui que mora o
 * risco de um arquivo mal escrito passar despercebido.
 *
 * <p>Toda falha vira {@link SeedInvalidoException} **nomeando o arquivo** - com
 * uma dezena deles, "erro de parsing" sozinho nao diz o que corrigir.
 */
public class LeitorDeSeed {

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory())
            // Os arquivos sao escritos em snake_case, como o resto da API (D-07).
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    private final PathMatchingResourcePatternResolver recursos = new PathMatchingResourcePatternResolver();

    public <T> List<T> lerLista(String caminho, Class<T[]> tipo) {
        return List.of(ler(recursos.getResource(caminho), caminho.replace("classpath:", ""), tipo));
    }

    /** Um arquivo por produto (plan.md DT-15), em ordem estavel para o log ficar comparavel. */
    public Resource[] arquivosDeProduto() {
        try {
            Resource[] encontrados = recursos.getResources("classpath:seed/products/*.yml");
            Arrays.sort(encontrados, Comparator.comparing(Resource::getFilename));
            return encontrados;
        } catch (IOException falha) {
            throw new SeedInvalidoException("seed/products/", "nao deu para listar os arquivos: " + falha.getMessage());
        }
    }

    public <T> T ler(Resource recurso, String nome, Class<T> tipo) {
        if (!recurso.exists()) {
            throw new SeedInvalidoException(nome, "arquivo nao encontrado");
        }
        try (InputStream entrada = recurso.getInputStream()) {
            // Lido como texto antes de passar ao Jackson so para poder dizer "arquivo
            // vazio" em vez do "No content to map due to end-of-input" dele.
            String conteudo = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
            if (conteudo.isBlank()) {
                throw new SeedInvalidoException(nome, "arquivo vazio");
            }

            T lido = yaml.readValue(conteudo, tipo);
            if (lido == null) {
                // YAML so com comentario tambem nao tem o que aplicar.
                throw new SeedInvalidoException(nome, "arquivo vazio");
            }
            return lido;
        } catch (JacksonException malFormado) {
            // A mensagem do Jackson traz linha e coluna, que e o que quem vai
            // corrigir o arquivo precisa saber.
            throw new SeedInvalidoException(nome, malFormado.getOriginalMessage());
        } catch (IOException ilegivel) {
            throw new SeedInvalidoException(nome, ilegivel.getMessage());
        }
    }
}
