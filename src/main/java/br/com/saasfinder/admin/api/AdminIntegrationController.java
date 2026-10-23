package br.com.saasfinder.admin.api;

import br.com.saasfinder.admin.api.dto.AdminIntegrationResponse;
import br.com.saasfinder.admin.api.dto.IntegrationCreateRequest;
import br.com.saasfinder.catalog.domain.CatalogWriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Curadoria de integracoes (spec secao 5.2). CRUD simples: listar e criar. */
@Tag(name = "Curadoria: integracoes", description = "Cadastro de integracoes. Exige credencial de curadoria")
@RestController
@RequestMapping("/api/v1/admin/integrations")
public class AdminIntegrationController {

    private final CatalogWriteService catalogo;

    public AdminIntegrationController(CatalogWriteService catalogo) {
        this.catalogo = catalogo;
    }

    @Operation(summary = "Lista as integracoes cadastradas")
    @GetMapping
    public List<AdminIntegrationResponse> listar() {
        return catalogo.listarIntegracoes().stream()
                .map(AdminIntegrationResponse::de)
                .toList();
    }

    @Operation(
            summary = "Cadastra uma integracao",
            description = "O slug e a chave que os produtos usam para se vincular, entao repeti-lo"
                    + " e conflito, e nao uma segunda integracao com o mesmo apelido.")
    @ApiResponse(responseCode = "201", description = "Integracao criada")
    @ApiResponse(responseCode = "409", description = "Slug ja cadastrado", content = @Content)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminIntegrationResponse criar(@Valid @RequestBody IntegrationCreateRequest corpo) {
        return AdminIntegrationResponse.de(catalogo.criarIntegracao(corpo.slug(), corpo.name()));
    }
}
