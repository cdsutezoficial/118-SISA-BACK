package mx.edu.utez.sisa.admission.domain.model;

/**
 * Lifecycle status of a {@link HighSchoolType}. Deliberately its own enum —
 * same "every aggregate owns its status type" rationale as
 * {@link OutreachChannelStatus}.
 */
public enum HighSchoolTypeStatus {
	ACTIVE,
	INACTIVE
}
