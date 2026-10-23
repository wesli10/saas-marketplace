package br.com.saasfinder.catalog.infra;

import br.com.saasfinder.catalog.domain.Product;
import br.com.saasfinder.catalog.domain.ProductStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    /** Guarda de exclusao de categoria: qualquer status conta, nao so publicado (AC-21). */
    boolean existsByCategoryId(UUID categoryId);

    /**
     * Contagem de publicados por categoria, numa consulta so - a alternativa seria
     * uma consulta por categoria ao montar a arvore.
     *
     * <p>Cada linha e um par (id da categoria, total). Categoria sem produto
     * publicado nao aparece no resultado; quem monta a arvore assume zero.
     */
    @Query("select p.category.id, count(p) from Product p where p.status = 'PUBLISHED' group by p.category.id")
    List<Object[]> contarPublicadosPorCategoria();

    /**
     * Ficha de um produto publicado, com categoria, tags e integracoes numa ida so
     * ao banco (DT-13). Sem os fetch, cada colecao viraria um select extra ao ser
     * lida na montagem da view.
     *
     * <p>O filtro por PUBLISHED mora aqui, e nao em quem chama: DRAFT e ARCHIVED
     * nao existem para o publico, e "nao existe" e justamente o 404 do AC-06.
     */
    @Query(
            """
            select p from Product p
              join fetch p.category
              left join fetch p.tags
              left join fetch p.integrations
             where p.slug = :slug and p.status = 'PUBLISHED'
            """)
    Optional<Product> buscarPublicadoPorSlug(@Param("slug") String slug);

    /**
     * Mesma ficha, para os 2 ou 3 slugs da comparacao (AC-16), numa consulta so.
     *
     * <p>Vem sem ordem: quem chama reordena pelo pedido do cliente, que e o unico
     * criterio que a comparacao respeita.
     */
    @Query(
            """
            select p from Product p
              join fetch p.category
              left join fetch p.tags
              left join fetch p.integrations
             where p.slug in :slugs and p.status = 'PUBLISHED'
            """)
    List<Product> buscarPublicadosPorSlugs(@Param("slugs") Collection<String> slugs);

    boolean existsBySlug(String slug);

    /**
     * A listagem de curadoria vem em duas consultas: primeiro so os ids da pagina,
     * depois o agregado deles com os vinculos.
     *
     * <p>Paginar direto na consulta com `join fetch` faria o Hibernate trazer tudo
     * e recortar em memoria - o `limit` nao pode ser aplicado quando cada produto
     * ocupa varias linhas do resultado.
     */
    @Query(value = "select p.id from Product p", countQuery = "select count(p) from Product p")
    Page<UUID> paginarIds(Pageable pagina);

    @Query(
            value = "select p.id from Product p where p.status = :status",
            countQuery = "select count(p) from Product p where p.status = :status")
    Page<UUID> paginarIdsPorStatus(@Param("status") ProductStatus status, Pageable pagina);

    /** Agregado completo dos ids pedidos, sem filtro de status: quem cura ve tudo. */
    @Query(
            """
            select p from Product p
              join fetch p.category
              left join fetch p.tags
              left join fetch p.integrations
             where p.id in :ids
            """)
    List<Product> buscarComVinculos(@Param("ids") Collection<UUID> ids);
}
