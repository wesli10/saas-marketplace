package br.com.saasfinder.catalog.infra;

import br.com.saasfinder.catalog.domain.Category;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    /**
     * Resolve o slug pela tabela de lookup, que aceita os dois idiomas e garante
     * unicidade global entre eles (D-01). Comparar slug_pt ou slug_en direto na
     * categoria funcionaria hoje, mas deixaria a garantia de unicidade sem uso -
     * e e ela que impede duas categorias responderem pelo mesmo slug (AC-04).
     */
    @Query(value = "select cs.category_id from category_slug cs where cs.slug = :slug", nativeQuery = true)
    Optional<UUID> buscarIdPorSlugEmQualquerIdioma(@Param("slug") String slug);

    /** Lista plana para a curadoria, na mesma ordem que a arvore publica usa. */
    List<Category> findAllByOrderBySortOrderAscSlugPtAsc();

    /** Tem subcategoria? Guarda de exclusao e de profundidade (AC-21). */
    boolean existsByParentId(UUID parentId);
}
