-- Spec 001 - vetores de busca (secao 4.6).
--
-- Por que trigger e nao coluna gerada (DT-06): o vetor inclui os nomes das tags
-- e integracoes ligadas (peso D), e GENERATED ALWAYS so enxerga a propria linha.

-- unaccent() e declarada STABLE, nao IMMUTABLE, porque depende do dicionario
-- carregado - e por isso nao pode entrar num indice. Fixando o dicionario de
-- forma explicita, a chamada passa a ser deterministica e indexavel. Truque
-- padrao do Postgres para busca sem acento.
create or replace function f_unaccent(text) returns text
    language sql immutable strict parallel safe as
$$ select public.unaccent('public.unaccent'::regdictionary, $1) $$;

alter table product
    add column search_vector_pt tsvector,
    add column search_vector_en tsvector;

-- Recalcula os dois vetores de um produto. Pesos da spec secao 4.6:
-- A = name, B = tagline, C = description, D = nomes de tags e integracoes.
create or replace function catalog_refresh_product_search_vector(p_product_id uuid)
    returns void language plpgsql as
$$
declare
    v_termos_pt text;
    v_termos_en text;
begin
    -- Cada vetor recebe os termos do seu idioma. Misturar os dois faria a busca em
    -- portugues casar com o nome em ingles da tag, poluindo o ranking do idioma
    -- pedido - e deixaria um rename de name_pt sem efeito visivel.
    -- Nome de integracao nao se traduz, entao entra nos dois.
    select coalesce(string_agg(termo, ' '), '')
      into v_termos_pt
      from (
          select t.name_pt as termo
            from product_tag pt join tag t on t.id = pt.tag_id
           where pt.product_id = p_product_id
          union all
          select i.name
            from product_integration pi join integration i on i.id = pi.integration_id
           where pi.product_id = p_product_id
      ) as termos;

    select coalesce(string_agg(termo, ' '), '')
      into v_termos_en
      from (
          select t.name_en as termo
            from product_tag pt join tag t on t.id = pt.tag_id
           where pt.product_id = p_product_id
          union all
          select i.name
            from product_integration pi join integration i on i.id = pi.integration_id
           where pi.product_id = p_product_id
      ) as termos;

    update product p
       set search_vector_pt =
               setweight(to_tsvector('portuguese', f_unaccent(coalesce(p.name, ''))), 'A')
            || setweight(to_tsvector('portuguese', f_unaccent(coalesce(p.tagline_pt, ''))), 'B')
            || setweight(to_tsvector('portuguese', f_unaccent(coalesce(p.description_pt, ''))), 'C')
            || setweight(to_tsvector('portuguese', f_unaccent(v_termos_pt)), 'D'),
           search_vector_en =
               setweight(to_tsvector('english', f_unaccent(coalesce(p.name, ''))), 'A')
            || setweight(to_tsvector('english', f_unaccent(coalesce(p.tagline_en, ''))), 'B')
            || setweight(to_tsvector('english', f_unaccent(coalesce(p.description_en, ''))), 'C')
            || setweight(to_tsvector('english', f_unaccent(v_termos_en)), 'D')
     where p.id = p_product_id;
end;
$$;

-- 1) O proprio produto. O UPDATE feito acima nao reentra aqui: ele so mexe nas
-- colunas de vetor, e UPDATE OF dispara apenas quando a coluna listada aparece
-- no SET.
create or replace function product_refresh_search() returns trigger as $$
begin
    perform catalog_refresh_product_search_vector(new.id);
    return null;
end;
$$ language plpgsql;

create trigger product_search_vector_sync
    after insert or update of name, tagline_pt, tagline_en, description_pt, description_en on product
    for each row execute function product_refresh_search();

-- 2 e 3) Vinculos de tag e integracao. Em AFTER DELETE o NEW nao existe, dai o
-- desvio por TG_OP em vez de coalesce(new.*, old.*).
create or replace function produto_vinculo_refresh_search() returns trigger as $$
begin
    if tg_op = 'DELETE' then
        perform catalog_refresh_product_search_vector(old.product_id);
    else
        perform catalog_refresh_product_search_vector(new.product_id);
    end if;
    return null;
end;
$$ language plpgsql;

create trigger product_tag_search_vector_sync
    after insert or delete on product_tag
    for each row execute function produto_vinculo_refresh_search();

create trigger product_integration_search_vector_sync
    after insert or delete on product_integration
    for each row execute function produto_vinculo_refresh_search();

-- 4 e 5) Renomear um item do vocabulario reflete em todo produto ligado a ele.
create or replace function tag_refresh_produtos() returns trigger as $$
declare
    v_produto uuid;
begin
    for v_produto in select product_id from product_tag where tag_id = new.id loop
        perform catalog_refresh_product_search_vector(v_produto);
    end loop;
    return null;
end;
$$ language plpgsql;

create trigger tag_search_vector_sync
    after update of name_pt, name_en on tag
    for each row execute function tag_refresh_produtos();

create or replace function integration_refresh_produtos() returns trigger as $$
declare
    v_produto uuid;
begin
    for v_produto in select product_id from product_integration where integration_id = new.id loop
        perform catalog_refresh_product_search_vector(v_produto);
    end loop;
    return null;
end;
$$ language plpgsql;

create trigger integration_search_vector_sync
    after update of name on integration
    for each row execute function integration_refresh_produtos();

create index product_search_pt_idx on product using gin (search_vector_pt);
create index product_search_en_idx on product using gin (search_vector_en);

-- A spec pede indice trigram em name. Ele e criado sobre a forma normalizada
-- porque a consulta do fallback compara minusculas e sem acento - indexar o
-- name cru deixaria o indice inutil para essa comparacao.
create index product_name_trgm_idx on product using gin (lower(f_unaccent(name)) gin_trgm_ops);

-- Produtos ja existentes (nenhum, nesta altura) ficariam com vetor nulo.
select catalog_refresh_product_search_vector(id) from product;
