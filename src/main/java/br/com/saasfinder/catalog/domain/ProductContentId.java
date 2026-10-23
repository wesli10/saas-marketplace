package br.com.saasfinder.catalog.domain;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Chave composta de product_content: produto mais idioma (spec secao 4.3). */
@Embeddable
public class ProductContentId implements Serializable {

    private UUID productId;
    private String locale;

    protected ProductContentId() {}

    public ProductContentId(UUID productId, String locale) {
        this.productId = productId;
        this.locale = locale;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getLocale() {
        return locale;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof ProductContentId id)) {
            return false;
        }
        return Objects.equals(productId, id.productId) && Objects.equals(locale, id.locale);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, locale);
    }
}
