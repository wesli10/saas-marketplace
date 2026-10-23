package br.com.saasfinder.catalog.domain;

import br.com.saasfinder.catalog.infra.CategoryRepository;
import br.com.saasfinder.catalog.infra.IntegrationRepository;
import br.com.saasfinder.catalog.infra.ProductContentRepository;
import br.com.saasfinder.catalog.infra.ProductRepository;
import br.com.saasfinder.catalog.infra.TagRepository;
import br.com.saasfinder.shared.error.ApiFieldError;
import br.com.saasfinder.shared.error.ConflictException;
import br.com.saasfinder.shared.error.InvalidParameterException;
import br.com.saasfinder.shared.error.NotFoundException;
import br.com.saasfinder.shared.error.NotPublishableException;
import br.com.saasfinder.shared.locale.AppLocale;
import br.com.saasfinder.shared.web.PagingParams;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escrita do catalogo, usada pelas rotas de curadoria (spec secao 5.2).
 *
 * <p>Mora no catalog, e nao no admin, por causa da constituicao secao 4: nenhum
 * modulo acessa repositorio de outro. As invariantes tambem sao do catalogo -
 * profundidade, unicidade de slug e as guardas de exclusao valem para qualquer
 * caminho de escrita, nao so para o admin de hoje.
 *
 * <p>O banco ja barra tudo isto por trigger e constraint (V2). A validacao aqui
 * nao substitui aquela: existe para o cliente receber 409 explicado em vez de um
 * 500 de violacao de constraint.
 */
@Service
public class CatalogWriteService {

    private final CategoryRepository categorias;
    private final ProductRepository produtos;
    private final IntegrationRepository integracoes;
    private final TagRepository tags;
    private final ProductContentRepository conteudos;
    private final ProductPublicationValidator validador;

    public CatalogWriteService(
            CategoryRepository categorias,
            ProductRepository produtos,
            IntegrationRepository integracoes,
            TagRepository tags,
            ProductContentRepository conteudos,
            ProductPublicationValidator validador) {
        this.categorias = categorias;
        this.produtos = produtos;
        this.integracoes = integracoes;
        this.tags = tags;
        this.conteudos = conteudos;
        this.validador = validador;
    }

    // --- Vocabulario: integracoes e tags (spec secao 5.2) ---

    @Transactional(readOnly = true)
    public List<Integration> listarIntegracoes() {
        return integracoes.findAllByOrderByNameAsc();
    }

    /**
     * O slug e a chave que os produtos usam para se vincular, entao repetir um ja
     * existente e conflito, e nao uma segunda integracao com o mesmo apelido.
     */
    @Transactional
    public Integration criarIntegracao(String slug, String nome) {
        integracoes.findBySlug(slug).ifPresent(existente -> {
            throw new ConflictException("problema.integracao.slug-em-uso", slug);
        });
        return integracoes.saveAndFlush(new Integration(slug, nome));
    }

    @Transactional(readOnly = true)
    public List<Tag> listarTags() {
        return tags.findAllByOrderByNamePtAsc();
    }

    @Transactional
    public Tag criarTag(String slug, String nomePt, String nomeEn) {
        tags.findBySlug(slug).ifPresent(existente -> {
            throw new ConflictException("problema.tag.slug-em-uso", slug);
        });
        return tags.saveAndFlush(new Tag(slug, nomePt, nomeEn));
    }

    // --- Produtos (spec secao 5.2) ---

    /**
     * Listagem de curadoria: todos os status por padrao, um deles se pedido.
     *
     * <p>Ordenada por `updated_at desc` - quem cura quer ver o que mexeu por
     * ultimo, nao o que entrou primeiro.
     */
    @Transactional(readOnly = true)
    public PaginaDeProdutos listarProdutos(ProductStatus status, PagingParams paginacao) {
        var pagina = PageRequest.of(
                paginacao.page(),
                paginacao.size(),
                Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by(Sort.Direction.DESC, "id")));

        var ids = status == null ? produtos.paginarIds(pagina) : produtos.paginarIdsPorStatus(status, pagina);
        if (ids.isEmpty()) {
            return new PaginaDeProdutos(List.of(), ids.getTotalElements());
        }

        // A segunda consulta perde a ordem do IN, entao a pagina e remontada na
        // ordem dos ids.
        Map<UUID, Product> porId = new LinkedHashMap<>();
        produtos.buscarComVinculos(ids.getContent()).forEach(produto -> porId.put(produto.getId(), produto));

        List<ProductAdminView> itens = ids.getContent().stream()
                .map(porId::get)
                .map(this::paraVisaoDeCuradoria)
                .toList();

        return new PaginaDeProdutos(itens, ids.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ProductAdminView produtoPorId(UUID id) {
        return paraVisaoDeCuradoria(carregarProduto(id));
    }

    /** Produto nasce sempre em DRAFT (spec secao 5.2): publicar e um passo a parte (T24). */
    @Transactional
    public ProductAdminView criarProduto(DadosDeProduto dados) {
        if (produtos.existsBySlug(dados.slug())) {
            throw new ConflictException("problema.produto.slug-em-uso", dados.slug());
        }

        Product produto = new Product(
                dados.slug(),
                dados.name(),
                categoriaDoProduto(dados.categoryId()),
                dados.taglinePt(),
                dados.taglineEn(),
                dados.descriptionPt(),
                dados.descriptionEn(),
                dados.websiteUrl(),
                dados.pricingModel(),
                dados.sourceNote());

        aplicar(produto, dados);
        return paraVisaoDeCuradoria(produtos.saveAndFlush(produto));
    }

    @Transactional
    public ProductAdminView atualizarProduto(UUID id, DadosDeProduto dados) {
        Product produto = carregarProduto(id);

        // Slug de produto publicado e imutavel (AC-20): o link ja esta na rua e o
        // buscador ja o indexou. Em DRAFT ainda da para corrigir.
        if (!produto.getSlug().equals(dados.slug())) {
            if (produto.getStatus() == ProductStatus.PUBLISHED) {
                throw new ConflictException("problema.produto.slug-imutavel", produto.getSlug());
            }
            if (produtos.existsBySlug(dados.slug())) {
                throw new ConflictException("problema.produto.slug-em-uso", dados.slug());
            }
            produto.setSlug(dados.slug());
        }

        produto.setName(dados.name());
        produto.setCategory(categoriaDoProduto(dados.categoryId()));
        produto.setTaglinePt(dados.taglinePt());
        produto.setTaglineEn(dados.taglineEn());
        produto.setDescriptionPt(dados.descriptionPt());
        produto.setDescriptionEn(dados.descriptionEn());
        produto.setSourceNote(dados.sourceNote());
        aplicar(produto, dados);

        return paraVisaoDeCuradoria(produtos.saveAndFlush(produto));
    }

    private Product carregarProduto(UUID id) {
        return produtos.findById(id).orElseThrow(() -> new NotFoundException("problema.produto.id-nao-encontrado", id));
    }

    private Category categoriaDoProduto(UUID categoryId) {
        return categorias
                .findById(categoryId)
                .orElseThrow(
                        () -> new InvalidParameterException("problema.produto.categoria-desconhecida", categoryId));
    }

    /** Campos opcionais e vinculos, comuns a criacao e atualizacao. */
    private void aplicar(Product produto, DadosDeProduto dados) {
        produto.setWebsiteUrl(dados.websiteUrl());
        produto.setLogoUrl(dados.logoUrl());
        produto.setPricingModel(dados.pricingModel());
        produto.setPriceMin(dados.priceMin());
        produto.setPriceCurrency(dados.priceCurrency());
        produto.setPricePeriod(dados.pricePeriod());
        produto.setHasFreeTrial(Boolean.TRUE.equals(dados.hasFreeTrial()));
        produto.setHasFreePlan(Boolean.TRUE.equals(dados.hasFreePlan()));
        produto.setSupportedLanguages(dados.supportedLanguages() == null ? List.of() : dados.supportedLanguages());
        produto.setHqCountry(dados.hqCountry());

        // Substituicao, nao acumulo: o corpo do PUT descreve o produto inteiro,
        // entao vinculo que sumiu do corpo sumiu do produto.
        produto.getIntegrations().clear();
        for (String slug : dados.integrationSlugs() == null ? List.<String>of() : dados.integrationSlugs()) {
            produto.getIntegrations()
                    .add(integracoes
                            .findBySlug(slug)
                            .orElseThrow(() ->
                                    new InvalidParameterException("problema.produto.integracao-desconhecida", slug)));
        }

        produto.getTags().clear();
        for (String slug : dados.tagSlugs() == null ? List.<String>of() : dados.tagSlugs()) {
            produto.getTags()
                    .add(tags.findBySlug(slug)
                            .orElseThrow(
                                    () -> new InvalidParameterException("problema.produto.tag-desconhecida", slug)));
        }
    }

    private ProductAdminView paraVisaoDeCuradoria(Product produto) {
        List<String> integracoesDoProduto = new ArrayList<>(produto.getIntegrations().stream()
                .map(Integration::getSlug)
                .sorted()
                .toList());
        List<String> tagsDoProduto = new ArrayList<>(
                produto.getTags().stream().map(Tag::getSlug).sorted().toList());

        return new ProductAdminView(
                produto.getId(),
                produto.getSlug(),
                produto.getName(),
                produto.getTaglinePt(),
                produto.getTaglineEn(),
                produto.getDescriptionPt(),
                produto.getDescriptionEn(),
                produto.getCategory().getId(),
                produto.getWebsiteUrl(),
                produto.getLogoUrl(),
                produto.getPricingModel(),
                produto.getPriceMin(),
                produto.getPriceCurrency(),
                produto.getPricePeriod(),
                produto.isHasFreeTrial(),
                produto.isHasFreePlan(),
                produto.getSupportedLanguages(),
                produto.getHqCountry(),
                produto.getStatus(),
                produto.getPublishedAt(),
                produto.getSourceNote(),
                integracoesDoProduto,
                tagsDoProduto,
                produto.getCreatedAt(),
                produto.getUpdatedAt());
    }

    // --- Conteudo curado e ciclo de publicacao (spec secao 5.2) ---

    /**
     * Substitui o conteudo daquele idioma (plan.md secao 5.2).
     *
     * <p>Substituicao, e nao remendo: o corpo descreve o registro inteiro. Assim o
     * curador consegue **remover** um contra, o que um merge parcial nao permitiria.
     */
    @Transactional
    public void substituirConteudo(UUID produtoId, AppLocale idioma, DadosDeConteudo dados) {
        Product produto = carregarProduto(produtoId);

        ProductContentId chave = new ProductContentId(produto.getId(), idioma.codigo());
        ProductContent conteudo = conteudos.findById(chave).orElseGet(() -> new ProductContent(chave));

        conteudo.setPros(dados.pros() == null ? List.of() : dados.pros());
        conteudo.setCons(dados.cons() == null ? List.of() : dados.cons());
        conteudo.setBestFor(dados.bestFor());
        conteudo.setFaq(dados.faq() == null ? List.of() : dados.faq());

        conteudos.saveAndFlush(conteudo);
    }

    /**
     * DRAFT -> PUBLISHED, se nao houver pendencia (AC-19).
     *
     * <p>Publicar um produto que ja esta publicado e aceito e nao mexe no
     * `published_at`: a data marca a **primeira** vez que a ficha foi ao ar, e
     * reescreve-la apagaria a informacao de ha quanto tempo ela existe.
     */
    @Transactional
    public ProductAdminView publicar(UUID id) {
        Product produto = carregarProduto(id);
        exigirEstado(produto, "publicar", ProductStatus.DRAFT, ProductStatus.PUBLISHED);

        List<ApiFieldError> pendencias = validador.pendencias(produto);
        if (!pendencias.isEmpty()) {
            throw new NotPublishableException(pendencias);
        }

        produto.setStatus(ProductStatus.PUBLISHED);
        if (produto.getPublishedAt() == null) {
            // Truncado para microssegundos porque e o que timestamptz guarda: sem isto
            // a resposta da primeira publicacao prometeria uma precisao que a leitura
            // seguinte nao confirma.
            produto.setPublishedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        }
        return paraVisaoDeCuradoria(produtos.saveAndFlush(produto));
    }

    /**
     * PUBLISHED -> DRAFT. O `published_at` fica onde esta: se o produto voltar ao
     * ar, continua sendo a data em que apareceu pela primeira vez.
     */
    @Transactional
    public ProductAdminView despublicar(UUID id) {
        Product produto = carregarProduto(id);
        exigirEstado(produto, "despublicar", ProductStatus.PUBLISHED);

        produto.setStatus(ProductStatus.DRAFT);
        return paraVisaoDeCuradoria(produtos.saveAndFlush(produto));
    }

    /** Qualquer estado -> ARCHIVED (spec secao 5.2). Some da rota publica na hora. */
    @Transactional
    public ProductAdminView arquivar(UUID id) {
        Product produto = carregarProduto(id);

        produto.setStatus(ProductStatus.ARCHIVED);
        return paraVisaoDeCuradoria(produtos.saveAndFlush(produto));
    }

    private void exigirEstado(Product produto, String operacao, ProductStatus... aceitos) {
        for (ProductStatus aceito : aceitos) {
            if (produto.getStatus() == aceito) {
                return;
            }
        }
        throw new ConflictException(
                "problema.produto.transicao-invalida",
                operacao,
                produto.getStatus().name());
    }

    // --- Categorias ---

    /** Todas as categorias, planas e ordenadas. O admin enxerga a lista inteira. */
    @Transactional(readOnly = true)
    public List<Category> listarCategorias() {
        return categorias.findAllByOrderBySortOrderAscSlugPtAsc();
    }

    @Transactional(readOnly = true)
    public Category categoriaPorId(UUID id) {
        return categorias
                .findById(id)
                .orElseThrow(() -> new NotFoundException("problema.categoria.id-nao-encontrado", id));
    }

    @Transactional
    public Category criarCategoria(DadosDeCategoria dados) {
        exigirSlugLivre(dados.slugPt(), null);
        exigirSlugLivre(dados.slugEn(), null);

        Category pai = resolverPai(dados.parentId(), null);
        Category categoria = new Category(dados.slugPt(), dados.slugEn(), dados.namePt(), dados.nameEn(), pai);
        aplicar(categoria, dados);

        return categorias.saveAndFlush(categoria);
    }

    @Transactional
    public Category atualizarCategoria(UUID id, DadosDeCategoria dados) {
        Category categoria = categoriaPorId(id);

        // Slug e imutavel apos a criacao (spec secao 4.1) e o banco recusa a troca.
        // Recusar aqui, com 409 explicado, e melhor do que aceitar o campo e
        // descarta-lo em silencio: o cliente acharia que a mudanca valeu.
        exigirSlugInalterado(dados.slugPt(), categoria.getSlugPt());
        exigirSlugInalterado(dados.slugEn(), categoria.getSlugEn());

        categoria.setParent(resolverPai(dados.parentId(), categoria));
        categoria.setNamePt(dados.namePt());
        categoria.setNameEn(dados.nameEn());
        aplicar(categoria, dados);

        return categorias.saveAndFlush(categoria);
    }

    /** Exclusao real, e so quando a categoria nao deixa nada orfao (AC-21). */
    @Transactional
    public void excluirCategoria(UUID id) {
        Category categoria = categoriaPorId(id);

        if (categorias.existsByParentId(id)) {
            throw new ConflictException("problema.categoria.com-subcategorias");
        }
        if (produtos.existsByCategoryId(id)) {
            throw new ConflictException("problema.categoria.com-produtos");
        }

        categorias.delete(categoria);
    }

    private void aplicar(Category categoria, DadosDeCategoria dados) {
        categoria.setDescriptionPt(dados.descriptionPt());
        categoria.setDescriptionEn(dados.descriptionEn());
        categoria.setSortOrder(dados.sortOrder() == null ? 0 : dados.sortOrder());
    }

    /**
     * Profundidade maxima 2 (spec secao 4.1), checada dos dois lados: pai que ja e
     * subcategoria, e categoria com filhas virando subcategoria.
     */
    private Category resolverPai(UUID parentId, Category categoria) {
        if (parentId == null) {
            return null;
        }
        if (categoria != null && parentId.equals(categoria.getId())) {
            throw new ConflictException("problema.categoria.pai-de-si-mesma");
        }

        Category pai = categorias
                .findById(parentId)
                .orElseThrow(() -> new NotFoundException("problema.categoria.id-nao-encontrado", parentId));

        if (pai.getParent() != null) {
            throw new ConflictException("problema.categoria.profundidade", parentId);
        }
        if (categoria != null && categorias.existsByParentId(categoria.getId())) {
            throw new ConflictException("problema.categoria.com-subcategorias-nao-vira-filha");
        }
        return pai;
    }

    /**
     * O slug e unico entre os dois idiomas (D-01): o pt de uma categoria nao pode
     * colidir com o en de outra, senao a rota publica ficaria ambigua.
     */
    private void exigirSlugLivre(String slug, UUID donoEsperado) {
        Optional<UUID> dono = categorias.buscarIdPorSlugEmQualquerIdioma(slug);
        if (dono.isPresent() && !dono.get().equals(donoEsperado)) {
            throw new ConflictException("problema.categoria.slug-em-uso", slug);
        }
    }

    private void exigirSlugInalterado(String pedido, String atual) {
        if (pedido != null && !pedido.equals(atual)) {
            throw new ConflictException("problema.categoria.slug-imutavel", atual);
        }
    }
}
