package br.com.saasfinder.catalog.infra.seed;

import br.com.saasfinder.catalog.domain.CatalogWriteService;
import br.com.saasfinder.catalog.domain.DadosDeCategoria;
import br.com.saasfinder.catalog.domain.DadosDeConteudo;
import br.com.saasfinder.catalog.domain.DadosDeProduto;
import br.com.saasfinder.catalog.domain.FaqItem;
import br.com.saasfinder.catalog.domain.ProductAdminView;
import br.com.saasfinder.catalog.infra.CategoryRepository;
import br.com.saasfinder.catalog.infra.IntegrationRepository;
import br.com.saasfinder.catalog.infra.ProductRepository;
import br.com.saasfinder.catalog.infra.TagRepository;
import br.com.saasfinder.catalog.infra.seed.ArquivosDeSeed.CategoriaDeSeed;
import br.com.saasfinder.catalog.infra.seed.ArquivosDeSeed.ConteudoDeSeed;
import br.com.saasfinder.catalog.infra.seed.ArquivosDeSeed.IntegracaoDeSeed;
import br.com.saasfinder.catalog.infra.seed.ArquivosDeSeed.ProdutoDeSeed;
import br.com.saasfinder.catalog.infra.seed.ArquivosDeSeed.TagDeSeed;
import br.com.saasfinder.shared.error.ApiException;
import br.com.saasfinder.shared.locale.AppLocale;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Popula o catalogo a partir dos arquivos versionados em `resources/seed`
 * (spec secao 6, plan.md DT-15).
 *
 * <p>So roda com `SEED_ENABLED=true`: encher o catalogo e decisao de quem sobe o
 * ambiente, nunca efeito colateral do boot.
 *
 * <p>Escreve pelo mesmo {@link CatalogWriteService} que o admin usa, e nao por SQL
 * proprio. Assim o seed passa pelas mesmas guardas e pelo mesmo validador de
 * publicacao: um caminho paralelo poderia publicar o que o admin recusaria.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    /**
     * Chave do advisory lock. Valor arbitrario, so precisa ser o mesmo em todas as
     * instancias - e o que impede duas replicas semearem ao mesmo tempo (AC-02).
     */
    private static final long TRAVA_DO_SEED = 8_724_531_001L;

    private final LeitorDeSeed leitor = new LeitorDeSeed();

    private final CatalogWriteService catalogo;
    private final CategoryRepository categorias;
    private final ProductRepository produtos;
    private final IntegrationRepository integracoes;
    private final TagRepository tags;
    private final JdbcTemplate jdbc;

    public SeedRunner(
            CatalogWriteService catalogo,
            CategoryRepository categorias,
            ProductRepository produtos,
            IntegrationRepository integracoes,
            TagRepository tags,
            JdbcTemplate jdbc) {
        this.catalogo = catalogo;
        this.categorias = categorias;
        this.produtos = produtos;
        this.integracoes = integracoes;
        this.tags = tags;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments argumentos) {
        // Duas travas para a mesma promessa (AC-02): o advisory lock protege contra
        // duas instancias subindo juntas, e a contagem protege contra o restart.
        // Sozinha, a contagem teria a janela entre ler e escrever.
        jdbc.queryForObject("select pg_advisory_xact_lock(?)", Object.class, TRAVA_DO_SEED);

        if (!catalogoVazio()) {
            log.info("seed ignorado: o catalogo ja tem dados");
            return;
        }

        Map<String, UUID> categoriasPorSlug = carregarCategorias();
        carregarIntegracoes();
        carregarTags();
        carregarProdutos(categoriasPorSlug);
    }

    /**
     * Vazio quer dizer **tudo** o que o seed escreve, e nao so categoria e produto.
     *
     * <p>Corrigido depois de o CI quebrar: um banco com integracoes gravadas e sem
     * categoria nenhuma passava por vazio, e ai o seed tentava recriar a mesma
     * integracao e derrubava o boot com 409. Meia carga anterior e justamente o
     * caso em que reaplicar o seed nao funciona.
     */
    private boolean catalogoVazio() {
        return categorias.count() == 0 && produtos.count() == 0 && integracoes.count() == 0 && tags.count() == 0;
    }

    private Map<String, UUID> carregarCategorias() {
        List<CategoriaDeSeed> arquivo = leitor.lerLista("classpath:seed/categories.yml", CategoriaDeSeed[].class);

        // Raizes primeiro: uma subcategoria nao pode ser criada antes do pai existir.
        List<CategoriaDeSeed> emOrdem = new ArrayList<>(arquivo);
        emOrdem.sort(Comparator.comparing(categoria -> categoria.parent() != null));

        Map<String, UUID> porSlug = new HashMap<>();
        for (CategoriaDeSeed categoria : emOrdem) {
            UUID pai = categoria.parent() == null ? null : porSlug.get(categoria.parent());
            if (categoria.parent() != null && pai == null) {
                throw new SeedInvalidoException(
                        "seed/categories.yml",
                        "a categoria pai \"%s\" nao existe no arquivo".formatted(categoria.parent()));
            }

            var criada = catalogo.criarCategoria(new DadosDeCategoria(
                    categoria.slugPt(),
                    categoria.slugEn(),
                    categoria.namePt(),
                    categoria.nameEn(),
                    categoria.descriptionPt(),
                    categoria.descriptionEn(),
                    pai,
                    categoria.sortOrder()));

            porSlug.put(categoria.slugPt(), criada.getId());
        }
        log.info("seed: {} categorias", porSlug.size());
        return porSlug;
    }

    private void carregarIntegracoes() {
        List<IntegracaoDeSeed> arquivo = leitor.lerLista("classpath:seed/integrations.yml", IntegracaoDeSeed[].class);
        arquivo.forEach(integracao -> catalogo.criarIntegracao(integracao.slug(), integracao.name()));
        log.info("seed: {} integracoes", arquivo.size());
    }

    private void carregarTags() {
        List<TagDeSeed> arquivo = leitor.lerLista("classpath:seed/tags.yml", TagDeSeed[].class);
        arquivo.forEach(tag -> catalogo.criarTag(tag.slug(), tag.namePt(), tag.nameEn()));
        log.info("seed: {} tags", arquivo.size());
    }

    private void carregarProdutos(Map<String, UUID> categoriasPorSlug) {
        Resource[] arquivos = leitor.arquivosDeProduto();

        for (Resource arquivo : arquivos) {
            String nome = "seed/products/" + arquivo.getFilename();
            ProdutoDeSeed produto = leitor.ler(arquivo, nome, ProdutoDeSeed.class);

            UUID categoria = categoriasPorSlug.get(produto.category());
            if (categoria == null) {
                throw new SeedInvalidoException(
                        nome, "a categoria \"%s\" nao existe no categories.yml".formatted(produto.category()));
            }

            ProductAdminView criado = catalogo.criarProduto(new DadosDeProduto(
                    produto.slug(),
                    produto.name(),
                    produto.taglinePt(),
                    produto.taglineEn(),
                    produto.descriptionPt(),
                    produto.descriptionEn(),
                    categoria,
                    produto.websiteUrl(),
                    produto.logoUrl(),
                    produto.pricingModel(),
                    produto.priceMin(),
                    produto.priceCurrency(),
                    produto.pricePeriod(),
                    produto.hasFreeTrial(),
                    produto.hasFreePlan(),
                    produto.supportedLanguages(),
                    produto.hqCountry(),
                    produto.sourceNote(),
                    produto.integrations(),
                    produto.tags()));

            for (AppLocale idioma : AppLocale.values()) {
                ConteudoDeSeed conteudo =
                        produto.content() == null ? null : produto.content().get(idioma.codigo());
                if (conteudo == null) {
                    continue;
                }
                catalogo.substituirConteudo(
                        criado.id(),
                        idioma,
                        new DadosDeConteudo(conteudo.pros(), conteudo.cons(), conteudo.bestFor(), perguntas(conteudo)));
            }

            publicar(criado.id(), nome);
        }
        log.info("seed: {} produtos publicados", arquivos.length);
    }

    private List<FaqItem> perguntas(ConteudoDeSeed conteudo) {
        return conteudo.faq() == null
                ? List.of()
                : conteudo.faq().stream()
                        .map(item -> new FaqItem(item.question(), item.answer()))
                        .toList();
    }

    /**
     * Produto de seed entra publicado (spec secao 6), passando pelo mesmo validador
     * do admin. Pendencia aqui derruba o boot: em dev, que e onde o erro custa
     * menos, e apontando o arquivo que precisa ser corrigido.
     */
    private void publicar(UUID id, String arquivo) {
        try {
            catalogo.publicar(id);
        } catch (ApiException recusado) {
            List<String> pendencias = recusado.getErros().stream()
                    .map(erro -> erro.field() + ": " + erro.message())
                    .toList();
            throw new SeedInvalidoException(arquivo, "nao pode ser publicado - " + String.join("; ", pendencias));
        }
    }
}
