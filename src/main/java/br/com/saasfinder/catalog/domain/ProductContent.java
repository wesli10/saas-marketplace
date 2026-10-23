package br.com.saasfinder.catalog.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Conteudo curado longo, um registro por idioma (spec secao 4.3).
 *
 * <p>O locale fica como texto por enquanto; o enum AppLocale entra no 001-T11,
 * junto com a resolucao de idioma da requisicao.
 */
@Entity
@Table(name = "product_content")
public class ProductContent {

    @EmbeddedId
    private ProductContentId id;

    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> pros = List.of();

    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> cons = List.of();

    private String bestFor;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<FaqItem> faq = List.of();

    protected ProductContent() {}

    public ProductContent(ProductContentId id) {
        this.id = id;
    }

    public ProductContentId getId() {
        return id;
    }

    public List<String> getPros() {
        return pros;
    }

    public void setPros(List<String> pros) {
        this.pros = pros;
    }

    public List<String> getCons() {
        return cons;
    }

    public void setCons(List<String> cons) {
        this.cons = cons;
    }

    public String getBestFor() {
        return bestFor;
    }

    public void setBestFor(String bestFor) {
        this.bestFor = bestFor;
    }

    public List<FaqItem> getFaq() {
        return faq;
    }

    public void setFaq(List<FaqItem> faq) {
        this.faq = faq;
    }
}
