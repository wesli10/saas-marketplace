package br.com.saasfinder.catalog.domain;

import br.com.saasfinder.catalog.infra.CategoryRepository;
import br.com.saasfinder.catalog.infra.IntegrationRepository;
import br.com.saasfinder.catalog.infra.ProductContentRepository;
import br.com.saasfinder.catalog.infra.ProductRepository;
import br.com.saasfinder.reviews.domain.NotaDoProduto;
import br.com.saasfinder.reviews.domain.ReviewFacade;
import br.com.saasfinder.search.domain.CategoriaResumo;
import br.com.saasfinder.search.domain.IntegracaoResumo;
import br.com.saasfinder.search.domain.ProductSummaryView;
import br.com.saasfinder.shared.error.InvalidParameterException;
import br.com.saasfinder.shared.error.NotFoundException;
import br.com.saasfinder.shared.locale.AppLocale;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Porta de entrada do modulo catalog (constituicao secao 4). Nenhum outro modulo
 * fala com os repositorios daqui.
 */
@Service
public class CatalogFacade {

    private final CategoryRepository categorias;
    private final ProductRepository produtos;
    private final ProductContentRepository conteudos;
    private final IntegrationRepository integracoes;
    private final ReviewFacade reviews;

    public CatalogFacade(
            CategoryRepository categorias,
            ProductRepository produtos,
            ProductContentRepository conteudos,
            IntegrationRepository integracoes,
            ReviewFacade reviews) {
        this.categorias = categorias;
        this.produtos = produtos;
        this.conteudos = conteudos;
        this.integracoes = integracoes;
        this.reviews = reviews;
    }

    /**
     * Anexa a nota a uma pagina de itens de lista (spec 003, AC-14).
     *
     * <p><b>Uma consulta para a pagina inteira</b>, alimentada pelos ids que a busca
     * ja leu. E aqui que o N+1 seria facil e invisivel: com um `notaDe(produto)`
     * dentro do `map`, uma listagem de 20 produtos faria 20 idas ao banco e ninguem
     * notaria ate a lista crescer.
     */
    public List<ProductSummaryView> comNotas(List<ProductSummaryView> itens) {
        if (itens.isEmpty()) {
            return itens;
        }

        Map<UUID, NotaDoProduto> notas =
                reviews.notasDe(itens.stream().map(ProductSummaryView::id).toList());

        return itens.stream().map(item -> item.comNota(notas.get(item.id()))).toList();
    }

    /** Integracoes do catalogo, com quantos produtos publicados usam cada uma. */
    @Transactional(readOnly = true)
    public List<IntegrationView> integracoes() {
        return integracoes.listarComContagemDePublicados();
    }

    /**
     * Quem e o produto publicado deste slug, se houver (plano 003, DT-01).
     *
     * <p>E a porta por onde o modulo {@code reviews} pergunta ao {@code catalog}:
     * ele precisa do id para gravar a review e do nome para escrever o e-mail.
     * Devolve {@link Optional} em vez de lancar, porque quem chama e que sabe se
     * slug desconhecido ali e 404 ou outra coisa.
     */
    @Transactional(readOnly = true)
    public Optional<ProdutoIdentificado> identificarPublicado(String slug) {
        return produtos.buscarPublicadoPorSlug(slug)
                .map(produto -> new ProdutoIdentificado(produto.getId(), produto.getSlug(), produto.getName()));
    }

    /**
     * Os produtos destes ids, chaveados por id (plano 003, DT-01).
     *
     * <p>Uma consulta para a pagina inteira, e nao uma por linha: a fila de
     * moderacao lista reviews de produtos variados, e perguntar de um em um seria o
     * N+1 que o AC-14 proibe do outro lado da mesma fronteira.
     *
     * <p>Traz publicados e nao publicados: uma review nao some da fila porque o
     * curador despublicou o produto no meio do caminho.
     */
    @Transactional(readOnly = true)
    public Map<UUID, ProdutoIdentificado> identificarPorIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return produtos.findAllById(ids).stream()
                .collect(Collectors.toMap(
                        Product::getId,
                        produto -> new ProdutoIdentificado(produto.getId(), produto.getSlug(), produto.getName())));
    }

    /**
     * Ficha completa de um produto publicado (AC-06, AC-07).
     *
     * <p>Sao duas consultas: o agregado do produto e a linha de conteudo longo do
     * idioma pedido. product_content nao e mapeado como colecao de Product porque
     * a ficha quer um idioma so - trazer os dois para descartar um seria trabalho
     * jogado fora em toda requisicao.
     */
    @Transactional(readOnly = true)
    public ProductDetailView produtoPublicadoPorSlug(String slug, AppLocale idioma) {
        Product produto = produtos.buscarPublicadoPorSlug(slug)
                .orElseThrow(() -> new NotFoundException("problema.produto.nao-encontrado", slug));

        // Produto sem conteudo curado naquele idioma responde com os campos longos
        // vazios, e nao com 500: a ficha curta ainda e util, e a lacuna e do dado,
        // nao da requisicao.
        ProductContent conteudo = conteudos
                .findById(new ProductContentId(produto.getId(), idioma.codigo()))
                .orElse(null);

        return paraFicha(produto, conteudo, idioma).comNota(notaDe(produto.getId()));
    }

    /**
     * As 2 ou 3 fichas da comparacao, na ordem em que os slugs foram pedidos (AC-16).
     *
     * <p>Slug ausente derruba a resposta inteira, em vez de sair da lista
     * silenciosamente: uma comparacao com menos produtos do que o cliente pediu
     * seria uma resposta errada disfarcada de certa. Quais faltaram vai na extensao
     * `missing_slugs` do problema, para o cliente nao ter de interpretar o texto.
     */
    @Transactional(readOnly = true)
    public List<ProductDetailView> produtosPublicadosPorSlugs(List<String> slugs, AppLocale idioma) {
        Map<String, Product> encontrados = produtos.buscarPublicadosPorSlugs(slugs).stream()
                .collect(Collectors.toMap(Product::getSlug, produto -> produto, (a, b) -> a, LinkedHashMap::new));

        // DRAFT e ARCHIVED nao chegam aqui - a consulta so traz publicados -, entao
        // caem naturalmente como ausentes, que e o que a spec pede.
        List<String> ausentes =
                slugs.stream().filter(slug -> !encontrados.containsKey(slug)).toList();
        if (!ausentes.isEmpty()) {
            throw new NotFoundException("problema.produto.slugs-nao-encontrados", String.join(", ", ausentes))
                    .comExtensao("missing_slugs", ausentes);
        }

        // Um select para o conteudo dos tres, e nao um por produto.
        Map<UUID, ProductContent> porProduto = conteudos
                .findAllById(encontrados.values().stream()
                        .map(produto -> new ProductContentId(produto.getId(), idioma.codigo()))
                        .toList())
                .stream()
                .collect(Collectors.toMap(conteudo -> conteudo.getId().getProductId(), conteudo -> conteudo));

        // Uma consulta para as tres fichas, e nao uma por produto.
        Map<UUID, NotaDoProduto> notas = reviews.notasDe(
                encontrados.values().stream().map(Product::getId).toList());

        return slugs.stream()
                .map(encontrados::get)
                .map(produto -> paraFicha(produto, porProduto.get(produto.getId()), idioma)
                        .comNota(notas.get(produto.getId())))
                .toList();
    }

    private NotaDoProduto notaDe(UUID produto) {
        return reviews.notasDe(List.of(produto)).get(produto);
    }

    /** Ficha resolvida no idioma pedido. `conteudo` nulo = sem curadoria naquele idioma. */
    private ProductDetailView paraFicha(Product produto, ProductContent conteudo, AppLocale idioma) {
        boolean portugues = idioma == AppLocale.PT_BR;
        Category categoria = produto.getCategory();

        return new ProductDetailView(
                produto.getSlug(),
                idioma.codigo(),
                produto.getName(),
                portugues ? produto.getTaglinePt() : produto.getTaglineEn(),
                portugues ? produto.getDescriptionPt() : produto.getDescriptionEn(),
                produto.getLogoUrl(),
                produto.getWebsiteUrl(),
                new CategoriaResumo(
                        portugues ? categoria.getSlugPt() : categoria.getSlugEn(),
                        portugues ? categoria.getNamePt() : categoria.getNameEn()),
                produto.getPricingModel(),
                produto.getPriceMin(),
                produto.getPriceCurrency(),
                produto.getPricePeriod(),
                produto.isHasFreeTrial(),
                produto.isHasFreePlan(),
                produto.getSupportedLanguages(),
                produto.getHqCountry(),
                // Ordem alfabetica porque Set nao tem ordem: sem isto a mesma ficha
                // sairia em ordens diferentes entre requisicoes.
                produto.getIntegrations().stream()
                        .map(integracao -> new IntegracaoResumo(integracao.getSlug(), integracao.getName()))
                        .sorted(Comparator.comparing(IntegracaoResumo::name))
                        .toList(),
                produto.getTags().stream()
                        .map(tag -> new TagResumo(tag.getSlug(), portugues ? tag.getNamePt() : tag.getNameEn()))
                        .sorted(Comparator.comparing(TagResumo::name))
                        .toList(),
                conteudo == null ? List.of() : conteudo.getPros(),
                conteudo == null ? List.of() : conteudo.getCons(),
                conteudo == null ? null : conteudo.getBestFor(),
                conteudo == null ? List.of() : conteudo.getFaq(),
                produto.getPublishedAt(),
                produto.getUpdatedAt(),
                null);
    }

    /** Arvore completa: raizes com suas subcategorias, ordenadas por sort_order. */
    @Transactional(readOnly = true)
    public List<CategoryNodeView> arvoreDeCategorias(AppLocale idioma) {
        return montar(idioma).raizes();
    }

    /** Uma categoria e suas filhas. O slug pode estar em qualquer um dos dois idiomas (AC-04). */
    @Transactional(readOnly = true)
    public CategoryDetailView categoriaPorSlug(String slug, AppLocale idioma) {
        UUID id = categorias
                .buscarIdPorSlugEmQualquerIdioma(slug)
                .orElseThrow(() -> new NotFoundException("problema.categoria.nao-encontrada", slug));

        CategoryNodeView no = montar(idioma).porId().get(id);
        if (no == null) {
            // So aconteceria com category_slug apontando para categoria inexistente,
            // o que a FK impede. Ainda assim, 404 e melhor que NullPointerException.
            throw new NotFoundException("problema.categoria.nao-encontrada", slug);
        }

        // O par de slugs sai da propria categoria: e projecao, nao consulta nova.
        Category categoria = categorias
                .findById(id)
                .orElseThrow(() -> new NotFoundException("problema.categoria.nao-encontrada", slug));

        return CategoryDetailView.de(no, categoria.getSlugPt(), categoria.getSlugEn());
    }

    /**
     * Ids da categoria e das subcategorias dela, para o filtro `category=` (AC-13).
     *
     * <p>Slug desconhecido vira 400, e nao lista vazia: erro de digitacao no filtro
     * deve aparecer como tal, e nao virar "nenhum produto encontrado".
     */
    @Transactional(readOnly = true)
    public Set<UUID> idsDaCategoriaEDescendentes(String slug) {
        UUID id = categorias
                .buscarIdPorSlugEmQualquerIdioma(slug)
                .orElseThrow(() -> new InvalidParameterException("problema.invalid-parameter.detalhe", "category"));

        Set<UUID> ids = new LinkedHashSet<>();
        ids.add(id);
        categorias.findAll().stream()
                .filter(categoria -> categoria.getParent() != null
                        && categoria.getParent().getId().equals(id))
                .forEach(filha -> ids.add(filha.getId()));
        return ids;
    }

    /**
     * Monta a arvore inteira em duas consultas: as categorias e a contagem de
     * publicados agrupada por categoria.
     *
     * <p>A busca por slug tambem passa por aqui, em vez de ter caminho proprio. O
     * catalogo desta fatia tem uma raiz e quatro subcategorias (spec secao 6),
     * entao carregar tudo custa menos do que manter duas logicas de contagem que
     * podem divergir.
     */
    private Arvore montar(AppLocale idioma) {
        List<Category> todas = categorias.findAll();

        Map<UUID, Long> diretos = new HashMap<>();
        for (Object[] linha : produtos.contarPublicadosPorCategoria()) {
            diretos.put((UUID) linha[0], (Long) linha[1]);
        }

        Map<UUID, List<Category>> filhasPorPai = new HashMap<>();
        List<Category> raizes = new ArrayList<>();
        for (Category categoria : todas) {
            if (categoria.getParent() == null) {
                raizes.add(categoria);
            } else {
                filhasPorPai
                        .computeIfAbsent(categoria.getParent().getId(), chave -> new ArrayList<>())
                        .add(categoria);
            }
        }

        Map<UUID, CategoryNodeView> porId = new LinkedHashMap<>();
        List<CategoryNodeView> resultado = new ArrayList<>();

        for (Category raiz : ordenadas(raizes)) {
            List<CategoryNodeView> filhas = new ArrayList<>();
            long totalDasFilhas = 0;

            for (Category filha : ordenadas(filhasPorPai.getOrDefault(raiz.getId(), List.of()))) {
                long contagem = diretos.getOrDefault(filha.getId(), 0L);
                CategoryNodeView noDaFilha = paraView(filha, idioma, contagem, List.of());
                filhas.add(noDaFilha);
                porId.put(filha.getId(), noDaFilha);
                totalDasFilhas += contagem;
            }

            // D-02: a contagem da raiz soma as subcategorias, para bater com o filtro
            // category=, que tambem inclui as filhas. A profundidade maxima e 2
            // (trigger no banco), entao um nivel de soma basta.
            long contagemDaRaiz = diretos.getOrDefault(raiz.getId(), 0L) + totalDasFilhas;
            CategoryNodeView noDaRaiz = paraView(raiz, idioma, contagemDaRaiz, filhas);
            resultado.add(noDaRaiz);
            porId.put(raiz.getId(), noDaRaiz);
        }

        return new Arvore(resultado, porId);
    }

    private List<Category> ordenadas(List<Category> aOrdenar) {
        return aOrdenar.stream()
                .sorted(Comparator.comparingInt(Category::getSortOrder).thenComparing(Category::getSlugPt))
                .toList();
    }

    private CategoryNodeView paraView(
            Category categoria, AppLocale idioma, long contagem, List<CategoryNodeView> filhas) {
        boolean portugues = idioma == AppLocale.PT_BR;
        return new CategoryNodeView(
                portugues ? categoria.getSlugPt() : categoria.getSlugEn(),
                portugues ? categoria.getNamePt() : categoria.getNameEn(),
                portugues ? categoria.getDescriptionPt() : categoria.getDescriptionEn(),
                categoria.getSortOrder(),
                contagem,
                filhas);
    }

    private record Arvore(List<CategoryNodeView> raizes, Map<UUID, CategoryNodeView> porId) {}
}
