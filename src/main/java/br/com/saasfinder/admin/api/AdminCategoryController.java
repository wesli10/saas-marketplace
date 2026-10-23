package br.com.saasfinder.admin.api;

import br.com.saasfinder.admin.api.dto.AdminCategoryResponse;
import br.com.saasfinder.admin.api.dto.CategoryUpsertRequest;
import br.com.saasfinder.catalog.domain.CatalogWriteService;
import br.com.saasfinder.shared.error.ApiException;
import br.com.saasfinder.shared.error.ApiFieldError;
import br.com.saasfinder.shared.error.ProblemTypes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Curadoria de categorias (spec secao 5.2). Protegida por HTTP Basic no
 * SecurityConfig, junto com todo o /api/v1/admin.
 *
 * <p>Aqui nao mora regra de negocio (constituicao secao 6): o controller valida o
 * corpo na borda e entrega ao servico de escrita do catalog.
 */
@Tag(name = "Curadoria: categorias", description = "CRUD de categorias. Exige credencial de curadoria")
@RestController
@RequestMapping("/api/v1/admin/categories")
public class AdminCategoryController {

    private final CatalogWriteService catalogo;

    public AdminCategoryController(CatalogWriteService catalogo) {
        this.catalogo = catalogo;
    }

    @Operation(
            summary = "Lista todas as categorias",
            description = "Lista plana e com os **dois** idiomas, ao contrario da arvore publica,"
                    + " que ja vem resolvida num idioma so.")
    @GetMapping
    public List<AdminCategoryResponse> listar() {
        return catalogo.listarCategorias().stream()
                .map(AdminCategoryResponse::de)
                .toList();
    }

    @Operation(summary = "Uma categoria pelo id")
    @ApiResponse(responseCode = "404", description = "Id desconhecido", content = @Content)
    @GetMapping("/{id}")
    public AdminCategoryResponse porId(@PathVariable UUID id) {
        return AdminCategoryResponse.de(catalogo.categoriaPorId(id));
    }

    @Operation(
            summary = "Cria uma categoria",
            description = "Profundidade maxima 2: o pai nao pode ser, ele mesmo, uma subcategoria."
                    + " O slug e unico entre os dois idiomas - o `slug_pt` de uma nao pode colidir com"
                    + " o `slug_en` de outra, senao a rota publica ficaria ambigua (D-01).")
    @ApiResponse(responseCode = "201", description = "Categoria criada")
    @ApiResponse(responseCode = "404", description = "`parent_id` nao existe", content = @Content)
    @ApiResponse(
            responseCode = "409",
            description = "Slug ja em uso, ou pai que ja e subcategoria",
            content = @Content(mediaType = "application/problem+json"))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminCategoryResponse criar(@Valid @RequestBody CategoryUpsertRequest corpo) {
        exigirSlugs(corpo);
        return AdminCategoryResponse.de(catalogo.criarCategoria(corpo.paraDominio()));
    }

    @Operation(
            summary = "Atualiza nomes, descricoes, pai e ordem",
            description = "O slug **nao** muda depois da criacao (spec 4.1). Mandar um slug diferente"
                    + " do atual responde 409, em vez de o campo ser ignorado em silencio.")
    @ApiResponse(responseCode = "404", description = "Id desconhecido", content = @Content)
    @ApiResponse(
            responseCode = "409",
            description = "Tentativa de trocar o slug, ou profundidade violada",
            content = @Content)
    @PutMapping("/{id}")
    public AdminCategoryResponse atualizar(@PathVariable UUID id, @Valid @RequestBody CategoryUpsertRequest corpo) {
        return AdminCategoryResponse.de(catalogo.atualizarCategoria(id, corpo.paraDominio()));
    }

    @Operation(
            summary = "Exclui uma categoria vazia",
            description = "So exclui se nao houver subcategoria nem produto vinculado (AC-21).")
    @ApiResponse(responseCode = "204", description = "Excluida")
    @ApiResponse(responseCode = "404", description = "Id desconhecido", content = @Content)
    @ApiResponse(responseCode = "409", description = "Tem subcategorias ou produtos", content = @Content)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        catalogo.excluirCategoria(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Slug e obrigatorio na criacao e ausente no PUT, entao a exigencia nao cabe
     * numa anotacao do record, que vale para os dois. O erro sai no mesmo formato
     * do Bean Validation, com errors[] apontando o campo (AC-18).
     */
    private void exigirSlugs(CategoryUpsertRequest corpo) {
        List<ApiFieldError> faltando = new ArrayList<>();
        if (corpo.slugPt() == null || corpo.slugPt().isBlank()) {
            faltando.add(new ApiFieldError("slug_pt", "NotBlank", "nao pode estar em branco"));
        }
        if (corpo.slugEn() == null || corpo.slugEn().isBlank()) {
            faltando.add(new ApiFieldError("slug_en", "NotBlank", "nao pode estar em branco"));
        }
        if (!faltando.isEmpty()) {
            throw new ApiException(ProblemTypes.VALIDATION_FAILED, "problema.validation-failed.detalhe", faltando);
        }
    }
}
