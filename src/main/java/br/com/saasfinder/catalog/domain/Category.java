package br.com.saasfinder.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/** Categoria do catalogo (spec secao 4.1). Profundidade maxima 2, garantida por trigger. */
@Entity
@Table(name = "category")
public class Category {

    @Id
    private UUID id;

    private String slugPt;
    private String slugEn;
    private String namePt;
    private String nameEn;
    private String descriptionPt;
    private String descriptionEn;

    /** So o pai e mapeado: a arvore e montada a partir de uma lista plana, evitando N+1. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    private int sortOrder;

    @Generated(event = EventType.INSERT)
    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(insertable = false, updatable = false)
    private Instant updatedAt;

    protected Category() {}

    public Category(String slugPt, String slugEn, String namePt, String nameEn, Category parent) {
        this.id = br.com.saasfinder.shared.id.IdGenerator.novo();
        this.slugPt = slugPt;
        this.slugEn = slugEn;
        this.namePt = namePt;
        this.nameEn = nameEn;
        this.parent = parent;
    }

    public UUID getId() {
        return id;
    }

    public String getSlugPt() {
        return slugPt;
    }

    public String getSlugEn() {
        return slugEn;
    }

    public String getNamePt() {
        return namePt;
    }

    public void setNamePt(String namePt) {
        this.namePt = namePt;
    }

    public String getNameEn() {
        return nameEn;
    }

    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
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

    public Category getParent() {
        return parent;
    }

    /** Sem setter de slug: ele e imutavel apos a criacao (spec secao 4.1). */
    public void setParent(Category parent) {
        this.parent = parent;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
