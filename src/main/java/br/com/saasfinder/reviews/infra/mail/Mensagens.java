package br.com.saasfinder.reviews.infra.mail;

import br.com.saasfinder.reviews.domain.Email;
import br.com.saasfinder.shared.locale.AppLocale;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * Os textos que <b>nos</b> escrevemos, nos dois idiomas (constituicao secao 5).
 *
 * <p>Um arquivo por idioma em {@code resources/mail/}: a <b>primeira linha e o
 * assunto</b>, o resto e o corpo. Sem engine de template (plano DT-05) - sao duas
 * frases e um link, e {@link MessageFormat} ja faz a substituicao. Trazer
 * Thymeleaf para isto seria uma dependencia inteira por conveniencia de sintaxe.
 *
 * <p>Cuidado ao editar os arquivos: no {@code MessageFormat}, apostrofo e caractere
 * de escape. Os textos sao escritos sem apostrofo de proposito.
 *
 * <p>A mensagem nao usa nome de marca. O produto ainda nao tem um definido, e
 * inventar um no e-mail seria dado de cabeca (constituicao secao 7); quem
 * identifica o remetente e o proprio {@code MAIL_FROM}.
 */
@Component
public class Mensagens {

    /**
     * O caminho da pagina de confirmacao, por idioma.
     *
     * <p><b>E conhecimento duplicado</b>: quem manda nas rotas do site e o
     * {@code web/src/lib/routes.ts}. O e-mail sai do backend, entao ele precisa
     * saber montar o endereco - nao ha como o servidor perguntar ao Next. A
     * duplicacao fica registrada aqui e e coberta de ponta a ponta na T13, que
     * pega o link no Mailpit e o abre de verdade: se as duas metades divergirem, o
     * teste quebra em vez de o link morrer em producao.
     */
    private static final Map<AppLocale, String> CAMINHO_DE_CONFIRMACAO = new EnumMap<>(Map.of(
            AppLocale.PT_BR, "/pt-BR/reviews/confirmar",
            AppLocale.EN, "/en/reviews/confirm"));

    /**
     * O caminho da ficha do produto, por idioma. Mesma duplicacao do mapa acima, e
     * pelo mesmo motivo - com um atenuante: estes dois segmentos sao percorridos por
     * dezenas de testes e2e da fatia 002, entao mudar um deles quebra muita coisa
     * antes de chegar a um e-mail.
     */
    private static final Map<AppLocale, String> CAMINHO_DO_PRODUTO =
            new EnumMap<>(Map.of(AppLocale.PT_BR, "/pt-BR/produtos/", AppLocale.EN, "/en/products/"));

    private final Map<AppLocale, String[]> confirmacao = new EnumMap<>(AppLocale.class);
    private final Map<AppLocale, String[]> publicada = new EnumMap<>(AppLocale.class);
    private final String urlDoSite;

    public Mensagens(@Value("${app.site-url}") String urlDoSite) {
        // Sem barra no fim, para a concatenacao com o caminho nao gerar "//".
        this.urlDoSite = urlDoSite.endsWith("/") ? urlDoSite.substring(0, urlDoSite.length() - 1) : urlDoSite;

        for (AppLocale idioma : AppLocale.values()) {
            confirmacao.put(idioma, carregar("mail/confirmacao." + idioma.codigo() + ".txt"));
            publicada.put(idioma, carregar("mail/publicada." + idioma.codigo() + ".txt"));
        }
    }

    /**
     * O e-mail com o link de confirmacao, no idioma em que a review foi escrita
     * (AC-16).
     */
    public Email confirmacao(
            String destinatario, AppLocale idioma, String nomeDoAutor, String produto, String slug, String token) {
        String[] modelo = confirmacao.get(idioma);
        String link = linkDeConfirmacao(idioma, token, slug);

        return new Email(
                destinatario,
                MessageFormat.format(modelo[0], produto, nomeDoAutor, link),
                MessageFormat.format(modelo[1], produto, nomeDoAutor, link));
    }

    /**
     * O aviso de que a review foi publicada (spec Q-02), no idioma em que ela foi
     * escrita.
     */
    public Email publicada(String destinatario, AppLocale idioma, String nomeDoAutor, String produto, String slug) {
        String[] modelo = publicada.get(idioma);
        String link = linkDoProduto(idioma, slug);

        return new Email(
                destinatario,
                MessageFormat.format(modelo[0], produto, nomeDoAutor, link),
                MessageFormat.format(modelo[1], produto, nomeDoAutor, link));
    }

    /** A ficha publica do produto, no idioma da review. */
    public String linkDoProduto(AppLocale idioma, String slug) {
        return urlDoSite + CAMINHO_DO_PRODUTO.get(idioma) + slug;
    }

    /**
     * O endereco que vai no e-mail. O token viaja na query, ja escapado.
     *
     * <p>O slug do produto vai junto: a tela de confirmacao oferece reenvio quando
     * o token expirou, e o reenvio precisa saber de que produto e a review. Sem
     * ele, a pessoa que clicou tarde demais teria de lembrar e digitar.
     */
    public String linkDeConfirmacao(AppLocale idioma, String token, String slug) {
        return urlDoSite + CAMINHO_DE_CONFIRMACAO.get(idioma) + "?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8) + "&produto="
                + URLEncoder.encode(slug, StandardCharsets.UTF_8);
    }

    /** Devolve [assunto, corpo]. Arquivo faltando derruba o boot, como a lista de provedores. */
    private static String[] carregar(String caminho) {
        ClassPathResource recurso = new ClassPathResource(caminho);
        if (!recurso.exists()) {
            throw new IllegalStateException("modelo de e-mail nao encontrado: " + caminho);
        }

        String conteudo;
        try {
            conteudo = StreamUtils.copyToString(recurso.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException erro) {
            throw new UncheckedIOException("falha ao ler o modelo de e-mail " + caminho, erro);
        }

        int quebra = conteudo.indexOf('\n');
        if (quebra < 0) {
            throw new IllegalStateException("modelo de e-mail sem corpo (so a linha de assunto): " + caminho);
        }

        return new String[] {
            conteudo.substring(0, quebra).trim(), conteudo.substring(quebra + 1).trim()
        };
    }
}
