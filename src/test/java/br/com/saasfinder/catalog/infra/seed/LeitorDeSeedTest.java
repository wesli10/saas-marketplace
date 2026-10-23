package br.com.saasfinder.catalog.infra.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.saasfinder.catalog.domain.PricingModel;
import br.com.saasfinder.catalog.infra.seed.ArquivosDeSeed.CategoriaDeSeed;
import br.com.saasfinder.catalog.infra.seed.ArquivosDeSeed.IntegracaoDeSeed;
import br.com.saasfinder.catalog.infra.seed.ArquivosDeSeed.ProdutoDeSeed;
import br.com.saasfinder.catalog.infra.seed.ArquivosDeSeed.TagDeSeed;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * Teste unitario do leitor de seed: valida os arquivos versionados e as falhas de
 * leitura sem precisar de banco nem de contexto Spring.
 */
class LeitorDeSeedTest {

    private final LeitorDeSeed leitor = new LeitorDeSeed();

    private Resource conteudo(String yaml) {
        return new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("YAML mal formado falha nomeando o arquivo e a posicao")
    void yamlMalFormado() {
        Resource quebrado = conteudo("slug: acme\n  name: [nao fecha\n");

        assertThatThrownBy(() -> leitor.ler(quebrado, "seed/products/acme.yml", ProdutoDeSeed.class))
                .isInstanceOf(SeedInvalidoException.class)
                .hasMessageContaining("seed/products/acme.yml")
                .hasMessageContaining("line");
    }

    @Test
    @DisplayName("arquivo ausente e arquivo vazio tambem dizem qual e")
    void ausenteOuVazio() {
        assertThatThrownBy(() -> leitor.ler(
                        new ClassPathResource("seed/nao-existe.yml"), "seed/nao-existe.yml", ProdutoDeSeed.class))
                .isInstanceOf(SeedInvalidoException.class)
                .hasMessageContaining("seed/nao-existe.yml")
                .hasMessageContaining("nao encontrado");

        assertThatThrownBy(() -> leitor.ler(conteudo(""), "seed/vazio.yml", ProdutoDeSeed.class))
                .isInstanceOf(SeedInvalidoException.class)
                .hasMessageContaining("arquivo vazio");
    }

    @Test
    @DisplayName("campo desconhecido no arquivo falha em vez de ser ignorado")
    void campoDesconhecido() {
        Resource comTypo = conteudo("slug: acme\nnome_do_produto: Acme\n");

        assertThatThrownBy(() -> leitor.ler(comTypo, "seed/products/acme.yml", ProdutoDeSeed.class))
                .isInstanceOf(SeedInvalidoException.class)
                .hasMessageContaining("nome_do_produto");
    }

    @Test
    @DisplayName("os arquivos versionados de vocabulario sao lidos como a spec descreve")
    void arquivosDeVocabulario() {
        List<CategoriaDeSeed> categorias = leitor.lerLista("classpath:seed/categories.yml", CategoriaDeSeed[].class);
        assertThat(categorias).hasSize(5);
        assertThat(categorias)
                .filteredOn(categoria -> categoria.parent() == null)
                .hasSize(1);
        assertThat(categorias)
                .extracting(CategoriaDeSeed::slugPt)
                .contains(
                        "atendimento-e-chatbots",
                        "automacao-de-whatsapp",
                        "geracao-de-conteudo",
                        "agentes-e-workflows");

        assertThat(leitor.lerLista("classpath:seed/integrations.yml", IntegracaoDeSeed[].class))
                .hasSize(12)
                .allMatch(integracao -> integracao.slug() != null && integracao.name() != null);

        assertThat(leitor.lerLista("classpath:seed/tags.yml", TagDeSeed[].class))
                .hasSize(15)
                .allMatch(tag -> tag.namePt() != null && tag.nameEn() != null);
    }

    @Test
    @DisplayName("todo arquivo de produto tem os dois idiomas, source_note e conteudo curado")
    void arquivosDeProduto() {
        Resource[] arquivos = leitor.arquivosDeProduto();
        assertThat(arquivos).isNotEmpty();

        for (Resource arquivo : arquivos) {
            String nome = "seed/products/" + arquivo.getFilename();
            ProdutoDeSeed produto = leitor.ler(arquivo, nome, ProdutoDeSeed.class);

            assertThat(produto.slug()).as("slug em %s", nome).isNotBlank();
            assertThat(produto.taglinePt()).as("tagline_pt em %s", nome).isNotBlank();
            assertThat(produto.taglineEn()).as("tagline_en em %s", nome).isNotBlank();
            assertThat(produto.descriptionPt()).as("description_pt em %s", nome).isNotBlank();
            assertThat(produto.descriptionEn()).as("description_en em %s", nome).isNotBlank();

            // Constituicao secao 7.5: dado de produto sem origem indicada nao entra.
            assertThat(produto.sourceNote()).as("source_note em %s", nome).isNotBlank();

            // O validador de publicacao exige as duas chaves; falhar aqui e mais
            // barato do que descobrir no boot.
            assertThat(produto.content()).as("content em %s", nome).containsKeys("pt-BR", "en");
            assertThat(produto.supportedLanguages())
                    .as("supported_languages em %s", nome)
                    .isNotEmpty();

            if (produto.pricingModel() == PricingModel.SUBSCRIPTION
                    || produto.pricingModel() == PricingModel.USAGE_BASED
                    || produto.pricingModel() == PricingModel.ONE_TIME) {
                assertThat(produto.priceMin()).as("price_min em %s", nome).isNotNull();
            }
        }
    }
}
