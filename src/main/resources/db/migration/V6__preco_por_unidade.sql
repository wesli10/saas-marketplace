-- Spec 001 - precisao de price_min (correcao descoberta no T26).
--
-- numeric(12,2) nao representa preco cobrado por unidade: a Twilio cobra
-- US$ 0,005 por mensagem no WhatsApp, e duas casas arredondariam para 0,01 - o
-- dobro. Como praticamente todo produto USAGE_BASED cobra abaixo de um centavo,
-- e a spec secao 4.2 exige price_min justamente para esse modelo, a coluna
-- precisava de mais casas ou o catalogo nunca conseguiria publicar um deles.
--
-- Quatro casas cobrem o que os provedores praticam hoje (milesimos de dolar) sem
-- transformar o campo num numero de contabilidade. A alteracao e alargamento: os
-- valores gravados com duas casas continuam validos e nao precisam de conversao.
alter table product
    alter column price_min type numeric(12, 4);
