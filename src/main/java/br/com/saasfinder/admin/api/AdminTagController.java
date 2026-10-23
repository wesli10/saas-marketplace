package br.com.saasfinder.admin.api;

import br.com.saasfinder.admin.api.dto.AdminTagResponse;
import br.com.saasfinder.admin.api.dto.TagCreateRequest;
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

/** Curadoria de tags (spec secao 5.2). CRUD simples: listar e criar. */
@Tag(name = "Curadoria: tags", description = "Cadastro de tags. Exige credencial de curadoria")
@RestController
@RequestMapping("/api/v1/admin/tags")
public class AdminTagController {

    private final CatalogWriteService catalogo;

    public AdminTagController(CatalogWriteService catalogo) {
        this.catalogo = catalogo;
    }

    @Operation(summary = "Lista as tags cadastradas")
    @GetMapping
    public List<AdminTagResponse> listar() {
        return catalogo.listarTags().stream().map(AdminTagResponse::de).toList();
    }

    @Operation(
            summary = "Cadastra uma tag",
            description = "Nome nos dois idiomas: o nome da tag entra no vetor de busca de cada"
                    + " idioma, entao uma tag so em portugues sumiria da busca em ingles.")
    @ApiResponse(responseCode = "201", description = "Tag criada")
    @ApiResponse(responseCode = "409", description = "Slug ja cadastrado", content = @Content)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminTagResponse criar(@Valid @RequestBody TagCreateRequest corpo) {
        return AdminTagResponse.de(catalogo.criarTag(corpo.slug(), corpo.namePt(), corpo.nameEn()));
    }
}
