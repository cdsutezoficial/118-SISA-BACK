-- Esquema inicial de SISAv2: las 45 entidades JPA (+ payment_concept_linked_concept)
-- tal como las construye Hibernate 6.6 sobre MySQL 8.4 con SisaMySQLDialect
-- (enums como VARCHAR), generado desde develop 03639e3 el 2026-11-05.
--
-- Esta migracion NO se edita. Cualquier cambio de esquema va en una migracion
-- nueva: V{yyyy}_{MM}_{dd}_{HHmm}__descripcion.sql (ver docs/guias/migraciones.md).
--
-- FOREIGN_KEY_CHECKS=0 porque las tablas se crean en orden alfabetico y algunas
-- llaves foraneas apuntan a tablas que aparecen despues.

SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `academic_division` (
  `code` varchar(12) NOT NULL,
  `director_person_id` binary(16) DEFAULT NULL,
  `id` binary(16) NOT NULL,
  `name` varchar(150) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `status` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK4c9kkj4oafpsjnematpeo7e8f` (`code`),
  UNIQUE KEY `UKajndhj2kk84wd3tgxdf83yxhc` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `academic_groups` (
  `max_capacity` int NOT NULL,
  `code` varchar(10) NOT NULL,
  `generation_id` binary(16) NOT NULL,
  `id` binary(16) NOT NULL,
  `period_id` binary(16) NOT NULL,
  `plan_level_id` binary(16) NOT NULL,
  `program_id` binary(16) NOT NULL,
  `shift` varchar(255) NOT NULL,
  `status` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_academic_groups_generation_code` (`generation_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `academic_period` (
  `end_date` date NOT NULL,
  `enrollment_end` date DEFAULT NULL,
  `enrollment_start` date DEFAULT NULL,
  `period_number` int NOT NULL,
  `period_year` int NOT NULL,
  `start_date` date NOT NULL,
  `id` binary(16) NOT NULL,
  `name` varchar(150) NOT NULL,
  `status` varchar(255) NOT NULL,
  `type` varchar(255) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `academic_plan` (
  `effective_from` date NOT NULL,
  `max_extraordinary_exams_per_period` int NOT NULL,
  `min_passing_grade` decimal(3,1) NOT NULL,
  `requires_social_service` bit(1) NOT NULL,
  `total_levels` int NOT NULL,
  `id` binary(16) NOT NULL,
  `program_id` binary(16) NOT NULL,
  `social_service_min_level_id` binary(16) DEFAULT NULL,
  `version` varchar(50) NOT NULL,
  `titulation_key` varchar(100) NOT NULL,
  `validity_period` varchar(100) NOT NULL,
  `status` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6txpgcg5o04fk5dl3us9pahqc` (`program_id`,`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `academic_program` (
  `continuity_program_id` binary(16) DEFAULT NULL,
  `division_id` binary(16) NOT NULL,
  `id` binary(16) NOT NULL,
  `code` varchar(41) NOT NULL,
  `dgp_code` varchar(100) DEFAULT NULL,
  `name` varchar(150) NOT NULL,
  `offer_name` varchar(200) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `level` varchar(255) NOT NULL,
  `modality` varchar(255) NOT NULL,
  `status` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKpflc561j60comj7llgkw3b2a3` (`offer_name`,`modality`),
  UNIQUE KEY `UKcij66jqtg5afqx9rwnufp5l7w` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `app_user` (
  `failed_login_attempts` int NOT NULL,
  `llave_mx_linked` bit(1) NOT NULL,
  `must_change_password` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `last_login_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `id` binary(16) NOT NULL,
  `person_id` binary(16) DEFAULT NULL,
  `llave_mx_subject` varchar(255) DEFAULT NULL,
  `password_hash` varchar(255) NOT NULL,
  `status` varchar(255) NOT NULL,
  `username` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK3k4cplvh82srueuttfkwnylq0` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `candidate` (
  `birth_date` date DEFAULT NULL,
  `has_children` bit(1) DEFAULT NULL,
  `is_enabled_for_induction` bit(1) NOT NULL,
  `is_first_choice` bit(1) NOT NULL,
  `llave_mx_verified` bit(1) NOT NULL,
  `monthly_family_income` decimal(12,2) DEFAULT NULL,
  `paid_at` datetime(6) DEFAULT NULL,
  `registered_at` datetime(6) NOT NULL,
  `selected_at` datetime(6) DEFAULT NULL,
  `admission_config_id` binary(16) NOT NULL,
  `birth_municipality_id` binary(16) DEFAULT NULL,
  `birth_state_id` binary(16) DEFAULT NULL,
  `ficha_payment_id` binary(16) DEFAULT NULL,
  `id` binary(16) NOT NULL,
  `outreach_channel_id` binary(16) DEFAULT NULL,
  `program_change_payment_id` binary(16) DEFAULT NULL,
  `selected_by` binary(16) DEFAULT NULL,
  `valid_period_id` binary(16) DEFAULT NULL,
  `curp` varchar(18) NOT NULL,
  `birth_country` varchar(100) DEFAULT NULL,
  `birth_foreign_municipality` varchar(255) DEFAULT NULL,
  `birth_foreign_state` varchar(255) DEFAULT NULL,
  `emergency_contact_phone` varchar(255) DEFAULT NULL,
  `first_name` varchar(255) NOT NULL,
  `folio` varchar(255) NOT NULL,
  `gender` varchar(255) DEFAULT NULL,
  `guardian_email` varchar(255) DEFAULT NULL,
  `guardian_name` varchar(255) DEFAULT NULL,
  `home_phone` varchar(255) DEFAULT NULL,
  `last_name1` varchar(255) NOT NULL,
  `last_name2` varchar(255) DEFAULT NULL,
  `marital_status` varchar(255) DEFAULT NULL,
  `mobile_phone` varchar(255) DEFAULT NULL,
  `nationality` varchar(255) DEFAULT NULL,
  `native_language` varchar(255) DEFAULT NULL,
  `personal_email` varchar(255) DEFAULT NULL,
  `status` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK7m9ind6a42iv2mw7hetbux63e` (`folio`),
  KEY `ix_candidate_curp` (`curp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `candidate_address` (
  `candidate_id` binary(16) NOT NULL,
  `municipality_id` binary(16) DEFAULT NULL,
  `state_id` binary(16) DEFAULT NULL,
  `exterior_number` varchar(255) NOT NULL,
  `foreign_municipality` varchar(255) DEFAULT NULL,
  `foreign_state` varchar(255) DEFAULT NULL,
  `interior_number` varchar(255) DEFAULT NULL,
  `locality` varchar(255) DEFAULT NULL,
  `neighborhood` varchar(255) DEFAULT NULL,
  `postal_code` varchar(255) NOT NULL,
  `street` varchar(255) NOT NULL,
  PRIMARY KEY (`candidate_id`),
  CONSTRAINT `FKmk95ujrhbelgtvn22uk96e3hs` FOREIGN KEY (`candidate_id`) REFERENCES `candidate` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `candidate_diversity_profile` (
  `belongs_to_lgbttiq_community` bit(1) NOT NULL,
  `is_afrodescendant` bit(1) NOT NULL,
  `parents_speak_indigenous_language` bit(1) NOT NULL,
  `self_identifies_afrodescendant` bit(1) NOT NULL,
  `self_identifies_indigenous` bit(1) NOT NULL,
  `self_identifies_non_binary` bit(1) NOT NULL,
  `speaks_indigenous_language` bit(1) NOT NULL,
  `candidate_id` binary(16) NOT NULL,
  `indigenous_language` varchar(255) DEFAULT NULL,
  `parents_indigenous_language` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`candidate_id`),
  CONSTRAINT `FKdvp1ks029wy6hei5sw8uk0qw1` FOREIGN KEY (`candidate_id`) REFERENCES `candidate` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `candidate_employment_info` (
  `is_employed` bit(1) NOT NULL,
  `monthly_income` decimal(12,2) DEFAULT NULL,
  `work_end_time` time(6) DEFAULT NULL,
  `work_start_time` time(6) DEFAULT NULL,
  `candidate_id` binary(16) NOT NULL,
  `company_name` varchar(255) DEFAULT NULL,
  `employment_type` varchar(255) DEFAULT NULL,
  `job_title` varchar(255) DEFAULT NULL,
  `work_phone` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`candidate_id`),
  CONSTRAINT `FKm48l8nbyax7x7sag6m4hiln0a` FOREIGN KEY (`candidate_id`) REFERENCES `candidate` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `candidate_health_profile` (
  `has_disability` bit(1) NOT NULL,
  `has_preexisting_condition` bit(1) NOT NULL,
  `candidate_id` binary(16) NOT NULL,
  `blood_type` varchar(255) DEFAULT NULL,
  `condition_description` varchar(255) DEFAULT NULL,
  `disability_description` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`candidate_id`),
  CONSTRAINT `FK29818whmtmppxkvr71ds7trjr` FOREIGN KEY (`candidate_id`) REFERENCES `candidate` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `candidate_high_school_background` (
  `cct_confirmed` bit(1) DEFAULT NULL,
  `gpa` decimal(4,2) DEFAULT NULL,
  `studied_in_mexico` bit(1) NOT NULL,
  `candidate_id` binary(16) NOT NULL,
  `school_municipality_id` binary(16) DEFAULT NULL,
  `school_state_id` binary(16) DEFAULT NULL,
  `school_type_id` binary(16) DEFAULT NULL,
  `academic_area` varchar(255) DEFAULT NULL,
  `cct` varchar(255) DEFAULT NULL,
  `foreign_country` varchar(255) DEFAULT NULL,
  `school_city` varchar(255) NOT NULL,
  `school_foreign_state` varchar(255) DEFAULT NULL,
  `school_name` varchar(255) DEFAULT NULL,
  `study_period` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`candidate_id`),
  CONSTRAINT `FKhnq158t4aawnixvpixhqmji9t` FOREIGN KEY (`candidate_id`) REFERENCES `candidate` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `candidate_program_change` (
  `credit_left` decimal(12,2) DEFAULT NULL,
  `new_amount` decimal(12,2) DEFAULT NULL,
  `paid_amount` decimal(12,2) DEFAULT NULL,
  `previous_amount` decimal(12,2) DEFAULT NULL,
  `to_pay` decimal(12,2) DEFAULT NULL,
  `was_paid` bit(1) NOT NULL,
  `changed_at` datetime(6) NOT NULL,
  `candidate_id` binary(16) NOT NULL,
  `changed_by` binary(16) DEFAULT NULL,
  `ficha_payment_id` binary(16) DEFAULT NULL,
  `from_config_id` binary(16) NOT NULL,
  `id` binary(16) NOT NULL,
  `new_payment_id` binary(16) DEFAULT NULL,
  `to_config_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_program_change_candidate` (`candidate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `checkout_attempt` (
  `amount` decimal(12,2) NOT NULL,
  `discount_amount` decimal(12,2) DEFAULT NULL,
  `original_amount` decimal(12,2) DEFAULT NULL,
  `closed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `benefit_id` binary(16) DEFAULT NULL,
  `candidate_id` binary(16) DEFAULT NULL,
  `id` binary(16) NOT NULL,
  `payment_id` binary(16) NOT NULL,
  `rate_id` binary(16) DEFAULT NULL,
  `student_id` binary(16) DEFAULT NULL,
  `close_reason` varchar(32) NOT NULL,
  `order_id` varchar(64) NOT NULL,
  `session_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK7jn5j84edtfhok2lseuro5lc1` (`order_id`),
  KEY `ix_checkout_attempt_payment` (`payment_id`,`close_reason`),
  KEY `ix_checkout_attempt_candidate` (`candidate_id`,`close_reason`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `generation` (
  `number` int NOT NULL,
  `id` binary(16) NOT NULL,
  `plan_id` binary(16) NOT NULL,
  `program_id` binary(16) NOT NULL,
  `start_period_id` binary(16) NOT NULL,
  `code` varchar(255) NOT NULL,
  `status` varchar(255) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `grade_scale` (
  `numeric_max` decimal(5,1) NOT NULL,
  `numeric_min` decimal(5,1) NOT NULL,
  `classification_id` binary(16) NOT NULL,
  `id` binary(16) NOT NULL,
  `plan_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKoy2fd10kwblbxjyfl30y4odny` (`plan_id`),
  CONSTRAINT `FKoy2fd10kwblbxjyfl30y4odny` FOREIGN KEY (`plan_id`) REFERENCES `academic_plan` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `grade_scale_entry` (
  `from_value` decimal(5,1) NOT NULL,
  `passed` bit(1) NOT NULL,
  `to_value` decimal(5,1) NOT NULL,
  `id` binary(16) NOT NULL,
  `scale_id` binary(16) NOT NULL,
  `description` varchar(255) NOT NULL,
  `letter` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKisnnsdux0aly5fb4fm2ixywol` (`scale_id`),
  CONSTRAINT `FKisnnsdux0aly5fb4fm2ixywol` FOREIGN KEY (`scale_id`) REFERENCES `grade_scale` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `high_school_type` (
  `id` binary(16) NOT NULL,
  `name` varchar(150) NOT NULL,
  `status` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_high_school_type_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `municipality` (
  `inegi_code` varchar(3) NOT NULL,
  `id` binary(16) NOT NULL,
  `state_id` binary(16) NOT NULL,
  `name` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK4els9vegju7plwg9ss32iqbl4` (`state_id`,`inegi_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `outreach_channel` (
  `id` binary(16) NOT NULL,
  `name` varchar(150) NOT NULL,
  `status` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_outreach_channel_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `password_reset_token` (
  `expires_at` datetime(6) NOT NULL,
  `used_at` datetime(6) DEFAULT NULL,
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `token_hash` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKanlf8vm14i1nn7pa6qlp0xa3w` (`token_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `payment` (
  `amount_due` decimal(12,2) DEFAULT NULL,
  `amount_paid` decimal(12,2) NOT NULL,
  `original_amount` decimal(12,2) DEFAULT NULL,
  `price_locked` bit(1) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `due_at` datetime(6) DEFAULT NULL,
  `benefit_id` binary(16) DEFAULT NULL,
  `candidate_id` binary(16) DEFAULT NULL,
  `generation_id` binary(16) DEFAULT NULL,
  `id` binary(16) NOT NULL,
  `period_id` binary(16) DEFAULT NULL,
  `program_id` binary(16) DEFAULT NULL,
  `rate_id` binary(16) DEFAULT NULL,
  `student_id` binary(16) DEFAULT NULL,
  `reference_number` varchar(40) DEFAULT NULL,
  `concept_code` varchar(50) NOT NULL,
  `origin` varchar(255) NOT NULL,
  `status` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_payment_charge_key` (`candidate_id`,`student_id`,`concept_code`,`period_id`,`generation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `payment_area` (
  `code` varchar(5) NOT NULL,
  `id` binary(16) NOT NULL,
  `name` varchar(150) NOT NULL,
  `description` text,
  `status` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_area_name` (`name`),
  UNIQUE KEY `uk_payment_area_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `payment_benefit` (
  `amount` decimal(12,2) DEFAULT NULL,
  `percent` decimal(5,2) DEFAULT NULL,
  `valid_from` date NOT NULL,
  `valid_to` date DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `candidate_id` binary(16) DEFAULT NULL,
  `generation_id` binary(16) DEFAULT NULL,
  `granted_by` binary(16) NOT NULL,
  `id` binary(16) NOT NULL,
  `payment_id` binary(16) DEFAULT NULL,
  `period_id` binary(16) DEFAULT NULL,
  `student_id` binary(16) DEFAULT NULL,
  `scope_concept_code` varchar(50) NOT NULL,
  `granted_by_name` varchar(150) NOT NULL,
  `reason` varchar(250) NOT NULL,
  `mode` varchar(255) NOT NULL,
  `status` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_benefit_anchor_concept` (`candidate_id`,`student_id`,`scope_concept_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `payment_concept` (
  `active_level` int DEFAULT NULL,
  `available_from` date DEFAULT NULL,
  `available_until` date DEFAULT NULL,
  `cost` decimal(12,2) DEFAULT NULL,
  `cost_external` decimal(12,2) DEFAULT NULL,
  `is_accumulable` bit(1) NOT NULL,
  `is_external` bit(1) NOT NULL,
  `is_multiconcept` bit(1) NOT NULL,
  `is_standalone` bit(1) NOT NULL,
  `level_number` int DEFAULT NULL,
  `max_per_period` int DEFAULT NULL,
  `max_per_student` int DEFAULT NULL,
  `quota_limit` int DEFAULT NULL,
  `requires_validation` bit(1) NOT NULL,
  `area_id` binary(16) DEFAULT NULL,
  `id` binary(16) NOT NULL,
  `code` varchar(255) NOT NULL,
  `description` text,
  `name` varchar(255) NOT NULL,
  `policies` text,
  `status` varchar(255) NOT NULL,
  `type` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKhyc4rvfpjumiukqraxnfxycwl` (`code`),
  UNIQUE KEY `uk_payment_concept_active_level` (`active_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `payment_concept_linked_concept` (
  `concept_id` binary(16) NOT NULL,
  `linked_concept_id` binary(16) NOT NULL,
  KEY `FKcbgm1cj2np21oi5gt54u3peml` (`concept_id`),
  CONSTRAINT `FKcbgm1cj2np21oi5gt54u3peml` FOREIGN KEY (`concept_id`) REFERENCES `payment_concept` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `payment_credit` (
  `amount` decimal(12,2) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `applied_to_payment_id` binary(16) DEFAULT NULL,
  `benefit_id` binary(16) DEFAULT NULL,
  `candidate_id` binary(16) DEFAULT NULL,
  `id` binary(16) NOT NULL,
  `origin_payment_id` binary(16) DEFAULT NULL,
  `student_id` binary(16) DEFAULT NULL,
  `status` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_credit_anchor` (`candidate_id`,`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `payment_entry` (
  `amount` decimal(12,2) NOT NULL,
  `is_physical` bit(1) NOT NULL,
  `paid_at` datetime(6) NOT NULL,
  `collected_by` binary(16) DEFAULT NULL,
  `id` binary(16) NOT NULL,
  `link_id` binary(16) DEFAULT NULL,
  `payment_id` binary(16) NOT NULL,
  `gateway` varchar(20) DEFAULT NULL,
  `receipt_number` varchar(40) DEFAULT NULL,
  `external_order_id` varchar(80) DEFAULT NULL,
  `reference` varchar(120) DEFAULT NULL,
  `collected_by_name` varchar(150) DEFAULT NULL,
  `channel` varchar(255) NOT NULL,
  `gateway_status` varchar(255) DEFAULT NULL,
  `method` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_entry_payment` (`payment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `payment_rate` (
  `amount` decimal(12,2) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `concept_id` binary(16) NOT NULL,
  `id` binary(16) NOT NULL,
  `period_id` binary(16) DEFAULT NULL,
  `program_id` binary(16) DEFAULT NULL,
  `level` varchar(255) DEFAULT NULL,
  `status` varchar(255) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `permission` (
  `id` binary(16) NOT NULL,
  `name` varchar(255) NOT NULL,
  `permission_key` varchar(255) NOT NULL,
  `status` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKa6ce7d623gvfanotmesph4skr` (`permission_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `person` (
  `birth_date` date DEFAULT NULL,
  `has_children` bit(1) DEFAULT NULL,
  `monthly_family_income` decimal(12,2) DEFAULT NULL,
  `birth_municipality_id` binary(16) DEFAULT NULL,
  `birth_state_id` binary(16) DEFAULT NULL,
  `id` binary(16) NOT NULL,
  `curp` varchar(18) NOT NULL,
  `birth_country` varchar(100) DEFAULT NULL,
  `birth_foreign_municipality` varchar(255) DEFAULT NULL,
  `birth_foreign_state` varchar(255) DEFAULT NULL,
  `emergency_contact_phone` varchar(255) DEFAULT NULL,
  `first_name` varchar(255) NOT NULL,
  `gender` varchar(255) DEFAULT NULL,
  `guardian_email` varchar(255) DEFAULT NULL,
  `guardian_name` varchar(255) DEFAULT NULL,
  `home_phone` varchar(255) DEFAULT NULL,
  `institutional_email` varchar(255) DEFAULT NULL,
  `last_name1` varchar(255) NOT NULL,
  `last_name2` varchar(255) DEFAULT NULL,
  `marital_status` varchar(255) DEFAULT NULL,
  `mobile_phone` varchar(255) DEFAULT NULL,
  `nationality` varchar(255) DEFAULT NULL,
  `native_language` varchar(255) DEFAULT NULL,
  `nss` varchar(255) DEFAULT NULL,
  `personal_email` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK80gpipnajvke2lwmtby6daupl` (`curp`),
  UNIQUE KEY `UK1ud34cwb95xr3txvwjvl1buna` (`institutional_email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `person_address` (
  `municipality_id` binary(16) DEFAULT NULL,
  `person_id` binary(16) NOT NULL,
  `state_id` binary(16) DEFAULT NULL,
  `exterior_number` varchar(255) NOT NULL,
  `foreign_municipality` varchar(255) DEFAULT NULL,
  `foreign_state` varchar(255) DEFAULT NULL,
  `interior_number` varchar(255) DEFAULT NULL,
  `locality` varchar(255) DEFAULT NULL,
  `neighborhood` varchar(255) DEFAULT NULL,
  `postal_code` varchar(255) NOT NULL,
  `street` varchar(255) NOT NULL,
  PRIMARY KEY (`person_id`),
  CONSTRAINT `FKnndfs0btabect8upo03uwgfxt` FOREIGN KEY (`person_id`) REFERENCES `person` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `person_diversity_profile` (
  `belongs_to_lgbttiq_community` bit(1) NOT NULL,
  `is_afrodescendant` bit(1) NOT NULL,
  `parents_speak_indigenous_language` bit(1) NOT NULL,
  `self_identifies_afrodescendant` bit(1) NOT NULL,
  `self_identifies_indigenous` bit(1) NOT NULL,
  `self_identifies_non_binary` bit(1) NOT NULL,
  `speaks_indigenous_language` bit(1) NOT NULL,
  `person_id` binary(16) NOT NULL,
  `indigenous_language` varchar(255) DEFAULT NULL,
  `parents_indigenous_language` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`person_id`),
  CONSTRAINT `FK5qtnr3n60smdjblbodibrnveb` FOREIGN KEY (`person_id`) REFERENCES `person` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `person_employment_info` (
  `is_employed` bit(1) NOT NULL,
  `monthly_income` decimal(12,2) DEFAULT NULL,
  `work_end_time` time(6) DEFAULT NULL,
  `work_start_time` time(6) DEFAULT NULL,
  `person_id` binary(16) NOT NULL,
  `company_name` varchar(255) DEFAULT NULL,
  `employment_type` varchar(255) DEFAULT NULL,
  `job_title` varchar(255) DEFAULT NULL,
  `work_phone` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`person_id`),
  CONSTRAINT `FKnmxwuy4jaeltis2keu7nvfib2` FOREIGN KEY (`person_id`) REFERENCES `person` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `person_health_profile` (
  `has_disability` bit(1) NOT NULL,
  `has_preexisting_condition` bit(1) NOT NULL,
  `person_id` binary(16) NOT NULL,
  `blood_type` varchar(255) DEFAULT NULL,
  `condition_description` varchar(255) DEFAULT NULL,
  `disability_description` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`person_id`),
  CONSTRAINT `FKosjfklblpcdl84p7e3j185uqa` FOREIGN KEY (`person_id`) REFERENCES `person` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `person_high_school_background` (
  `cct_confirmed` bit(1) DEFAULT NULL,
  `gpa` decimal(4,2) DEFAULT NULL,
  `studied_in_mexico` bit(1) NOT NULL,
  `person_id` binary(16) NOT NULL,
  `school_municipality_id` binary(16) DEFAULT NULL,
  `school_state_id` binary(16) DEFAULT NULL,
  `school_type_id` binary(16) DEFAULT NULL,
  `academic_area` varchar(255) DEFAULT NULL,
  `cct` varchar(255) DEFAULT NULL,
  `foreign_country` varchar(255) DEFAULT NULL,
  `school_city` varchar(255) NOT NULL,
  `school_foreign_state` varchar(255) DEFAULT NULL,
  `school_name` varchar(255) DEFAULT NULL,
  `study_period` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`person_id`),
  CONSTRAINT `FKea3hc7e5fosyonp7y8rrafxud` FOREIGN KEY (`person_id`) REFERENCES `person` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `plan_level` (
  `level_number` int NOT NULL,
  `id` binary(16) NOT NULL,
  `plan_id` binary(16) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `type` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKtpiump1sixjti0v9txfsrqvm7` (`plan_id`),
  CONSTRAINT `FKtpiump1sixjti0v9txfsrqvm7` FOREIGN KEY (`plan_id`) REFERENCES `academic_plan` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `postal_code_settlement` (
  `sepomex_settlement_id` varchar(4) NOT NULL,
  `postal_code` varchar(5) NOT NULL,
  `id` binary(16) NOT NULL,
  `municipality_id` binary(16) NOT NULL,
  `state_id` binary(16) NOT NULL,
  `locality_name` varchar(255) DEFAULT NULL,
  `settlement_name` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKfdlw61jeiv4wcwrgkkirrlht3` (`postal_code`,`sepomex_settlement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `program_admission_config` (
  `is_offered` bit(1) NOT NULL,
  `max_candidates` int NOT NULL,
  `closes_at` datetime(6) NOT NULL,
  `opens_at` datetime(6) NOT NULL,
  `id` binary(16) NOT NULL,
  `period_id` binary(16) NOT NULL,
  `program_id` binary(16) NOT NULL,
  `target_generation_id` binary(16) NOT NULL,
  `selection_status` varchar(255) NOT NULL,
  `status` varchar(255) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `refresh_token` (
  `expires_at` datetime(6) NOT NULL,
  `revoked_at` datetime(6) DEFAULT NULL,
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `token_hash` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKkdj16cltjxdksuyiosdhliveg` (`token_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `role` (
  `id` binary(16) NOT NULL,
  `description` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `role_key` varchar(255) NOT NULL,
  `status` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKkq6633upjks80igxj3rx1f93q` (`role_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `role_permission` (
  `id` binary(16) NOT NULL,
  `permission_id` binary(16) NOT NULL,
  `role_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKt49nxpqax9cveurs8f61sns2d` (`role_id`,`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `state` (
  `inegi_code` varchar(2) NOT NULL,
  `id` binary(16) NOT NULL,
  `name` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK76sp8qj5mv1uuk6rhnr08g6oh` (`inegi_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `subject` (
  `credits` int NOT NULL,
  `display_order` int NOT NULL,
  `evaluation_units` int NOT NULL,
  `is_retakeable` bit(1) NOT NULL,
  `weekly_hours` int NOT NULL,
  `classification_id` binary(16) NOT NULL,
  `id` binary(16) NOT NULL,
  `plan_id` binary(16) NOT NULL,
  `plan_level_id` binary(16) NOT NULL,
  `code` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `type` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK11enk5b3aepub88buj95spvtt` (`plan_level_id`),
  CONSTRAINT `FK11enk5b3aepub88buj95spvtt` FOREIGN KEY (`plan_level_id`) REFERENCES `plan_level` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `subject_classification` (
  `id` binary(16) NOT NULL,
  `code` varchar(20) NOT NULL,
  `name` varchar(150) NOT NULL,
  `status` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKmbxhhxe5uruyd9wb9np47bvqa` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_role` (
  `division_id` binary(16) DEFAULT NULL,
  `id` binary(16) NOT NULL,
  `role_id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `role_type` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
