package br.com.saasfinder.shared.locale;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Idioma resolvido para a requisicao em curso.
 *
 * <p>Le o atributo gravado pelo {@link LocaleResolutionFilter}, que e o valor
 * exato - sem passar por {@link java.util.Locale} e voltar, o que perderia a
 * distincao entre codigo e lingua. O LocaleContextHolder serve de rede: e o que
 * o MessageSource consulta, e o AppLocaleResolver garante que os dois concordem.
 */
public final class RequestLocale {

    private RequestLocale() {}

    public static AppLocale atual() {
        RequestAttributes atributos = RequestContextHolder.getRequestAttributes();
        if (atributos != null) {
            Object resolvido = atributos.getAttribute(LocaleResolutionFilter.ATRIBUTO, RequestAttributes.SCOPE_REQUEST);
            if (resolvido instanceof AppLocale idioma) {
                return idioma;
            }
        }
        return AppLocale.porLingua(LocaleContextHolder.getLocale().getLanguage())
                .orElse(AppLocale.PT_BR);
    }
}
