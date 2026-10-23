-- Spec 001 - categorias (secao 4.1) e o lookup bilingue de slug (secao 4.1.1, desvio D-01).

-- Funcao compartilhada por todas as tabelas com updated_at.
create or replace function set_updated_at() returns trigger as $$
begin
    new.updated_at = now();
    return new;
end;
$$ language plpgsql;

create table category (
    id             uuid        primary key,
    slug_pt        text        not null,
    slug_en        text        not null,
    name_pt        text        not null,
    name_en        text        not null,
    description_pt text,
    description_en text,
    parent_id      uuid        references category (id) on delete restrict,
    sort_order     int         not null default 0,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now(),

    constraint category_slug_pt_kebab check (slug_pt ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    constraint category_slug_en_kebab check (slug_en ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    constraint category_name_pt_tamanho check (char_length(name_pt) between 1 and 80),
    constraint category_name_en_tamanho check (char_length(name_en) between 1 and 80),
    constraint category_description_pt_tamanho check (description_pt is null or char_length(description_pt) <= 500),
    constraint category_description_en_tamanho check (description_en is null or char_length(description_en) <= 500)
);

create unique index category_slug_pt_uq on category (slug_pt);
create unique index category_slug_en_uq on category (slug_en);
create index category_parent_ordem_idx on category (parent_id, sort_order);

-- Lookup de slug (desvio D-01).
--
-- Unicidade por coluna nao basta: nada impediria o slug_pt de uma categoria
-- colidir com o slug_en de outra, e GET /categories/{slug} (AC-04) ficaria
-- ambiguo. Com o slug como PK aqui, a colisao vira erro de banco, e a rota
-- resolve a categoria numa consulta so.
create table category_slug (
    slug        text primary key,
    category_id uuid not null references category (id) on delete cascade,
    locale      text not null,

    constraint category_slug_locale_valido check (locale in ('pt-BR', 'en'))
);

create index category_slug_categoria_idx on category_slug (category_id);

-- Profundidade maxima 2: categoria -> subcategoria (spec secao 4.1).
create or replace function category_guard_profundidade() returns trigger as $$
begin
    if new.parent_id is null then
        return new;
    end if;

    if new.parent_id = new.id then
        raise exception 'categoria nao pode ser pai de si mesma' using errcode = '23514';
    end if;

    if exists (select 1 from category where id = new.parent_id and parent_id is not null) then
        raise exception 'profundidade maxima de categoria e 2: % ja e uma subcategoria', new.parent_id
            using errcode = '23514';
    end if;

    -- Sem esta guarda daria para criar a neta pelo outro lado: bastaria pegar uma
    -- categoria que ja tem filhas e transforma-la em subcategoria.
    if exists (select 1 from category where parent_id = new.id) then
        raise exception 'categoria com subcategorias nao pode virar subcategoria'
            using errcode = '23514';
    end if;

    return new;
end;
$$ language plpgsql;

create trigger category_depth_guard
    before insert or update of parent_id on category
    for each row execute function category_guard_profundidade();

-- Slug e imutavel apos a criacao (spec secao 4.1): a URL localizada e o que o
-- SEO indexa, e trocar o slug quebraria o link.
create or replace function category_guard_slug_imutavel() returns trigger as $$
begin
    if new.slug_pt is distinct from old.slug_pt or new.slug_en is distinct from old.slug_en then
        raise exception 'slug de categoria e imutavel apos a criacao' using errcode = '23514';
    end if;
    return new;
end;
$$ language plpgsql;

create trigger category_slug_imutavel
    before update of slug_pt, slug_en on category
    for each row execute function category_guard_slug_imutavel();

-- Mantem o lookup em dia. So no INSERT: o UPDATE de slug e barrado pelo trigger
-- acima, e o DELETE e coberto pelo ON DELETE CASCADE da FK.
create or replace function category_sync_slugs() returns trigger as $$
begin
    insert into category_slug (slug, category_id, locale) values (new.slug_pt, new.id, 'pt-BR');

    -- Quando os dois idiomas usam o mesmo slug (ex.: "chatbots"), grava uma linha
    -- so - senao a PK acusaria colisao da categoria com ela mesma.
    if new.slug_en is distinct from new.slug_pt then
        insert into category_slug (slug, category_id, locale) values (new.slug_en, new.id, 'en');
    end if;

    return null;
end;
$$ language plpgsql;

create trigger category_slug_sync
    after insert on category
    for each row execute function category_sync_slugs();

create trigger category_set_updated_at
    before update on category
    for each row execute function set_updated_at();
