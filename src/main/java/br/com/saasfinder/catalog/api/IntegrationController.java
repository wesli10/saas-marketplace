package br.com.saasfinder.catalog.api;

import br.com.saasfinder.catalog.api.dto.IntegrationListResponse;
import br.com.saasfinder.catalog.domain.CatalogFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Integracoes do catalogo (spec secao 5.1). Rota publica. */
@Tag(name = "Integracoes", description = "Integracoes declaradas pelos produtos do catalogo")
@RestController
@RequestMapping("/api/v1/integrations")
public class IntegrationController {

    private final CatalogFacade catalogo;

    public IntegrationController(CatalogFacade catalogo) {
        this.catalogo = catalogo;
    }

    @Operation(
            summary = "Integracoes com a contagem de produtos",
            description = "`product_count` conta so os publicados. Integracao sem nenhum produto"
                    + " aparece com zero, em vez de sumir da lista. Sem `locale`: nome de integracao"
                    + " e nome proprio e nao se traduz.")
    @GetMapping
    public IntegrationListResponse listar() {
        return new IntegrationListResponse(catalogo.integracoes());
    }
}
