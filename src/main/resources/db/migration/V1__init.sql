-- Spec 001 - catalogo core.
--
-- Migracao imutavel depois de commitada (constituicao secao 5): correcao vira V2.
-- Nesta tarefa (001-T04) ela so habilita as extensoes; as tabelas entram nas
-- tarefas T05 a T08.

-- Busca sem sensibilidade a acento (AC-11).
create extension if not exists unaccent;

-- Similaridade por trigrama, para tolerar erro de digitacao no nome (AC-12).
create extension if not exists pg_trgm;

-- Habilitada agora e ainda sem uso: nenhuma tabela desta fatia tem embedding.
-- Fica pronta para a spec 005-assistente-rag (spec secao 2).
create extension if not exists vector;
