-- =====================================================================
-- Una caja abierta por origen.
--   * LOCAL: la caja de la PC del local.
--   * PROD : la caja de la web (venta de contingencia, ver V101 en db/cloud).
-- Antes solo podia haber una caja abierta en total; ahora la del local y la de
-- la web son independientes, cada una con su propio arqueo.
-- =====================================================================
drop index if exists uq_caja_abierta;
create unique index uq_caja_abierta_origen on cajas (origen) where estado = 'ABIERTA';
