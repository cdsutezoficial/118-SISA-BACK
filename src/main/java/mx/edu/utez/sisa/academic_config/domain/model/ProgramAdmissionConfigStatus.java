package mx.edu.utez.sisa.academic_config.domain.model;

/**
 * Controls the ticket-sales window ({@code opensAt}/{@code closesAt}) of a
 * {@link ProgramAdmissionConfig} — a simple 2-value toggle, same class of
 * status as {@link GenerationStatus}/{@link GroupStatus} (both
 * ACTIVE/INACTIVE-shaped), just named {@code OPEN}/{@code CLOSED} to match
 * the domain vocabulary in {@code 02-config-academica.md} lines 205-226. Both
 * directions are valid at any time — {@code PATCH
 * /program-admission-configs/{id}/status} enforces no sequence. Deliberately
 * distinct from {@link SelectionStatus}, which this status never touches (no
 * use case in this phase changes {@code selectionStatus} — see
 * {@code docs/plans/2026-07-28-program-admission-config.md} §3).
 */
public enum ProgramAdmissionConfigStatus {
	OPEN, CLOSED
}
