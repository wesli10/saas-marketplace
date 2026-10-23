package br.com.saasfinder.catalog.domain;

/** Item da FAQ curada, serializado dentro do jsonb de product_content (spec secao 4.3). */
public record FaqItem(String question, String answer) {}
