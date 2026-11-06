-- Bitacora de auditoria (AuditEntry): quien cambio que y cuando.
-- Llego con fix/correcciones-pagos-ficha (#52), que se mergeo despues de V1.

CREATE TABLE `audit_entry` (
  `occurred_at` datetime(6) NOT NULL,
  `actor_id` binary(16) DEFAULT NULL,
  `entity_id` binary(16) DEFAULT NULL,
  `id` binary(16) NOT NULL,
  `entity_type` varchar(60) NOT NULL,
  `action` varchar(80) NOT NULL,
  `field` varchar(120) DEFAULT NULL,
  `new_value` varchar(2000) DEFAULT NULL,
  `old_value` varchar(2000) DEFAULT NULL,
  `actor_name` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_audit_entity` (`entity_type`,`entity_id`,`occurred_at`),
  KEY `ix_audit_actor` (`actor_id`,`occurred_at`),
  KEY `ix_audit_occurred` (`occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
