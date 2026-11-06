-- Saldo a favor: external_order_id guarda la orden EVO de un cobro doble que se
-- volvio saldo; es unica para que la conciliacion no la acredite dos veces.
-- person_id lo declara la entidad pero hoy nadie lo llena (el saldo se agrupa
-- por CURP). Ambas nullable: solo se agrega.
-- Llego con fix/correcciones-pagos-ficha (#52), que se mergeo despues de V1.

ALTER TABLE `payment_credit`
  ADD COLUMN `person_id` binary(16) DEFAULT NULL,
  ADD COLUMN `external_order_id` varchar(80) DEFAULT NULL,
  ADD UNIQUE KEY `ux_credit_external_order` (`external_order_id`),
  ADD KEY `ix_credit_person` (`person_id`,`status`);
