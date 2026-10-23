package br.com.saasfinder.catalog.domain;

import br.com.saasfinder.catalog.infra.ProductContentRepository;
import br.com.saasfinder.shared.error.ApiFieldError;
import br.com.saasfinder.shared.error.ProblemDetailFactory;
import br.com.saasfinder.shared.locale.AppLocale;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * O que falta para um produto poder ir ao ar (plan.md DT-16).
 *
 * <p>A regra vive num lugar so porque tem dois usuarios: a rota de publicacao,
 * que devolve 422 com as pendencias em errors[] (AC-19), e o loader de seed
 * (T25). Duplicar significaria seed publicando o que o admin recusaria.
 *
 * <p>Devolve a **lista inteira** de pendencias, e nao a primeira: o curador
 * corrige tudo de uma vez em vez de descobrir uma por requisicao.
 */
@Component
public class ProductPublicationValidator {

    /**
     * Modelos que so fazem sentido com preco divulgado (spec secao 4.2).
     *
     * <p>FREE e FREEMIUM nao tem preco a informar, e CONTACT_SALES e justamente o
     * caso em que o preco nao e publico.
     */
    private static final Set<PricingModel> EXIGEM_PRECO =
            EnumSet.of(PricingModel.SUBSCRIPTION, PricingModel.USAGE_BASED, PricingModel.ONE_TIME);

    private final ProductContentRepository conteudos;
    private final ProblemDetailFactory problemas;

    public ProductPublicationValidator(ProductContentRepository conteudos, ProblemDetailFactory problemas) {
        this.conteudos = conteudos;
        this.problemas = problemas;
    }

    /** Lista vazia = pode publicar. */
    public List<ApiFieldError> pendencias(Product produto) {
        List<ApiFieldError> pendencias = new ArrayList<>();

        // Todo texto visivel existe nos dois idiomas (constituicao secao 5): publicar
        // com metade do conteudo colocaria no ar uma ficha vazia para quem le em ingles.
        for (AppLocale idioma : AppLocale.values()) {
            if (!conteudos.existsById(new ProductContentId(produto.getId(), idioma.codigo()))) {
                pendencias.add(new ApiFieldError(
                        "product_content." + idioma.codigo(),
                        "Missing",
                        problemas.texto("pendencia.conteudo-ausente", idioma.codigo())));
            }
        }

        if (produto.getSupportedLanguages().isEmpty()) {
            pendencias.add(new ApiFieldError(
                    "supported_languages", "Missing", problemas.texto("pendencia.sem-idioma-suportado")));
        }

        if (EXIGEM_PRECO.contains(produto.getPricingModel()) && produto.getPriceMin() == null) {
            pendencias.add(new ApiFieldError(
                    "price_min",
                    "Missing",
                    problemas.texto(
                            "pendencia.preco-obrigatorio",
                            produto.getPricingModel().name())));
        }

        return pendencias;
    }
}
