package br.com.saasfinder.reviews.infra;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * A lista versionada de provedores de e-mail gratuito (spec secao 4.5).
 *
 * <p>Dado que muda com o tempo mora em arquivo, como o seed - nunca em constante
 * no codigo. O arquivo e lido <b>uma vez</b>, na construcao do bean: a pergunta e
 * feita a cada confirmacao de review, e reler o classpath a cada uma seria I/O por
 * nada.
 *
 * <p><b>Arquivo ausente ou vazio derruba o boot</b>, de proposito. O modo de falha
 * silencioso seria o pior possivel: uma lista vazia nao da erro em lugar nenhum,
 * ela apenas marca <i>todo mundo</i> como verificado, e o selo perde o sentido sem
 * que ninguem perceba.
 */
@Component
public class ProvedoresGratuitos {

    static final String CAMINHO = "reviews/provedores-gratuitos.txt";

    private final Set<String> dominios;

    public ProvedoresGratuitos() {
        this(new ClassPathResource(CAMINHO));
    }

    ProvedoresGratuitos(Resource origem) {
        this.dominios = carregar(origem);
    }

    /**
     * O dominio e de provedor gratuito? A comparacao e em minusculas: dominio nao
     * tem caixa, e "GMAIL.COM" no e-mail de quem escreve nao pode virar selo.
     */
    public boolean ehGratuito(String dominio) {
        if (dominio == null || dominio.isBlank()) {
            return false;
        }
        return dominios.contains(dominio.trim().toLowerCase(Locale.ROOT));
    }

    /** Quantos dominios a lista carregou. Existe para o log de boot e para o teste. */
    public int quantidade() {
        return dominios.size();
    }

    private static Set<String> carregar(Resource origem) {
        Set<String> lidos = new LinkedHashSet<>();

        if (!origem.exists()) {
            throw new IllegalStateException("lista de provedores gratuitos nao encontrada em " + origem.getDescription()
                    + ": sem ela, toda review sairia como verificada");
        }

        try (BufferedReader leitor =
                new BufferedReader(new InputStreamReader(origem.getInputStream(), StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = leitor.readLine()) != null) {
                String dominio = linha.trim();
                if (dominio.isEmpty() || dominio.startsWith("#")) {
                    continue;
                }
                lidos.add(dominio.toLowerCase(Locale.ROOT));
            }
        } catch (IOException erro) {
            throw new IllegalStateException("falha ao ler " + origem.getDescription(), erro);
        }

        if (lidos.isEmpty()) {
            throw new IllegalStateException("lista de provedores gratuitos vazia em " + origem.getDescription()
                    + ": uma lista vazia marcaria toda review como verificada");
        }

        return Set.copyOf(lidos);
    }
}
