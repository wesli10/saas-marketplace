package br.com.saasfinder.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/** Tag editorial (spec secao 4.5). Ao contrario da integracao, tem nome nos dois idiomas. */
@Entity
@Table(name = "tag")
public class Tag {

    @Id
    private UUID id;

    private String slug;
    private String namePt;
    private String nameEn;

    @Generated(event = EventType.INSERT)
    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(insertable = false, updatable = false)
    private Instant updatedAt;

    protected Tag() {}

    public Tag(String slug, String namePt, String nameEn) {
        this.id = br.com.saasfinder.shared.id.IdGenerator.novo();
        this.slug = slug;
        this.namePt = namePt;
        this.nameEn = nameEn;
    }

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
