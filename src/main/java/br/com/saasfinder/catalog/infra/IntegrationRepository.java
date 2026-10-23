package br.com.saasfinder.catalog.infra;

import br.com.saasfinder.catalog.domain.Integration;
import br.com.saasfinder.catalog.domain.IntegrationView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IntegrationRepository extends JpaRepository<Integration, UUID> {

    Optional<Integration> findBySlug(String slug);

    List<Integration> findAllByOrderByNameAsc();

    /**
     * Todas as integracoes com a contagem de produtos publicados que as usam.
     *
     * <p>A contagem e subconsulta correlacionada, e nao join com group by, para
     * que integracao sem nenhum produto publicado apareca com zero em vez de
     * sumir do resultado. Continua sendo um unico SELECT.
     */
    @Query(
            """
            select new br.com.saasfinder.catalog.domain.IntegrationView(
                       i.slug,
                       i.name,
                       (select count(p) from Product p
                          join p.integrations usada
                         where usada.id = i.id and p.status = 'PUBLISHED'))
              from Integration i
             order by i.name
            """)
    List<IntegrationView> listarComContagemDePublicados();
}
