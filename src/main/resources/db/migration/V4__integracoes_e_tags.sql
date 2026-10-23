-- Spec 001 - integracoes (secao 4.4) e tags (secao 4.5), com as duas N:N.

-- Nome de integracao nao se traduz (WhatsApp, HubSpot, Shopify), entao a coluna
-- e unica - ao contrario de tag, que e vocabulario editorial e precisa dos dois
-- idiomas.
create table integration (
    id         uuid        primary key,
    slug       text        not null,
    name       text        not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint integration_slug_kebab check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    constraint integration_name_tamanho check (char_length(name) between 1 and 80)
);

create unique index integration_slug_uq on integration (slug);

create table tag (
    id         uuid        primary key,
    slug       text        not null,
    name_pt    text        not null,
    name_en    text        not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint tag_slug_kebab check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    constraint tag_name_pt_tamanho check (char_length(name_pt) between 1 and 80),
    constraint tag_name_en_tamanho check (char_length(name_en) between 1 and 80)
);

create unique index tag_slug_uq on tag (slug);

-- Nas duas N:N o vinculo cai junto com o produto (CASCADE), mas segura a
-- exclusao do lado do vocabulario (RESTRICT): apagar uma integracao ainda em uso
-- sumiria com o vinculo sem ninguem perceber, e o vetor de busca depende dele.
create table product_integration (
    product_id     uuid not null references product (id) on delete cascade,
    integration_id uuid not null references integration (id) on delete restrict,

    primary key (product_id, integration_id)
);

create index product_integration_integracao_idx on product_integration (integration_id);

create table product_tag (
    product_id uuid not null references product (id) on delete cascade,
    tag_id     uuid not null references tag (id) on delete restrict,

    primary key (product_id, tag_id)
);

create index product_tag_tag_idx on product_tag (tag_id);

create trigger integration_set_updated_at
    before update on integration
    for each row execute function set_updated_at();

create trigger tag_set_updated_at
    before update on tag
    for each row execute function set_updated_at();
