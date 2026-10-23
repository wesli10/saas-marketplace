package br.com.saasfinder.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

/**
 * Ficha de um produto SaaS (spec secao 4.2).
 *
 * <p>As colunas search_vector_pt e search_vector_en existem na tabela e nao sao
 * mapeadas de proposito: sao mantidas por trigger (DT-06) e do tipo tsvector, que
 * o JPA nao tem por que conhecer.
 */
@Entity
@Table(name = "product")
public class Product {

    @Id
    private UUID id;

    private String slug;
    private String name;
    private String taglinePt;
    private String taglineEn;
    private String descriptionPt;
    private String descriptionEn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    private String websiteUrl;
    private String logoUrl;

    @Enumerated(EnumType.STRING)
    private PricingModel pricingModel;

    private BigDecimal priceMin;

    @JdbcTypeCode(SqlTypes.CHAR)
    private String priceCurrency;

    @Enumerated(EnumType.STRING)
    private PricePeriod pricePeriod;

    private boolean hasFreeTrial;
    private boolean hasFreePlan;

    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<String> supportedLanguages = List.of();

    @JdbcTypeCode(SqlTypes.CHAR)
    private String hqCountry;

    @Enumerated(EnumType.STRING)
    private ProductStatus status = ProductStatus.DRAFT;

    private Instant publishedAt;

    /** De onde veio o dado. Nunca exposto publicamente (spec secao 4.2). */
    private String sourceNote;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "product_integration",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "integration_id"))
    private Set<Integration> integrations = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "product_tag",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    @Generated(event = EventType.INSERT)
    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(insertable = false, updatable = false)
    private Instant updatedAt;

    protected Product() {}

    public Product(
            String slug,
            String name,
            Category category,
            String taglinePt,
            String taglineEn,
            String descriptionPt,
            String descriptionEn,
            String websiteUrl,
            PricingModel pricingModel,
            String sourceNote) {
        this.id = br.com.saasfinder.shared.id.IdGenerator.novo();
        this.slug = slug;
        this.name = name;
        this.category = category;
        this.taglinePt = taglinePt;
        this.taglineEn = taglineEn;
        this.descriptionPt = descriptionPt;
        this.descriptionEn = descriptionEn;
        this.websiteUrl = websiteUrl;
        this.pricingModel = pricingModel;
        this.sourceNote = sourceNote;
    }

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTaglinePt() {
        return taglinePt;
    }

    public void setTaglinePt(String taglinePt) {
        this.taglinePt = taglinePt;
    }

    public String getTaglineEn() {
        return taglineEn;
    }

    public void setTaglineEn(String taglineEn) {
        this.taglineEn = taglineEn;
    }

    public String getDescriptionPt() {
        return descriptionPt;
    }

    public void setDescriptionPt(String descriptionPt) {
        this.descriptionPt = descriptionPt;
    }

    public String getDescriptionEn() {
        return descriptionEn;
    }

    public void setDescriptionEn(String descriptionEn) {
        this.descriptionEn = descriptionEn;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public void setWebsiteUrl(String websiteUrl) {
        this.websiteUrl = websiteUrl;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public PricingModel getPricingModel() {
        return pricingModel;
    }

    public void setPricingModel(PricingModel pricingModel) {
        this.pricingModel = pricingModel;
    }

    public BigDecimal getPriceMin() {
        return priceMin;
    }

    public void setPriceMin(BigDecimal priceMin) {
        this.priceMin = priceMin;
    }

    public String getPriceCurrency() {
        return priceCurrency;
    }

    public void setPriceCurrency(String priceCurrency) {
        this.priceCurrency = priceCurrency;
    }

    public PricePeriod getPricePeriod() {
        return pricePeriod;
    }

    public void setPricePeriod(PricePeriod pricePeriod) {
        this.pricePeriod = pricePeriod;
    }

    public boolean isHasFreeTrial() {
        return hasFreeTrial;
    }

    public void setHasFreeTrial(boolean hasFreeTrial) {
        this.hasFreeTrial = hasFreeTrial;
    }

    public boolean isHasFreePlan() {
        return hasFreePlan;
    }

    public void setHasFreePlan(boolean hasFreePlan) {
        this.hasFreePlan = hasFreePlan;
    }

    public List<String> getSupportedLanguages() {
        return supportedLanguages;
    }

    public void setSupportedLanguages(List<String> supportedLanguages) {
        this.supportedLanguages = supportedLanguages;
    }

    public String getHqCountry() {
        return hqCountry;
    }

    public void setHqCountry(String hqCountry) {
        this.hqCountry = hqCountry;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public void setStatus(ProductStatus status) {
        this.status = status;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getSourceNote() {
        return sourceNote;
    }

    public void setSourceNote(String sourceNote) {
        this.sourceNote = sourceNote;
    }

    public Set<Integration> getIntegrations() {
        return integrations;
    }

    public Set<Tag> getTags() {
        return tags;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
