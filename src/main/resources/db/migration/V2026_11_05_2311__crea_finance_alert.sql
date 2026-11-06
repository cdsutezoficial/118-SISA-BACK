-- Alertas de pago para Finanzas (FinanceAlert): pagos en linea que no cuadran.
-- Llego con fix/correcciones-pagos-ficha (#52), que se mergeo despues de V1.

CREATE TABLE `finance_alert` (
  `amount` decimal(12,2) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `last_seen_at` datetime(6) NOT NULL,
  `resolved_at` datetime(6) DEFAULT NULL,
  `candidate_id` binary(16) DEFAULT NULL,
  `id` binary(16) NOT NULL,
  `payment_id` binary(16) DEFAULT NULL,
  `resolved_by` binary(16) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `folio` varchar(40) DEFAULT NULL,
  `type` varchar(40) NOT NULL,
  `order_id` varchar(80) NOT NULL,
  `detail` varchar(500) DEFAULT NULL,
  `resolution_note` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_alert_type_order` (`type`,`order_id`),
  KEY `ix_alert_status` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
