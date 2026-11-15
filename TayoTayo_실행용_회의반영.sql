-- Meeting-updated MySQL execution schema. Target: tayotayo_dev.
-- Full schema initializer for an empty development database; not an ALTER migration.
-- Direction: PICK_UP = morning to institution; DROP_OFF = afternoon from institution.
-- Seat assignment and belt telemetry are intentionally omitted.

-- Meeting-updated TayoTayo schema for MySQL 8.0.13+
-- Target database: tayotayo_dev
-- The original ERD export contained primary keys only. Foreign keys below are inferred from column names/comments.
-- PostgreSQL-like types converted: timestamptz->DATETIME(3), uuid->CHAR(36), bytea->BLOB,
-- inet->VARCHAR(45), jsonb->JSON, bigint[]->JSON.
-- DATETIME/default timestamps use UTC session time. ON DELETE RESTRICT preserves history; prefer soft deletion.

CREATE DATABASE IF NOT EXISTS `tayotayo_dev`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
USE `tayotayo_dev`;
SET time_zone = '+00:00';

CREATE TABLE `organizations` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`name`	varchar(100)	NOT NULL,
	`org_type`	varchar(20)	NOT NULL	DEFAULT 'OTHER'	COMMENT 'DAYCARE / KINDERGARTEN / ACADEMY / SCHOOL / OTHER',
	`phone`	varchar(30)	NULL,
	`address`	varchar(255)	NULL,
	`latitude`	numeric(9,6)	NULL,
	`longitude`	numeric(9,6)	NULL,
	`timezone`	varchar(40)	NOT NULL	DEFAULT 'Asia/Seoul',
	`status`	varchar(20)	NOT NULL	DEFAULT 'ACTIVE'	COMMENT 'ACTIVE / SUSPENDED / CLOSED',
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`updated_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`deleted_at`	DATETIME(3)	NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `users` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`email`	varchar(255)	NOT NULL,
	`password_hash`	varchar(255)	NULL,
	`name`	varchar(50)	NOT NULL,
	`phone_enc`	BLOB	NULL,
	`email_verified_at`	DATETIME(3)	NULL,
	`status`	varchar(20)	NOT NULL	DEFAULT 'ACTIVE'	COMMENT 'ACTIVE / LOCKED / WITHDRAWN',
	`last_login_at`	DATETIME(3)	NULL,
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`updated_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`withdrawn_at`	DATETIME(3)	NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `auth_sessions` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`user_id`	bigint	NOT NULL,
	`client_type`	varchar(20)	NOT NULL	COMMENT 'GUARDIAN_APP / ADMIN_WEB / TEACHER_APP',
	`refresh_token_hash`	char(64)	NOT NULL,
	`device_label`	varchar(100)	NULL,
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`last_used_at`	DATETIME(3)	NULL,
	`expires_at`	DATETIME(3)	NOT NULL,
	`revoked_at`	DATETIME(3)	NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `email_verifications` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`user_id`	bigint	NULL,
	`email`	varchar(255)	NOT NULL,
	`purpose`	varchar(20)	NOT NULL	COMMENT 'SIGN_UP / EMAIL_CHANGE / PASSWORD_RESET',
	`code_hash`	char(64)	NOT NULL,
	`attempt_count`	smallint	NOT NULL	DEFAULT 0,
	`expires_at`	DATETIME(3)	NOT NULL,
	`consumed_at`	DATETIME(3)	NULL,
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `policy_documents` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`policy_type`	varchar(30)	NOT NULL	COMMENT 'TERMS / PRIVACY / LOCATION / CHILD_INFO / PUSH / MARKETING',
	`version`	varchar(20)	NOT NULL,
	`title`	varchar(200)	NOT NULL,
	`content_url`	varchar(500)	NULL,
	`is_required`	TINYINT(1)	NOT NULL	DEFAULT true,
	`effective_at`	DATETIME(3)	NOT NULL,
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user_consents` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`user_id`	bigint	NOT NULL,
	`policy_document_id`	bigint	NOT NULL,
	`child_id`	bigint	NULL,
	`agreed_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`withdrawn_at`	DATETIME(3)	NULL,
	`agreed_ip`	VARCHAR(45)	NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `org_staff` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`org_id`	bigint	NOT NULL,
	`user_id`	bigint	NULL,
	`name`	varchar(50)	NOT NULL,
	`phone_enc`	BLOB	NULL,
	`staff_role`	varchar(20)	NOT NULL	COMMENT 'ADMIN / TEACHER / DRIVER',
	`web_role`	varchar(20)	NULL	COMMENT 'OWNER / MANAGER / VIEWER',
	`status`	varchar(20)	NOT NULL	DEFAULT 'ACTIVE'	COMMENT 'ACTIVE / INACTIVE',
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`updated_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`deleted_at`	DATETIME(3)	NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `vehicles` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`org_id`	bigint	NOT NULL,
	`name`	varchar(50)	NOT NULL,
	`plate_number`	varchar(20)	NULL,
	`model`	varchar(50)	NULL,
	`seat_capacity`	smallint	NULL,
	`status`	varchar(20)	NOT NULL	DEFAULT 'ACTIVE'	COMMENT 'ACTIVE / INACTIVE',
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`updated_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`deleted_at`	DATETIME(3)	NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `vehicle_pairing_codes` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`org_id`	bigint	NOT NULL	COMMENT '→ 기관.id (ERD 선 생략)',
	`vehicle_id`	bigint	NOT NULL,
	`code_hash`	char(64)	NOT NULL,
	`issued_by_staff_id`	bigint	NULL	COMMENT '→ 기관 소속 인원.id (ERD 선 생략)',
	`expires_at`	DATETIME(3)	NOT NULL,
	`used_at`	DATETIME(3)	NULL,
	`used_by_device_id`	bigint	NULL,
	`revoked_at`	DATETIME(3)	NULL,
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `vehicle_devices` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`org_id`	bigint	NOT NULL	COMMENT '→ 기관.id (ERD 선 생략)',
	`vehicle_id`	bigint	NOT NULL,
	`install_id`	CHAR(36)	NOT NULL,
	`credential_hash`	char(64)	NOT NULL,
	`app_version`	varchar(20)	NULL,
	`pleos_sdk_version`	varchar(20)	NULL,
	`paired_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`last_seen_at`	DATETIME(3)	NULL,
	`revoked_at`	DATETIME(3)	NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `stops` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`org_id`	bigint	NOT NULL,
	`name`	varchar(100)	NOT NULL,
	`address`	varchar(255)	NULL,
	`latitude`	numeric(9,6)	NOT NULL,
	`longitude`	numeric(9,6)	NOT NULL,
	`arrival_radius_m`	smallint	NOT NULL	DEFAULT 50,
	`memo`	varchar(255)	NULL,
	`is_active`	TINYINT(1)	NOT NULL	DEFAULT true,
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`updated_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`deleted_at`	DATETIME(3)	NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `children` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`org_id`	bigint	NOT NULL,
	`name`	varchar(50)	NOT NULL,
	`class_name`	varchar(50)	NULL,
	`status`	varchar(20)	NOT NULL	DEFAULT 'ACTIVE'	COMMENT 'ACTIVE / INACTIVE / GRADUATED',
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`updated_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`deleted_at`	DATETIME(3)	NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `route_stops` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`org_id`	bigint	NOT NULL,
	`route_id`	bigint	NOT NULL,
	`stop_id`	bigint	NOT NULL,
	`default_seq`	smallint	NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `child_route_assignments` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`org_id`	bigint	NOT NULL,
	`child_id`	bigint	NOT NULL,
	`route_id`	bigint	NOT NULL,
	`vehicle_id`	bigint	NOT NULL,
	`stop_id`	bigint	NOT NULL,
	`weekdays`	smallint	NOT NULL	DEFAULT 31,
	`effective_from`	date	NOT NULL	DEFAULT (CURRENT_DATE),
	`effective_to`	date	NULL,
	`assigned_by_staff_id`	bigint	NULL	COMMENT '→ 기관 소속 인원.id (ERD 선 생략)',
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`updated_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `routes` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`org_id`	bigint	NOT NULL	COMMENT '→ 기관.id (ERD 선 생략)',
	`vehicle_id`	bigint	NOT NULL,
	`name`	varchar(100)	NOT NULL,
	`direction`	varchar(10)	NOT NULL	DEFAULT 'DROP_OFF'	COMMENT 'DROP_OFF / PICK_UP',
	`departure_time`	time	NULL,
	`is_active`	TINYINT(1)	NOT NULL	DEFAULT true,
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`updated_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`deleted_at`	DATETIME(3)	NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `guardian_invitations` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`org_id`	bigint	NOT NULL,
	`child_id`	bigint	NOT NULL,
	`invitee_email`	varchar(255)	NOT NULL,
	`relationship`	varchar(20)	NULL	COMMENT 'MOTHER / FATHER / GRANDPARENT / SIBLING / OTHER',
	`code_hash`	char(64)	NOT NULL,
	`status`	varchar(20)	NOT NULL	DEFAULT 'PENDING'	COMMENT 'PENDING / ACCEPTED / EXPIRED / REVOKED',
	`expires_at`	DATETIME(3)	NOT NULL,
	`accepted_at`	DATETIME(3)	NULL,
	`accepted_by_user_id`	bigint	NULL	COMMENT '→ 로그인 계정.id (ERD 선 생략)',
	`revoked_at`	DATETIME(3)	NULL,
	`reissued_from_id`	bigint	NULL,
	`issued_by_staff_id`	bigint	NULL	COMMENT '→ 기관 소속 인원.id (ERD 선 생략)',
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `child_guardians` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`child_id`	bigint	NOT NULL,
	`user_id`	bigint	NOT NULL,
	`relationship`	varchar(20)	NOT NULL	DEFAULT 'OTHER'	COMMENT 'MOTHER / FATHER / GRANDPARENT / SIBLING / OTHER',
	`is_primary`	TINYINT(1)	NOT NULL	DEFAULT false,
	`invitation_id`	bigint	NULL,
	`status`	varchar(20)	NOT NULL	DEFAULT 'ACTIVE'	COMMENT 'ACTIVE / REVOKED',
	`linked_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`revoked_at`	DATETIME(3)	NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `authorized_pickup_persons` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`org_id`	bigint	NOT NULL,
	`child_id`	bigint	NOT NULL,
	`name`	varchar(50)	NOT NULL,
	`relationship`	varchar(30)	NOT NULL,
	`phone_enc`	BLOB	NULL,
	`registered_by_user_id`	bigint	NOT NULL	COMMENT '→ 로그인 계정.id (ERD 선 생략)',
	`status`	varchar(20)	NOT NULL	DEFAULT 'PENDING'	COMMENT 'PENDING / APPROVED / REJECTED / REVOKED',
	`reviewed_by_staff_id`	bigint	NULL	COMMENT '→ 기관 소속 인원.id (ERD 선 생략)',
	`reviewed_at`	DATETIME(3)	NULL,
	`valid_from`	date	NULL,
	`valid_to`	date	NULL,
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`updated_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `push_devices` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`user_id`	bigint	NOT NULL,
	`platform`	varchar(10)	NOT NULL	COMMENT 'ANDROID / IOS / WEB',
	`fcm_token`	varchar(512)	NOT NULL,
	`app_version`	varchar(20)	NULL,
	`os_permission`	varchar(20)	NOT NULL	DEFAULT 'UNKNOWN'	COMMENT 'GRANTED / DENIED / UNKNOWN',
	`registered_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`last_refreshed_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`revoked_at`	DATETIME(3)	NULL,
	`revoke_reason`	varchar(30)	NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `attendance_requests` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`org_id`	bigint	NOT NULL,
	`child_id`	bigint	NOT NULL,
	`requested_by_user_id`	bigint	NOT NULL	COMMENT '→ 로그인 계정.id (ERD 선 생략)',
	`service_date`	date	NOT NULL,
	`direction`	varchar(10)	NOT NULL	COMMENT 'DROP_OFF / PICK_UP',
	`request_type`	varchar(20)	NOT NULL	COMMENT 'ABSENT / SELF_PICKUP / STOP_CHANGE / PICKUP_PERSON / NOT_RIDING',
	`reason_code`	varchar(20)	NULL	COMMENT 'SICK / FAMILY / TRIP / OTHER',
	`reason_text`	varchar(500)	NULL,
	`requested_stop_id`	bigint	NULL,
	`pickup_person_id`	bigint	NULL,
	`pickup_guardian_user_id`	bigint	NULL	COMMENT '→ 로그인 계정.id (ERD 선 생략)',
	`status`	varchar(20)	NOT NULL	DEFAULT 'PENDING'	COMMENT 'PENDING / APPROVED / REJECTED / CANCELLED',
	`reviewed_by_staff_id`	bigint	NULL	COMMENT '→ 기관 소속 인원.id (ERD 선 생략)',
	`reviewed_at`	DATETIME(3)	NULL,
	`review_note`	varchar(500)	NULL,
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`updated_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `trips` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`client_trip_id`	CHAR(36)	NOT NULL,
	`org_id`	bigint	NOT NULL,
	`vehicle_id`	bigint	NOT NULL,
	`route_id`	bigint	NULL,
	`vehicle_device_id`	bigint	NOT NULL,
	`direction`	varchar(10)	NOT NULL	DEFAULT 'DROP_OFF'	COMMENT 'DROP_OFF / PICK_UP',
	`service_date`	date	NOT NULL,
	`status`	varchar(20)	NOT NULL	DEFAULT 'PREPARING'	COMMENT 'PREPARING / IN_PROGRESS / RETURNING / COMPLETED / CANCELLED',
	`attendant_staff_id`	bigint	NULL,
	`driver_staff_id`	bigint	NULL	COMMENT '→ 기관 소속 인원.id (ERD 선 생략)',
	`route_revision_no`	integer	NOT NULL	DEFAULT 0,
	`last_latitude`	numeric(9,6)	NULL,
	`last_longitude`	numeric(9,6)	NULL,
	`last_speed_kph`	numeric(5,1)	NULL,
	`last_gear`	varchar(10)	NULL	COMMENT 'P / R / N / D / UNKNOWN',
	`last_location_at`	DATETIME(3)	NULL,
	`driver_checked_at`	DATETIME(3)	NULL	COMMENT '기사 구두 확인을 교사가 체크한 시각. 미확인 출발 가능',
	`boarding_completed_at`	DATETIME(3)	NULL,
	`started_at`	DATETIME(3)	NULL,
	`returning_at`	DATETIME(3)	NULL,
	`returned_at`	DATETIME(3)	NULL,
	`interior_checked_at`	DATETIME(3)	NULL,
	`ended_at`	DATETIME(3)	NULL,
	`cancelled_at`	DATETIME(3)	NULL,
	`cancel_reason`	varchar(200)	NULL,
	`end_note`	varchar(500)	NULL,
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`updated_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `trip_stops` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`trip_id`	bigint	NOT NULL,
	`stop_id`	bigint	NOT NULL,
	`seq`	smallint	NOT NULL,
	`suggested_seq`	smallint	NULL,
	`stop_name`	varchar(100)	NOT NULL,
	`latitude`	numeric(9,6)	NOT NULL,
	`longitude`	numeric(9,6)	NOT NULL,
	`status`	varchar(20)	NOT NULL	DEFAULT 'PENDING'	COMMENT 'PENDING / ARRIVED / COMPLETED / SKIPPED',
	`arrived_at`	DATETIME(3)	NULL,
	`completed_at`	DATETIME(3)	NULL,
	`eta_remaining_sec`	integer	NULL,
	`eta_remaining_m`	integer	NULL,
	`eta_snapshot_id`	bigint	NULL,
	`eta_received_at`	DATETIME(3)	NULL,
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`updated_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `trip_route_revisions` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`trip_id`	bigint	NOT NULL,
	`revision_no`	integer	NOT NULL,
	`stop_order`	JSON	NOT NULL,
	`change_reason`	varchar(20)	NOT NULL	COMMENT 'INITIAL / MANUAL_REORDER / STOP_SKIPPED / STOP_ADDED',
	`changed_by_staff_id`	bigint	NULL	COMMENT '→ 기관 소속 인원.id (ERD 선 생략)',
	`changed_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`vehicle_speed_kph`	numeric(5,1)	NULL,
	`vehicle_gear`	varchar(10)	NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `trip_events` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`client_event_id`	CHAR(36)	NOT NULL,
	`trip_id`	bigint	NOT NULL,
	`trip_child_id`	bigint	NULL,
	`trip_stop_id`	bigint	NULL,
	`event_type`	varchar(40)	NOT NULL	COMMENT 'TRIP_PREPARED / CHILD_BOARDED / CHILD_BOARDING_UNDONE / CHILD_NOT_BOARDED / BOARDING_COMPLETED / ROUTE_REORDERED / TRIP_STARTED / NAVIGATION_REQUESTED / STOP_ARRIVED / CHILD_HANDED_OVER / CHILD_DROPPED_OFF / GUARDIAN_NO_SHOW / STOP_COMPLETED / STOP_SKIPPED / TRIP_RETURNING / TRIP_RETURNED / CHILD_RETURNED_TO_ORG / INTERIOR_CHECKED / TRIP_ENDED / TRIP_CANCELLED / VOICE_ANNOUNCED / PROXIMITY_ALERT / GUARDIAN_CALL_REQUESTED / GUARDIAN_CALL_RESPONDED / TEACHER_CONFIRMED_NOT_BOARDED / CHILD_RECEIVED_BY_ORG / DRIVER_PRE_DEPARTURE_CONFIRMED / CORRECTION',
	`corrects_event_id`	bigint	NULL,
	`occurred_at`	DATETIME(3)	NOT NULL,
	`received_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`recorded_by_device_id`	bigint	NULL,
	`recorded_by_staff_id`	bigint	NULL	COMMENT '→ 기관 소속 인원.id (ERD 선 생략)',
	`vs_speed_kph`	numeric(5,1)	NULL,
	`vs_gear`	varchar(10)	NULL	COMMENT 'P / R / N / D / UNKNOWN',
	`vs_ux_restricted`	TINYINT(1)	NULL,
	`vs_connected`	TINYINT(1)	NULL,
	`vs_sampled_at`	DATETIME(3)	NULL,
	`latitude`	numeric(9,6)	NULL,
	`longitude`	numeric(9,6)	NULL,
	`payload`	JSON	NOT NULL	DEFAULT (JSON_OBJECT()),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `trip_children` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`org_id`	bigint	NOT NULL,
	`trip_id`	bigint	NOT NULL,
	`vehicle_id`	bigint	NOT NULL,
	`child_id`	bigint	NOT NULL,
	`trip_stop_id`	bigint	NOT NULL,
	`child_name`	varchar(50)	NOT NULL,
	`boarding_expected`	TINYINT(1)	NOT NULL	DEFAULT 1	COMMENT '해당 방향 운행에서 탑승 예정인지. 기본 탑승',
	`status`	varchar(20)	NOT NULL	DEFAULT 'EXPECTED'	COMMENT 'EXPECTED / BOARDED / NOT_BOARDED / HANDED_OVER / DROPPED_OFF / GUARDIAN_NO_SHOW / RETURNED_TO_ORG',
	`not_boarded_reason`	varchar(20)	NULL	COMMENT 'ABSENT / SELF_PICKUP / OTHER / NOT_RIDING',
	`attendance_request_id`	bigint	NULL,
	`boarded_at`	DATETIME(3)	NULL,
	`handed_over_at`	DATETIME(3)	NULL,
	`handover_to_type`	varchar(20)	NULL	COMMENT 'GUARDIAN / AUTHORIZED_PERSON / ORG_STAFF',
	`handover_guardian_user_id`	bigint	NULL	COMMENT '→ 로그인 계정.id (ERD 선 생략)',
	`handover_pickup_person_id`	bigint	NULL,
	`handover_person_name`	varchar(50)	NULL,
	`dropped_off_at`	DATETIME(3)	NULL,
	`no_show_at`	DATETIME(3)	NULL,
	`no_show_reason`	varchar(300)	NULL,
	`returned_to_org_at`	DATETIME(3)	NULL,
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`updated_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `trip_eta_snapshots` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`trip_id`	bigint	NOT NULL,
	`route_revision_no`	integer	NOT NULL,
	`nav_route_key`	varchar(100)	NULL,
	`source_time`	DATETIME(3)	NOT NULL,
	`received_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`validation_status`	varchar(20)	NOT NULL	COMMENT 'VALID / STOP_MISMATCH / ROUTE_MISMATCH / REROUTING / STALE / INVALID_PAYLOAD',
	`validation_note`	varchar(300)	NULL,
	`raw_payload`	JSON	NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `trip_location_samples` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`trip_id`	bigint	NOT NULL,
	`recorded_at`	DATETIME(3)	NOT NULL,
	`received_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`latitude`	numeric(9,6)	NOT NULL,
	`longitude`	numeric(9,6)	NOT NULL,
	`speed_kph`	numeric(5,1)	NULL,
	`heading_deg`	smallint	NULL,
	`accuracy_m`	numeric(6,1)	NULL,
	`gear`	varchar(10)	NULL	COMMENT 'P / R / N / D / UNKNOWN',
	`source`	varchar(10)	NOT NULL	DEFAULT 'PLEOS'	COMMENT 'PLEOS / GPS',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `org_announcements` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`org_id`	bigint	NOT NULL,
	`trip_id`	bigint	NULL,
	`audience`	varchar(20)	NOT NULL	COMMENT 'ALL / VEHICLE / TRIP',
	`vehicle_id`	bigint	NULL,
	`category`	varchar(20)	NOT NULL	COMMENT 'DELAY / EMERGENCY / NOTICE',
	`title`	varchar(100)	NOT NULL,
	`body`	varchar(1000)	NOT NULL,
	`created_by_staff_id`	bigint	NOT NULL	COMMENT '→ 기관 소속 인원.id (ERD 선 생략)',
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`sent_at`	DATETIME(3)	NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `trip_eta_entries` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`trip_id`	bigint	NOT NULL,
	`snapshot_id`	bigint	NOT NULL,
	`bundle_index`	smallint	NOT NULL,
	`point_type`	varchar(20)	NOT NULL	COMMENT 'WAYPOINT / DESTINATION',
	`trip_stop_id`	bigint	NULL,
	`latitude`	numeric(9,6)	NULL,
	`longitude`	numeric(9,6)	NULL,
	`remaining_sec`	integer	NOT NULL,
	`remaining_m`	integer	NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `notifications` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`user_id`	bigint	NOT NULL,
	`child_id`	bigint	NULL,
	`trip_id`	bigint	NULL,
	`trip_event_id`	bigint	NULL,
	`announcement_id`	bigint	NULL,
	`notification_type`	varchar(30)	NOT NULL	COMMENT 'TRIP_DEPARTED / APPROACHING / ARRIVED / DROPPED_OFF / GUARDIAN_NO_SHOW / RETURNED_TO_ORG / TRIP_DELAYED / ANNOUNCEMENT / REQUEST_REVIEWED',
	`dedupe_key`	varchar(150)	NOT NULL	COMMENT '출결 알림 묶음 키에는 direction 포함: attendance:{child_id}:{service_date}:{direction}',
	`title`	varchar(100)	NOT NULL,
	`body`	varchar(500)	NOT NULL,
	`data`	JSON	NOT NULL	DEFAULT (JSON_OBJECT()),
	`status`	varchar(20)	NOT NULL	DEFAULT 'PENDING'	COMMENT 'PENDING / SENT / PARTIAL / FAILED / CANCELLED / SKIPPED',
	`cancel_reason`	varchar(50)	NULL,
	`eta_snapshot_id`	bigint	NULL,
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`sent_at`	DATETIME(3)	NULL,
	`read_at`	DATETIME(3)	NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `trip_checklist_items` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`trip_id`	bigint	NOT NULL,
	`phase`	varchar(20)	NOT NULL	COMMENT 'PRE_DEPARTURE / POST_TRIP',
	`item_code`	varchar(40)	NOT NULL,
	`result`	varchar(10)	NOT NULL	COMMENT 'OK / NG / NA',
	`note`	varchar(300)	NULL,
	`checked_by_staff_id`	bigint	NULL	COMMENT '→ 기관 소속 인원.id (ERD 선 생략)',
	`checked_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `share_links` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`token_hash`	char(64)	NOT NULL,
	`child_id`	bigint	NOT NULL,
	`trip_id`	bigint	NOT NULL,
	`created_by_user_id`	bigint	NOT NULL	COMMENT '→ 로그인 계정.id (ERD 선 생략)',
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`expires_at`	DATETIME(3)	NOT NULL,
	`revoked_at`	DATETIME(3)	NULL,
	`revoke_reason`	varchar(20)	NULL	COMMENT 'MANUAL / DROPPED_OFF / TRIP_ENDED / GUARDIAN_UNLINKED',
	`last_accessed_at`	DATETIME(3)	NULL,
	`access_count`	integer	NOT NULL	DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `notification_deliveries` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`notification_id`	bigint	NOT NULL,
	`push_device_id`	bigint	NOT NULL,
	`attempt_no`	smallint	NOT NULL	DEFAULT 1,
	`status`	varchar(20)	NOT NULL	COMMENT 'QUEUED / SENT / FAILED / INVALID_TOKEN',
	`fcm_message_id`	varchar(200)	NULL,
	`error_code`	varchar(50)	NULL,
	`attempted_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`next_retry_at`	DATETIME(3)	NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `org_handover_records` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`trip_child_id`	bigint	NOT NULL,
	`received_by_staff_id`	bigint	NOT NULL	COMMENT '→ 기관 소속 인원.id (ERD 선 생략)',
	`received_at`	DATETIME(3)	NOT NULL,
	`guardian_contacted_at`	DATETIME(3)	NULL,
	`resolution`	varchar(30)	NULL	COMMENT 'PICKED_UP_AT_ORG / REDELIVERED / OTHER',
	`resolved_at`	DATETIME(3)	NULL,
	`note`	varchar(500)	NULL,
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
	`updated_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `staff_invitations` (

	`id`	bigint	NOT NULL AUTO_INCREMENT,
	`org_id`	bigint	NOT NULL	COMMENT '→ 기관.id (ERD 선 생략)',
	`staff_id`	bigint	NULL,
	`invitee_email`	varchar(255)	NOT NULL,
	`staff_role`	varchar(20)	NOT NULL	COMMENT 'ADMIN / TEACHER / DRIVER',
	`web_role`	varchar(20)	NULL	COMMENT 'OWNER / MANAGER / VIEWER',
	`code_hash`	char(64)	NOT NULL,
	`status`	varchar(20)	NOT NULL	DEFAULT 'PENDING'	COMMENT 'PENDING / ACCEPTED / EXPIRED / REVOKED',
	`expires_at`	DATETIME(3)	NOT NULL,
	`accepted_at`	DATETIME(3)	NULL,
	`accepted_by_user_id`	bigint	NULL	COMMENT '→ 로그인 계정.id (ERD 선 생략)',
	`revoked_at`	DATETIME(3)	NULL,
	`issued_by_staff_id`	bigint	NULL	COMMENT '→ 기관 소속 인원.id (ERD 선 생략)',
	`created_at`	DATETIME(3)	NOT NULL	DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `daily_pickups` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `org_id` bigint NOT NULL,
  `child_id` bigint NOT NULL,
  `service_date` date NOT NULL,
  `direction` varchar(10) NOT NULL COMMENT 'DROP_OFF / PICK_UP',
  `pickup_person_id` bigint NULL,
  `pickup_guardian_user_id` bigint NULL,
  `person_name_snapshot` varchar(50) NULL,
  `relationship_snapshot` varchar(30) NULL,
  `updated_by_user_id` bigint NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_daily_pickup_child_date_direction` (`child_id`, `service_date`, `direction`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `guardian_call_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `trip_id` bigint NOT NULL,
  `trip_child_id` bigint NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN / RESPONDED / CLOSED / EXPIRED',
  `opened_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `expires_at` DATETIME(3) NOT NULL,
  `response` varchar(20) NULL COMMENT 'ON_THE_WAY / NOT_RIDING',
  `responded_at` DATETIME(3) NULL,
  `closed_at` DATETIME(3) NULL,
  `created_by_staff_id` bigint NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `auth_sessions` ADD CONSTRAINT `fk_auth_sessions_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `email_verifications` ADD CONSTRAINT `fk_email_verifications_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `user_consents` ADD CONSTRAINT `fk_user_consents_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `user_consents` ADD CONSTRAINT `fk_user_consents_policy_document_id` FOREIGN KEY (`policy_document_id`) REFERENCES `policy_documents` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `user_consents` ADD CONSTRAINT `fk_user_consents_child_id` FOREIGN KEY (`child_id`) REFERENCES `children` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `org_staff` ADD CONSTRAINT `fk_org_staff_org_id` FOREIGN KEY (`org_id`) REFERENCES `organizations` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `org_staff` ADD CONSTRAINT `fk_org_staff_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `vehicles` ADD CONSTRAINT `fk_vehicles_org_id` FOREIGN KEY (`org_id`) REFERENCES `organizations` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `vehicle_pairing_codes` ADD CONSTRAINT `fk_vehicle_pairing_codes_org_id` FOREIGN KEY (`org_id`) REFERENCES `organizations` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `vehicle_pairing_codes` ADD CONSTRAINT `fk_vehicle_pairing_codes_vehicle_id` FOREIGN KEY (`vehicle_id`) REFERENCES `vehicles` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `vehicle_pairing_codes` ADD CONSTRAINT `fk_vehicle_pairing_codes_issued_by_staff_id` FOREIGN KEY (`issued_by_staff_id`) REFERENCES `org_staff` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `vehicle_pairing_codes` ADD CONSTRAINT `fk_vehicle_pairing_codes_used_by_device_id` FOREIGN KEY (`used_by_device_id`) REFERENCES `vehicle_devices` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `vehicle_devices` ADD CONSTRAINT `fk_vehicle_devices_org_id` FOREIGN KEY (`org_id`) REFERENCES `organizations` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `vehicle_devices` ADD CONSTRAINT `fk_vehicle_devices_vehicle_id` FOREIGN KEY (`vehicle_id`) REFERENCES `vehicles` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `stops` ADD CONSTRAINT `fk_stops_org_id` FOREIGN KEY (`org_id`) REFERENCES `organizations` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `children` ADD CONSTRAINT `fk_children_org_id` FOREIGN KEY (`org_id`) REFERENCES `organizations` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `route_stops` ADD CONSTRAINT `fk_route_stops_org_id` FOREIGN KEY (`org_id`) REFERENCES `organizations` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `route_stops` ADD CONSTRAINT `fk_route_stops_route_id` FOREIGN KEY (`route_id`) REFERENCES `routes` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `route_stops` ADD CONSTRAINT `fk_route_stops_stop_id` FOREIGN KEY (`stop_id`) REFERENCES `stops` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `child_route_assignments` ADD CONSTRAINT `fk_child_route_assignments_org_id` FOREIGN KEY (`org_id`) REFERENCES `organizations` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `child_route_assignments` ADD CONSTRAINT `fk_child_route_assignments_child_id` FOREIGN KEY (`child_id`) REFERENCES `children` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `child_route_assignments` ADD CONSTRAINT `fk_child_route_assignments_route_id` FOREIGN KEY (`route_id`) REFERENCES `routes` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `child_route_assignments` ADD CONSTRAINT `fk_child_route_assignments_vehicle_id` FOREIGN KEY (`vehicle_id`) REFERENCES `vehicles` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `child_route_assignments` ADD CONSTRAINT `fk_child_route_assignments_stop_id` FOREIGN KEY (`stop_id`) REFERENCES `stops` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `child_route_assignments` ADD CONSTRAINT `fk_child_route_assignments_assigned_by_staff_id` FOREIGN KEY (`assigned_by_staff_id`) REFERENCES `org_staff` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `routes` ADD CONSTRAINT `fk_routes_org_id` FOREIGN KEY (`org_id`) REFERENCES `organizations` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `routes` ADD CONSTRAINT `fk_routes_vehicle_id` FOREIGN KEY (`vehicle_id`) REFERENCES `vehicles` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `guardian_invitations` ADD CONSTRAINT `fk_guardian_invitations_org_id` FOREIGN KEY (`org_id`) REFERENCES `organizations` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `guardian_invitations` ADD CONSTRAINT `fk_guardian_invitations_child_id` FOREIGN KEY (`child_id`) REFERENCES `children` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `guardian_invitations` ADD CONSTRAINT `fk_guardian_invitations_accepted_by_user_id` FOREIGN KEY (`accepted_by_user_id`) REFERENCES `users` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `guardian_invitations` ADD CONSTRAINT `fk_guardian_invitations_reissued_from_id` FOREIGN KEY (`reissued_from_id`) REFERENCES `guardian_invitations` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `guardian_invitations` ADD CONSTRAINT `fk_guardian_invitations_issued_by_staff_id` FOREIGN KEY (`issued_by_staff_id`) REFERENCES `org_staff` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `child_guardians` ADD CONSTRAINT `fk_child_guardians_child_id` FOREIGN KEY (`child_id`) REFERENCES `children` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `child_guardians` ADD CONSTRAINT `fk_child_guardians_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `child_guardians` ADD CONSTRAINT `fk_child_guardians_invitation_id` FOREIGN KEY (`invitation_id`) REFERENCES `guardian_invitations` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `authorized_pickup_persons` ADD CONSTRAINT `fk_authorized_pickup_persons_org_id` FOREIGN KEY (`org_id`) REFERENCES `organizations` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `authorized_pickup_persons` ADD CONSTRAINT `fk_authorized_pickup_persons_child_id` FOREIGN KEY (`child_id`) REFERENCES `children` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `authorized_pickup_persons` ADD CONSTRAINT `fk_authorized_pickup_persons_registered_by_user_id` FOREIGN KEY (`registered_by_user_id`) REFERENCES `users` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `authorized_pickup_persons` ADD CONSTRAINT `fk_authorized_pickup_persons_reviewed_by_staff_id` FOREIGN KEY (`reviewed_by_staff_id`) REFERENCES `org_staff` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `push_devices` ADD CONSTRAINT `fk_push_devices_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `attendance_requests` ADD CONSTRAINT `fk_attendance_requests_org_id` FOREIGN KEY (`org_id`) REFERENCES `organizations` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `attendance_requests` ADD CONSTRAINT `fk_attendance_requests_child_id` FOREIGN KEY (`child_id`) REFERENCES `children` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `attendance_requests` ADD CONSTRAINT `fk_attendance_requests_requested_by_user_id` FOREIGN KEY (`requested_by_user_id`) REFERENCES `users` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `attendance_requests` ADD CONSTRAINT `fk_attendance_requests_requested_stop_id` FOREIGN KEY (`requested_stop_id`) REFERENCES `stops` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `attendance_requests` ADD CONSTRAINT `fk_attendance_requests_pickup_person_id` FOREIGN KEY (`pickup_person_id`) REFERENCES `authorized_pickup_persons` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `attendance_requests` ADD CONSTRAINT `fk_attendance_requests_pickup_guardian_user_id` FOREIGN KEY (`pickup_guardian_user_id`) REFERENCES `users` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `attendance_requests` ADD CONSTRAINT `fk_attendance_requests_reviewed_by_staff_id` FOREIGN KEY (`reviewed_by_staff_id`) REFERENCES `org_staff` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trips` ADD CONSTRAINT `fk_trips_org_id` FOREIGN KEY (`org_id`) REFERENCES `organizations` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trips` ADD CONSTRAINT `fk_trips_vehicle_id` FOREIGN KEY (`vehicle_id`) REFERENCES `vehicles` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trips` ADD CONSTRAINT `fk_trips_route_id` FOREIGN KEY (`route_id`) REFERENCES `routes` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trips` ADD CONSTRAINT `fk_trips_vehicle_device_id` FOREIGN KEY (`vehicle_device_id`) REFERENCES `vehicle_devices` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trips` ADD CONSTRAINT `fk_trips_attendant_staff_id` FOREIGN KEY (`attendant_staff_id`) REFERENCES `org_staff` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trips` ADD CONSTRAINT `fk_trips_driver_staff_id` FOREIGN KEY (`driver_staff_id`) REFERENCES `org_staff` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trip_stops` ADD CONSTRAINT `fk_trip_stops_trip_id` FOREIGN KEY (`trip_id`) REFERENCES `trips` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trip_stops` ADD CONSTRAINT `fk_trip_stops_stop_id` FOREIGN KEY (`stop_id`) REFERENCES `stops` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trip_stops` ADD CONSTRAINT `fk_trip_stops_eta_snapshot_id` FOREIGN KEY (`eta_snapshot_id`) REFERENCES `trip_eta_snapshots` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trip_route_revisions` ADD CONSTRAINT `fk_trip_route_revisions_trip_id` FOREIGN KEY (`trip_id`) REFERENCES `trips` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trip_route_revisions` ADD CONSTRAINT `fk_trip_route_revisions_changed_by_staff_id` FOREIGN KEY (`changed_by_staff_id`) REFERENCES `org_staff` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trip_events` ADD CONSTRAINT `fk_trip_events_trip_id` FOREIGN KEY (`trip_id`) REFERENCES `trips` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trip_events` ADD CONSTRAINT `fk_trip_events_trip_child_id` FOREIGN KEY (`trip_child_id`) REFERENCES `trip_children` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trip_events` ADD CONSTRAINT `fk_trip_events_trip_stop_id` FOREIGN KEY (`trip_stop_id`) REFERENCES `trip_stops` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trip_events` ADD CONSTRAINT `fk_trip_events_corrects_event_id` FOREIGN KEY (`corrects_event_id`) REFERENCES `trip_events` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trip_events` ADD CONSTRAINT `fk_trip_events_recorded_by_device_id` FOREIGN KEY (`recorded_by_device_id`) REFERENCES `vehicle_devices` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trip_events` ADD CONSTRAINT `fk_trip_events_recorded_by_staff_id` FOREIGN KEY (`recorded_by_staff_id`) REFERENCES `org_staff` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trip_children` ADD CONSTRAINT `fk_trip_children_org_id` FOREIGN KEY (`org_id`) REFERENCES `organizations` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trip_children` ADD CONSTRAINT `fk_trip_children_trip_id` FOREIGN KEY (`trip_id`) REFERENCES `trips` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trip_children` ADD CONSTRAINT `fk_trip_children_vehicle_id` FOREIGN KEY (`vehicle_id`) REFERENCES `vehicles` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trip_children` ADD CONSTRAINT `fk_trip_children_child_id` FOREIGN KEY (`child_id`) REFERENCES `children` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trip_children` ADD CONSTRAINT `fk_trip_children_trip_stop_id` FOREIGN KEY (`trip_stop_id`) REFERENCES `trip_stops` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trip_children` ADD CONSTRAINT `fk_trip_children_attendance_request_id` FOREIGN KEY (`attendance_request_id`) REFERENCES `attendance_requests` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trip_children` ADD CONSTRAINT `fk_trip_children_handover_guardian_user_id` FOREIGN KEY (`handover_guardian_user_id`) REFERENCES `users` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trip_children` ADD CONSTRAINT `fk_trip_children_handover_pickup_person_id` FOREIGN KEY (`handover_pickup_person_id`) REFERENCES `authorized_pickup_persons` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trip_eta_snapshots` ADD CONSTRAINT `fk_trip_eta_snapshots_trip_id` FOREIGN KEY (`trip_id`) REFERENCES `trips` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trip_location_samples` ADD CONSTRAINT `fk_trip_location_samples_trip_id` FOREIGN KEY (`trip_id`) REFERENCES `trips` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `org_announcements` ADD CONSTRAINT `fk_org_announcements_org_id` FOREIGN KEY (`org_id`) REFERENCES `organizations` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `org_announcements` ADD CONSTRAINT `fk_org_announcements_trip_id` FOREIGN KEY (`trip_id`) REFERENCES `trips` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `org_announcements` ADD CONSTRAINT `fk_org_announcements_vehicle_id` FOREIGN KEY (`vehicle_id`) REFERENCES `vehicles` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `org_announcements` ADD CONSTRAINT `fk_org_announcements_created_by_staff_id` FOREIGN KEY (`created_by_staff_id`) REFERENCES `org_staff` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trip_eta_entries` ADD CONSTRAINT `fk_trip_eta_entries_trip_id` FOREIGN KEY (`trip_id`) REFERENCES `trips` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trip_eta_entries` ADD CONSTRAINT `fk_trip_eta_entries_snapshot_id` FOREIGN KEY (`snapshot_id`) REFERENCES `trip_eta_snapshots` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trip_eta_entries` ADD CONSTRAINT `fk_trip_eta_entries_trip_stop_id` FOREIGN KEY (`trip_stop_id`) REFERENCES `trip_stops` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `notifications` ADD CONSTRAINT `fk_notifications_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `notifications` ADD CONSTRAINT `fk_notifications_child_id` FOREIGN KEY (`child_id`) REFERENCES `children` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `notifications` ADD CONSTRAINT `fk_notifications_trip_id` FOREIGN KEY (`trip_id`) REFERENCES `trips` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `notifications` ADD CONSTRAINT `fk_notifications_trip_event_id` FOREIGN KEY (`trip_event_id`) REFERENCES `trip_events` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `notifications` ADD CONSTRAINT `fk_notifications_announcement_id` FOREIGN KEY (`announcement_id`) REFERENCES `org_announcements` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `notifications` ADD CONSTRAINT `fk_notifications_eta_snapshot_id` FOREIGN KEY (`eta_snapshot_id`) REFERENCES `trip_eta_snapshots` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trip_checklist_items` ADD CONSTRAINT `fk_trip_checklist_items_trip_id` FOREIGN KEY (`trip_id`) REFERENCES `trips` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `trip_checklist_items` ADD CONSTRAINT `fk_trip_checklist_items_checked_by_staff_id` FOREIGN KEY (`checked_by_staff_id`) REFERENCES `org_staff` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `share_links` ADD CONSTRAINT `fk_share_links_child_id` FOREIGN KEY (`child_id`) REFERENCES `children` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `share_links` ADD CONSTRAINT `fk_share_links_trip_id` FOREIGN KEY (`trip_id`) REFERENCES `trips` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `share_links` ADD CONSTRAINT `fk_share_links_created_by_user_id` FOREIGN KEY (`created_by_user_id`) REFERENCES `users` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `notification_deliveries` ADD CONSTRAINT `fk_notification_deliveries_notification_id` FOREIGN KEY (`notification_id`) REFERENCES `notifications` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `notification_deliveries` ADD CONSTRAINT `fk_notification_deliveries_push_device_id` FOREIGN KEY (`push_device_id`) REFERENCES `push_devices` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `org_handover_records` ADD CONSTRAINT `fk_org_handover_records_trip_child_id` FOREIGN KEY (`trip_child_id`) REFERENCES `trip_children` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `org_handover_records` ADD CONSTRAINT `fk_org_handover_records_received_by_staff_id` FOREIGN KEY (`received_by_staff_id`) REFERENCES `org_staff` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `staff_invitations` ADD CONSTRAINT `fk_staff_invitations_org_id` FOREIGN KEY (`org_id`) REFERENCES `organizations` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `staff_invitations` ADD CONSTRAINT `fk_staff_invitations_staff_id` FOREIGN KEY (`staff_id`) REFERENCES `org_staff` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `staff_invitations` ADD CONSTRAINT `fk_staff_invitations_accepted_by_user_id` FOREIGN KEY (`accepted_by_user_id`) REFERENCES `users` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `staff_invitations` ADD CONSTRAINT `fk_staff_invitations_issued_by_staff_id` FOREIGN KEY (`issued_by_staff_id`) REFERENCES `org_staff` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;


ALTER TABLE `daily_pickups` ADD CONSTRAINT `fk_daily_pickups_org_id` FOREIGN KEY (`org_id`) REFERENCES `organizations` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `daily_pickups` ADD CONSTRAINT `fk_daily_pickups_child_id` FOREIGN KEY (`child_id`) REFERENCES `children` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `daily_pickups` ADD CONSTRAINT `fk_daily_pickups_pickup_person_id` FOREIGN KEY (`pickup_person_id`) REFERENCES `authorized_pickup_persons` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `daily_pickups` ADD CONSTRAINT `fk_daily_pickups_guardian_user_id` FOREIGN KEY (`pickup_guardian_user_id`) REFERENCES `users` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `daily_pickups` ADD CONSTRAINT `fk_daily_pickups_updated_by_user_id` FOREIGN KEY (`updated_by_user_id`) REFERENCES `users` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `guardian_call_requests` ADD CONSTRAINT `fk_guardian_call_requests_trip_id` FOREIGN KEY (`trip_id`) REFERENCES `trips` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `guardian_call_requests` ADD CONSTRAINT `fk_guardian_call_requests_trip_child_id` FOREIGN KEY (`trip_child_id`) REFERENCES `trip_children` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;
ALTER TABLE `guardian_call_requests` ADD CONSTRAINT `fk_guardian_call_requests_created_by_staff_id` FOREIGN KEY (`created_by_staff_id`) REFERENCES `org_staff` (`id`) ON UPDATE CASCADE ON DELETE RESTRICT;

-- Demo data for a fresh tayotayo_dev database.
-- Fixed IDs intentionally make the example predictable; do not run this against an existing schema.
-- Demo login: orgs@example.com / orgspass123 (ADMIN_WEB)
-- Demo vehicle credential for API smoke tests: DEMO-HASAL-VEHICLE-1
-- Demo stops use approximate coordinates around Pangyo, Yatap, and Seongnam City Hall.
INSERT INTO `organizations` (`id`,`name`,`org_type`,`status`) VALUES
  (1,'햇살 유치원','KINDERGARTEN','ACTIVE');

-- Password hash uses the same PBKDF2-HMAC-SHA256 format and 120,000 iterations as AuthController.
INSERT INTO `users` (`id`,`email`,`password_hash`,`name`,`email_verified_at`,`status`) VALUES
  (1,'orgs@example.com','pbkdf2$AAECAwQFBgcICQoLDA0ODw==$ay8ZAsavzjx89WqtaEvKRt4OZF8qwBtdbAbEwiuyDZI=','orgs',CURRENT_TIMESTAMP(3),'ACTIVE');
INSERT INTO `org_staff` (`id`,`org_id`,`user_id`,`name`,`staff_role`,`web_role`,`status`) VALUES
  (1,1,1,'orgs 관리자','ADMIN','OWNER','ACTIVE');

INSERT INTO `vehicles` (`id`,`org_id`,`name`,`plate_number`,`model`,`seat_capacity`,`status`) VALUES
  (1,1,'햇살 1호차','12가3456','셔틀버스',20,'ACTIVE');
INSERT INTO `vehicle_devices` (`id`,`org_id`,`vehicle_id`,`install_id`,`credential_hash`,`app_version`,`pleos_sdk_version`) VALUES
  (1,1,1,'00000000-0000-4000-8000-000000000001',SHA2('DEMO-HASAL-VEHICLE-1',256),'demo','demo');

INSERT INTO `stops` (`id`,`org_id`,`name`,`address`,`latitude`,`longitude`,`arrival_radius_m`,`is_active`) VALUES
  (1,1,'판교역','경기 성남시 분당구 판교역로 160',37.3948,127.1112,50,1),
  (2,1,'야탑역','경기 성남시 분당구 성남대로 903',37.4113,127.1287,50,1),
  (3,1,'성남시청','경기 성남시 중원구 성남대로 997',37.4201,127.1265,50,1);

INSERT INTO `routes` (`id`,`org_id`,`vehicle_id`,`name`,`direction`,`departure_time`,`is_active`) VALUES
  (1,1,1,'오전 등원 노선','PICK_UP','08:30:00',1);
INSERT INTO `route_stops` (`org_id`,`route_id`,`stop_id`,`default_seq`) VALUES
  (1,1,1,1),(1,1,2,2),(1,1,3,3);

INSERT INTO `children` (`id`,`org_id`,`name`,`class_name`,`status`) VALUES
  (1,1,'김하늘','햇살반','ACTIVE'),
  (2,1,'김바다','햇살반','ACTIVE'),
  (3,1,'이마음','햇살반','ACTIVE');
INSERT INTO `child_route_assignments` (`org_id`,`child_id`,`route_id`,`vehicle_id`,`stop_id`,`weekdays`,`effective_from`) VALUES
  (1,1,1,1,1,31,CURRENT_DATE),
  (1,2,1,1,2,31,CURRENT_DATE),
  (1,3,1,1,3,31,CURRENT_DATE);

-- One PREPARING trip for today, with the route stops and assigned children snapshotted.
INSERT INTO `trips` (`id`,`client_trip_id`,`org_id`,`vehicle_id`,`route_id`,`vehicle_device_id`,`direction`,`service_date`,`status`) VALUES
  (1,'00000000-0000-4000-8000-000000000101',1,1,1,1,'PICK_UP',CURRENT_DATE,'PREPARING');
INSERT INTO `trip_stops` (`trip_id`,`stop_id`,`seq`,`stop_name`,`latitude`,`longitude`) VALUES
  (1,1,1,'판교역',37.3948,127.1112),
  (1,2,2,'야탑역',37.4113,127.1287),
  (1,3,3,'성남시청',37.4201,127.1265);
INSERT INTO `trip_children` (`org_id`,`trip_id`,`vehicle_id`,`child_id`,`trip_stop_id`,`child_name`,`boarding_expected`,`status`) VALUES
  (1,1,1,1,1,'김하늘',1,'EXPECTED'),
  (1,1,1,2,2,'김바다',1,'EXPECTED'),
  (1,1,1,3,3,'이마음',1,'EXPECTED');
