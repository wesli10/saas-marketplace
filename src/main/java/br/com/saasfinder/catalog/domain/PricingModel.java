package br.com.saasfinder.catalog.domain;

/** Modelo de cobranca do produto (spec secao 4.2). */
public enum PricingModel {
    FREE,
    FREEMIUM,
    SUBSCRIPTION,
    USAGE_BASED,
    ONE_TIME,
    CONTACT_SALES;

    /** Modelos em que a publicacao exige price_min preenchido (spec secao 5.2). */
    public boolean exigePreco() {
        return this == SUBSCRIPTION || this == USAGE_BASED || this == ONE_TIME;
    }
}
