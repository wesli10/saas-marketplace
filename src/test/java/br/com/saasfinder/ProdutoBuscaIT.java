package br.com.saasfinder;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.saasfinder.catalog.domain.Category;
import br.com.saasfinder.catalog.domain.PricingModel;
import br.com.saasfinder.catalog.domain.Product;
import br.com.saasfinder.catalog.domain.ProductStatus;
import br.com.saasfinder.catalog.domain.Tag;
import br.com.saasfinder.catalog.infra.CategoryRepository;
import br.com.saasfinder.catalog.infra.ProductRepository;
import br.com.saasfinder.catalog.infra.TagRepository;
import br.com.saasfinder.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** Criterio de verificacao das tarefas 001-T16 e 001-T17: AC-10, AC-11 e AC-12. */
class ProdutoBuscaIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private CategoryRepository categorias;

    @Autowired
    private ProductRepository produtos;

    @Autowired
    private TagRepository tags;

    @Autowired
    private JdbcTemplate jdbc;

    private Category atendimento;
    private Category conteudo;

    @BeforeEach
    void preparar() {
        jdbc.execute("truncate table category, integration, tag cascade");
        atendimento = categorias.save(
                new Category("atendimento", "customer-support", "Atendimento", "Customer support", null));
        conteudo = categorias.save(new Category("conteudo", "content", "Conteudo", "Content", null));
    }

    private Product criar(
            String slug, String nome, Category categoria, String taglinePt, String taglineEn, String descPt) {
        Product produto = new Product(
                slug,
                nome,
                categoria,
                taglinePt,
                taglineEn,
                descPt,
                "english description",
                "https://acme.com",
                PricingModel.SUBSCRIPTION,
                "https://acme.com/pricing");
        produto.setStatus(ProductStatus.PUBLISHED);
        produto.setPublishedAt(Instant.now());
        return produtos.saveAndFlush(produto);
    }

    private JsonNode buscar(String consulta) {
        ResponseEntity<JsonNode> resposta = http.getForEntity("/api/v1/products" + consulta, JsonNode.class);
        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        return resposta.getBody();
    }

    private List<String> slugs(JsonNode corpo) {
        List<String> encontrados = new ArrayList<>();
        corpo.get("items").forEach(item -> encontrados.add(item.get("slug").asText()));
        return encontrados;
    }

    @Test
    @DisplayName("AC-10: a mesma busca nos dois idiomas encontra o mesmo conjunto")
    void mesmoConjuntoNosDoisIdiomas() {
        criar(
                "acme-chat",
                "Acme Chat",
                atendimento,
                "atendimento ao cliente no whatsapp",
                "customer support on whatsapp",
                "descricao");
        criar("zeta-desk", "Zeta Desk", atendimento, "central de atendimento", "customer support desk", "descricao");
        criar("escritor-ia", "Escritor IA", conteudo, "geracao de textos", "content generation", "descricao");

        assertThat(slugs(buscar("?q=atendimento&lang=pt-BR"))).containsExactlyInAnyOrder("acme-chat", "zeta-desk");
        assertThat(slugs(buscar("?q=customer support&lang=en"))).containsExactlyInAnyOrder("acme-chat", "zeta-desk");
    }

    @Test
    @DisplayName("AC-11: a busca ignora acento nos dois sentidos")
    void buscaIgnoraAcento() {
        criar("auto-flow", "Auto Flow", conteudo, "automação de processos", "process automation", "descricao");

        assertThat(slugs(buscar("?q=automacao"))).containsExactly("auto-flow");
        assertThat(slugs(buscar("?q=automação"))).containsExactly("auto-flow");
    }

    @Test
    @DisplayName("AC-10: quem casa no nome vem antes de quem casa so na descricao")
    void relevanciaColocaNomePrimeiro() {
        criar("so-na-descricao", "Zeta Desk", atendimento, "outra coisa", "other", "isso aqui fala de chatbot");
        criar("no-nome", "Chatbot Pro", conteudo, "outra coisa", "other", "sem relacao");

        assertThat(slugs(buscar("?q=chatbot"))).containsExactly("no-nome", "so-na-descricao");
    }

    @Test
    @DisplayName("o nome da tag tambem e encontrado pela busca")
    void tagEntraNaBusca() {
        Product produto = criar("acme-chat", "Acme Chat", atendimento, "tagline", "tagline en", "descricao");
        Tag tag = tags.save(new Tag("ia-generativa", "inteligencia artificial", "artificial intelligence"));
        jdbc.update("insert into product_tag values (?, ?)", produto.getId(), tag.getId());

        assertThat(slugs(buscar("?q=inteligencia"))).containsExactly("acme-chat");
    }

    @Test
    @DisplayName("matched_by indica FULL_TEXT quando casou, e NONE quando nao casou")
    void matchedBy() {
        criar("acme-chat", "Acme Chat", atendimento, "atendimento", "support", "descricao");

        JsonNode comResultado = buscar("?q=atendimento");
        assertThat(comResultado.get("query").get("matched_by").asText()).isEqualTo("FULL_TEXT");
        assertThat(comResultado.get("query").get("q").asText()).isEqualTo("atendimento");

        JsonNode semResultado = buscar("?q=xilofone");
        assertThat(semResultado.get("items")).isEmpty();
        assertThat(semResultado.get("query").get("matched_by").asText()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("sem texto de busca, matched_by fica nulo: nao houve casamento a relatar")
    void semTextoNaoTemMatchedBy() {
        criar("acme-chat", "Acme Chat", atendimento, "atendimento", "support", "descricao");

        JsonNode corpo = buscar("");

        assertThat(corpo.get("items")).hasSize(1);
        assertThat(corpo.get("query").get("matched_by").isNull()).isTrue();
    }

    @Test
    @DisplayName("entrada livre com aspas e simbolos nao derruba a busca")
    void entradaLivreNaoQuebra() {
        criar("acme-chat", "Acme Chat", atendimento, "atendimento ao cliente", "customer support", "descricao");

        // to_tsquery estouraria com qualquer um destes; websearch_to_tsquery aceita.
        for (String entrada : List.of(
                "\"atendimento ao cliente\"",
                "atendimento or suporte",
                "atendimento -whatsapp",
                "&&&",
                ":",
                "a & b | c")) {
            ResponseEntity<String> resposta = http.getForEntity("/api/v1/products?q={q}", String.class, entrada);
            assertThat(resposta.getStatusCode().value())
                    .as("entrada: %s", entrada)
                    .isEqualTo(200);
        }
    }

    @Test
    @DisplayName("aspas funcionam como frase exata")
    void aspasBuscamFraseExata() {
        criar("com-frase", "Acme", atendimento, "atendimento ao cliente", "customer support", "descricao");
        criar("palavras-soltas", "Zeta", atendimento, "atendimento de qualidade ao publico", "support", "cliente");

        assertThat(slugs(buscar("?q=\"atendimento ao cliente\""))).containsExactly("com-frase");
    }

    @Test
    @DisplayName("a busca combina com os filtros")
    void buscaCombinaComFiltro() {
        criar("de-atendimento", "Acme Chat", atendimento, "atendimento no whatsapp", "support", "descricao");
        criar("de-conteudo", "Zeta Write", conteudo, "atendimento por texto", "support", "descricao");

        assertThat(slugs(buscar("?q=atendimento&category=atendimento"))).containsExactly("de-atendimento");
    }

    @Test
    @DisplayName("busca so com espacos e tratada como ausencia de busca")
    void buscaEmBrancoNaoFiltra() {
        criar("acme-chat", "Acme Chat", atendimento, "tagline", "tagline en", "descricao");

        // Passado como variavel de template: montar a URL com %20 na mao faria o
        // RestTemplate re-codificar o %, e o q chegaria como o texto literal "%20%20".
        JsonNode corpo = http.getForEntity("/api/v1/products?q={q}", JsonNode.class, "   ")
                .getBody();

        assertThat(corpo.get("items")).hasSize(1);
        assertThat(corpo.get("query").get("matched_by").isNull()).isTrue();
    }

    @Test
    @DisplayName("AC-12: nome com um caractere trocado ainda encontra o produto, por similaridade")
    void erroDeDigitacaoNoNomeCaiNoFallback() {
        criar("chatvolt", "Chatvolt", atendimento, "atendimento no whatsapp", "customer support", "descricao");

        // "Chatvold": o full-text nao casa - ele compara lexemas inteiros, nao aproxima.
        JsonNode corpo = buscar("?q=Chatvold");

        assertThat(slugs(corpo)).containsExactly("chatvolt");
        assertThat(corpo.get("query").get("matched_by").asText()).isEqualTo("TRIGRAM");
        assertThat(corpo.get("total").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-12: quando o full-text acha, o fallback nao entra")
    void ftsQueAchaNaoDisparaOFallback() {
        criar("chatvolt", "Chatvolt", atendimento, "atendimento no whatsapp", "customer support", "descricao");
        criar("chatvoltz", "Chatvoltz", conteudo, "outra coisa", "other", "sem relacao");

        JsonNode corpo = buscar("?q=Chatvolt");

        // O vizinho parecido so apareceria se o fallback tivesse rodado.
        assertThat(slugs(corpo)).containsExactly("chatvolt");
        assertThat(corpo.get("query").get("matched_by").asText()).isEqualTo("FULL_TEXT");
    }

    @Test
    @DisplayName("AC-12: pagina alem do fim nao vira NONE - o casamento aconteceu, a pagina e que passou")
    void paginaAlemDoFimNaoApagaOCasamento() {
        // Criterio de verificacao da tarefa 002-T28. Quem decidia se houve
        // casamento era a pagina ter vindo com linha; entao, alem do fim, uma
        // busca legitima era relatada como NONE - e ainda disparava o fallback
        // de similaridade por nada.
        criar("chatvolt", "Chatvolt", atendimento, "atendimento no whatsapp", "customer support", "descricao");

        JsonNode corpo = buscar("?q=Chatvolt&page=9&size=2");

        assertThat(corpo.get("items")).isEmpty();
        assertThat(corpo.get("total").asInt()).isEqualTo(1);
        assertThat(corpo.get("query").get("matched_by").asText()).isEqualTo("FULL_TEXT");
    }

    @Test
    @DisplayName("AC-12: texto sem relacao nenhuma devolve lista vazia com matched_by NONE")
    void semRelacaoNaoResgataNada() {
        criar("chatvolt", "Chatvolt", atendimento, "atendimento no whatsapp", "customer support", "descricao");

        JsonNode corpo = buscar("?q=xilofone");

        assertThat(corpo.get("items")).isEmpty();
        assertThat(corpo.get("total").asInt()).isZero();
        assertThat(corpo.get("query").get("matched_by").asText()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("no fallback, o nome mais parecido vem primeiro")
    void fallbackOrdenaPorSemelhanca() {
        criar("zylotron", "Zylotron", atendimento, "tagline", "tagline en", "descricao");
        criar("zylotronn", "Zylotronn", conteudo, "tagline", "tagline en", "descricao");

        assertThat(slugs(buscar("?q=Zylotro"))).containsExactly("zylotron", "zylotronn");
    }

    @Test
    @DisplayName("o resgate por similaridade continua obedecendo aos filtros")
    void fallbackRespeitaOsFiltros() {
        criar("chatvolt", "Chatvolt", atendimento, "atendimento no whatsapp", "customer support", "descricao");

        // O produto seria resgatado pela semelhanca, mas esta na categoria errada.
        JsonNode corpo = buscar("?q=Chatvold&category=conteudo");

        assertThat(corpo.get("items")).isEmpty();
        assertThat(corpo.get("query").get("matched_by").asText()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("produto nao publicado nao e resgatado pela similaridade")
    void fallbackNaoVazaRascunho() {
        Product rascunho = criar("chatvolt", "Chatvolt", atendimento, "tagline", "tagline en", "descricao");
        rascunho.setStatus(ProductStatus.DRAFT);
        produtos.saveAndFlush(rascunho);

        JsonNode corpo = buscar("?q=Chatvold");

        assertThat(corpo.get("items")).isEmpty();
        assertThat(corpo.get("query").get("matched_by").asText()).isEqualTo("NONE");
    }
}
