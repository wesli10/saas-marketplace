package br.com.saasfinder.reviews.domain;

import br.com.saasfinder.shared.id.IdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Opiniao de quem usa o produto (spec secao 4.1).
 *
 * <p>O produto entra como {@code UUID}, e nao como {@code @ManyToOne Product}:
 * {@code reviews} nao acessa o repositorio de {@code catalog} (constituicao secao
 * 4), e mapear a entidade do outro modulo seria exatamente isso por outro caminho.
 * Quem traduz slug em id e o {@code CatalogFacade}. A integridade continua no
 * banco, pela chave estrangeira da V7.
 *
 * <p>As transicoes de estado vivem aqui, e nao no servico (plano DT-03): e a
 * entidade que sabe de que estado ela pode sair.
 */
@Entity
@Table(name = "review")
public class Review {

    @Id
    private UUID id;

    private UUID productId;

    private short rating;
    private boolean wouldRecommend;
    private String title;
    private String body;

    /** Idioma em que foi escrita, nao o de quem le (spec secao 4.3). */
    private String locale;

    private String authorName;

    /** Nunca sai em rota publica. Serve ao link de confirmacao e a unicidade. */
    private String authorEmail;

    private String authorDomain;

    private boolean verified;

    @Enumerated(EnumType.STRING)
    private ReviewStatus status = ReviewStatus.PENDING_EMAIL;

    /** Motivo da decisao do curador. Nunca sai em rota publica (spec secao 4.1). */
    private String moderationNote;

    private String moderatedBy;
    private Instant moderatedAt;
    private Instant confirmedAt;
    private Instant publishedAt;

    @Generated(event = EventType.INSERT)
    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(insertable = false, updatable = false)
    private Instant updatedAt;

    protected Review() {}

    public Review(
            UUID productId,
            int rating,
            boolean wouldRecommend,
            String title,
            String body,
            String locale,
            String authorName,
            String authorEmail) {
        this.id = IdGenerator.novo();
        this.productId = productId;
        this.rating = (short) rating;
        this.wouldRecommend = wouldRecommend;
        this.title = title;
        this.body = body;
        this.locale = locale;
        this.authorName = authorName;
        // Minusculas na entrada, e nao so no indice: o e-mail volta para o link de
        // confirmacao e para a spec 006, e "Ana@Acme.com" e "ana@acme.com" sao a
        // mesma caixa postal.
        this.authorEmail = authorEmail == null ? null : authorEmail.trim().toLowerCase(Locale.ROOT);
        this.authorDomain = dominioDe(this.authorEmail);
    }

    /**
     * O que vem depois do @ (spec secao 4.5). E o que a tela mostra ao lado do
     * selo, e o que a lista de provedores gratuitos consulta.
     */
    public static String dominioDe(String email) {
        if (email == null) {
            return null;
        }
        int arroba = email.lastIndexOf('@');
        return arroba < 0 ? null : email.substring(arroba + 1);
    }

    /**
     * O dono do e-mail clicou no link (spec secao 4.2).
     *
     * <p>E aqui que o selo e decidido: {@code verified = confirmou && !gratuito}
     * (spec secao 4.5). Quem responde se o dominio e de provedor gratuito e a
     * lista versionada; a regra e desta entidade, porque o selo so existe junto
     * com a confirmacao.
     */
    public void confirmar(boolean dominioDeProvedorGratuito) {
        exigirEstado("confirmar", ReviewStatus.PENDING_EMAIL);

        this.confirmedAt = agora();
        this.verified = !dominioDeProvedorGratuito;
        this.status = ReviewStatus.PENDING_MODERATION;
    }

    /**
     * O curador aprovou (spec secao 5.2).
     *
     * <p>Recebe quem decidiu porque toda decisao de moderacao deixa rastro
     * (AC-21c): sem {@code moderated_by} e {@code moderated_at} nao ha como
     * demonstrar que houve analise, que e o que o Marco Civil art. 19 cobra.
     */
    public void publicar(String moderador) {
        exigirEstado("publicar", ReviewStatus.PENDING_MODERATION);
        exigirTexto(moderador, "moderador");

        Instant agora = agora();
        this.moderatedBy = moderador;
        this.moderatedAt = agora;
        this.publishedAt = agora;
        this.status = ReviewStatus.PUBLISHED;
    }

    /** O curador recusou. O motivo e obrigatorio (AC-08) e nunca sai em rota publica. */
    public void rejeitar(String motivo, String moderador) {
        exigirEstado("rejeitar", ReviewStatus.PENDING_MODERATION);
        exigirTexto(motivo, "motivo");
        exigirTexto(moderador, "moderador");

        this.moderationNote = motivo;
        this.moderatedBy = moderador;
        this.moderatedAt = agora();
        this.status = ReviewStatus.REJECTED;
    }

    /**
     * Registra a decisao de <b>manter</b> a review depois de uma contestacao (spec
     * secao 7.1, AC-21c).
     *
     * <p>Nao muda o estado: muda o registro. Sem uma linha dizendo quem analisou,
     * quando e por que, nao ha como demonstrar que houve analise - que e exatamente
     * o que o Marco Civil art. 19 cobra de quem publica conteudo de terceiro.
     *
     * <p>So faz sentido sobre review publicada: "decidimos manter" uma review que
     * nunca foi publicada seria uma mentira no registro de auditoria.
     */
    public void registrarDecisaoDeManter(String motivo, String moderador) {
        exigirEstado("manter", ReviewStatus.PUBLISHED);
        exigirTexto(motivo, "motivo");
        exigirTexto(moderador, "moderador");

        this.moderationNote = motivo;
        this.moderatedBy = moderador;
        this.moderatedAt = agora();
    }

    private void exigirEstado(String operacao, ReviewStatus aceito) {
        if (this.status != aceito) {
            throw new TransicaoInvalidaException(operacao, this.status);
        }
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " e obrigatorio");
        }
    }

    /** Postgres guarda timestamptz em microssegundos; truncar evita ida e volta assimetrica. */
    private static Instant agora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public short getRating() {
        return rating;
    }

    public boolean isWouldRecommend() {
        return wouldRecommend;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getLocale() {
        return locale;
    }

    public String getAuthorName() {
        return authorName;
    }

    public String getAuthorEmail() {
        return authorEmail;
    }

    public String getAuthorDomain() {
        return authorDomain;
    }

    public boolean isVerified() {
        return verified;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public String getModerationNote() {
        return moderationNote;
    }

    public String getModeratedBy() {
        return moderatedBy;
    }

    public Instant getModeratedAt() {
        return moderatedAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
