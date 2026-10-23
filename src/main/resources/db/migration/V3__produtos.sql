-- Spec 001 - produto (secao 4.2) e conteudo curado por idioma (secao 4.3).

-- Auxiliar para os limites de tamanho de item dentro de arrays de texto.
-- CHECK nao aceita subconsulta, entao a agregacao precisa vir de uma funcao
-- imutavel.
create or replace function texto_mais_longo(itens text[]) returns int
    language sql immutable strict as
$$ select coalesce(max(char_length(item)), 0) from unnest(itens) as item $$;

-- Idem: valida que todo item do array e um codigo ISO 639-1 (duas minusculas).
create or replace function todos_idiomas_iso639(codigos text[]) returns boolean
    language sql immutable strict as
$$ select bool_and(codigo ~ '^[a-z]{2}$') from unnest(codigos) as codigo $$;

create table product (
    id                  uuid           primary key,
    slug                text           not null,
    name                text           not null,
    tagline_pt          text           not null,
    tagline_en          text           not null,
    description_pt      text           not null,
    description_en      text           not null,
    category_id         uuid           not null references category (id) on delete restrict,
    website_url         text           not null,
    logo_url            text,
    pricing_model       text           not null,
    price_min           numeric(12, 2),
    price_currency      char(3),
    price_period        text,
    has_free_trial      boolean        not null default false,
    has_free_plan       boolean        not null default false,
    supported_languages text[]         not null default '{}',
    hq_country          char(2),
    status              text           not null default 'DRAFT',
    published_at        timestamptz,
    -- De onde veio o dado (URL da pricing page e data da coleta). Nunca exposto
    -- publicamente (spec secao 4.2).
    source_note         text           not null,
    created_at          timestamptz    not null default now(),
    updated_at          timestamptz    not null default now(),

    constraint product_slug_kebab check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    constraint product_name_tamanho check (char_length(name) between 1 and 120),
    constraint product_tagline_pt_tamanho check (char_length(tagline_pt) between 1 and 160),
    constraint product_tagline_en_tamanho check (char_length(tagline_en) between 1 and 160),
    constraint product_description_pt_tamanho check (char_length(description_pt) between 1 and 4000),
    constraint product_description_en_tamanho check (char_length(description_en) between 1 and 4000),
    constraint product_website_url_http check (website_url ~ '^https?://'),
    constraint product_logo_url_http check (logo_url is null or logo_url ~ '^https?://'),

    constraint product_pricing_model_valido check (
        pricing_model in ('FREE', 'FREEMIUM', 'SUBSCRIPTION', 'USAGE_BASED', 'ONE_TIME', 'CONTACT_SALES')),
    constraint product_price_period_valido check (
        price_period is null or price_period in ('MONTH', 'YEAR', 'ONE_TIME')),
    constraint product_status_valido check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED')),

    -- Preco sem moeda nao significa nada; isso vale em qualquer estado.
    constraint product_preco_exige_moeda check (price_min is null or price_currency is not null),
    constraint product_price_min_nao_negativo check (price_min is null or price_min >= 0),
    constraint product_price_currency_iso4217 check (price_currency is null or price_currency ~ '^[A-Z]{3}$'),
    constraint product_hq_country_iso3166 check (hq_country is null or hq_country ~ '^[A-Z]{2}$'),
    constraint product_idiomas_iso639 check (
        cardinality(supported_languages) = 0 or todos_idiomas_iso639(supported_languages)),

    -- Publicado sempre tem data de publicacao.
    constraint product_publicado_tem_data check (status <> 'PUBLISHED' or published_at is not null)
);

-- Deliberadamente NAO existe um CHECK exigindo price_min para SUBSCRIPTION,
-- USAGE_BASED e ONE_TIME, nem exigindo ao menos um supported_language (desvio
-- D-04). Um CHECK desses impediria salvar rascunho incompleto, que e o fluxo
-- normal do curador (spec secao 3). Ambas as regras sao verificadas na
-- publicacao e devolvem 422 listando as pendencias (AC-19).

create unique index product_slug_uq on product (slug);
create index product_categoria_idx on product (category_id);
create index product_status_idx on product (status);
create index product_price_min_idx on product (price_min) where price_min is not null;

-- Slug e imutavel depois de publicado (spec secao 4.2). O admin transforma esta
-- recusa em 409 (AC-20); aqui fica a rede de seguranca.
create or replace function product_guard_slug_imutavel() returns trigger as $$
begin
    if old.status = 'PUBLISHED' and new.slug is distinct from old.slug then
        raise exception 'slug de produto publicado e imutavel' using errcode = '23514';
    end if;
    return new;
end;
$$ language plpgsql;

create trigger product_slug_imutavel
    before update of slug on product
    for each row execute function product_guard_slug_imutavel();

create trigger product_set_updated_at
    before update on product
    for each row execute function set_updated_at();

-- Conteudo longo, um registro por idioma (spec secao 4.3).
create table product_content (
    product_id uuid not null references product (id) on delete cascade,
    locale     text not null,
    pros       text[] not null default '{}',
    cons       text[] not null default '{}',
    best_for   text,
    faq        jsonb  not null default '[]'::jsonb,

    primary key (product_id, locale),

    constraint product_content_locale_valido check (locale in ('pt-BR', 'en')),
    constraint product_content_pros_limite check (
        cardinality(pros) <= 8 and texto_mais_longo(pros) <= 200),
    constraint product_content_cons_limite check (
        cardinality(cons) <= 8 and texto_mais_longo(cons) <= 200),
    constraint product_content_best_for_tamanho check (best_for is null or char_length(best_for) <= 300),
    constraint product_content_faq_array check (jsonb_typeof(faq) = 'array'),
    constraint product_content_faq_limite check (jsonb_array_length(faq) <= 10)
);
