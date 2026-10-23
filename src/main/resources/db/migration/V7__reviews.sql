-- Spec 003 - reviews verificadas (secao 4.1 e 4.2), plano DT-08.
--
-- Uma migracao so para as duas tabelas: elas nascem juntas e review_verification
-- nao faz sentido sozinha. Migracao commitada e imutavel (constituicao secao 5):
-- correcao vira V8.

create table review (
    id              uuid        primary key,
    product_id      uuid        not null references product (id) on delete cascade,
    rating          smallint    not null,
    would_recommend boolean     not null,
    title           text        not null,
    body            text        not null,
    -- O idioma em que a review FOI ESCRITA, e nao o de quem le (spec secao 4.3).
    locale          text        not null,
    author_name     text        not null,
    -- Nunca sai em rota publica. Guardado em minusculas (spec secao 4.1); a
    -- unicidade usa lower() assim mesmo, porque indice que depende de CHECK para
    -- estar certo depende de o CHECK nunca ser removido.
    author_email    text        not null,
    author_domain   text        not null,
    -- confirmou o e-mail E o dominio nao e de provedor gratuito (spec secao 4.5).
    verified        boolean     not null default false,
    status          text        not null default 'PENDING_EMAIL',
    -- Motivo da decisao do curador. Obrigatorio ao rejeitar, usado tambem ao
    -- manter depois de uma contestacao (spec secao 7.1). Nunca sai em rota publica.
    moderation_note text,
    moderated_by    text,
    moderated_at    timestamptz,
    confirmed_at    timestamptz,
    published_at    timestamptz,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),

    constraint review_rating_faixa check (rating between 1 and 5),
    constraint review_title_tamanho check (char_length(title) between 1 and 120),
    -- O piso de 80 existe para barrar "bom" e "nao gostei" (spec secao 4.1).
    constraint review_body_tamanho check (char_length(body) between 80 and 2000),
    constraint review_locale_valido check (locale in ('pt-BR', 'en')),
    constraint review_author_name_tamanho check (char_length(author_name) between 1 and 80),
    constraint review_author_email_tamanho check (char_length(author_email) between 3 and 320),
    constraint review_author_email_minusculo check (author_email = lower(author_email)),
    constraint review_author_domain_tamanho check (char_length(author_domain) between 1 and 255),
    constraint review_moderation_note_tamanho check (
        moderation_note is null or char_length(moderation_note) between 1 and 300),
    constraint review_moderated_by_tamanho check (moderated_by is null or char_length(moderated_by) between 1 and 80),

    -- status como text com check, e nao enum nativo: e o que a V3 fez em
    -- product.status (plano DT-08). Duas convencoes no mesmo banco seria pior.
    constraint review_status_valido check (
        status in ('PENDING_EMAIL', 'PENDING_MODERATION', 'PUBLISHED', 'REJECTED')),

    -- Estado e carimbo andam juntos. Sao a rede de seguranca da maquina de
    -- estados que vive em Review (plano DT-03): se um caminho novo esquecer de
    -- gravar a data, o banco recusa em vez de deixar passar meio estado.
    constraint review_confirmada_tem_data check (status = 'PENDING_EMAIL' or confirmed_at is not null),
    constraint review_publicada_tem_data check (status <> 'PUBLISHED' or published_at is not null),
    constraint review_rejeitada_tem_motivo check (status <> 'REJECTED' or moderation_note is not null),
    constraint review_decidida_tem_autor check (
        status not in ('PUBLISHED', 'REJECTED') or (moderated_by is not null and moderated_at is not null)),
    -- Selo sem confirmacao nao existe (spec secao 4.1).
    constraint review_verificada_confirmou check (not verified or confirmed_at is not null)
);

-- Uma review por (produto, e-mail) - spec secao 4.1 e Q-05. Por e-mail, e nao por
-- dominio: por dominio travaria o @gmail.com inteiro em uma review por produto.
create unique index review_produto_email_uq on review (product_id, lower(author_email));

-- A consulta publica: as publicadas de um produto, da mais recente para a mais
-- antiga (spec secao 5.1). As tres colunas na ordem em que o WHERE/ORDER BY as usa.
create index review_leitura_idx on review (product_id, status, created_at desc);

create trigger review_set_updated_at
    before update on review
    for each row execute function set_updated_at();

-- Token de confirmacao (spec secao 4.2, plano DT-04).
create table review_verification (
    -- PK e FK ao mesmo tempo: uma linha por review. Reenviar substitui o token,
    -- em vez de criar um segundo valido - dois links dobram a janela de ataque
    -- sem ajudar ninguem.
    review_id  uuid        primary key references review (id) on delete cascade,
    -- SHA-256 em hex. O token em claro nunca e persistido: quem le o banco nao
    -- confirma review de terceiro.
    token_hash text        not null,
    expires_at timestamptz not null,
    used_at    timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint review_verification_token_hash_sha256 check (token_hash ~ '^[0-9a-f]{64}$')
);

-- A busca da confirmacao e pelo hash, nao pela review.
create unique index review_verification_token_hash_uq on review_verification (token_hash);

create trigger review_verification_set_updated_at
    before update on review_verification
    for each row execute function set_updated_at();
