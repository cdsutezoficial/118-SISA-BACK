package mx.edu.utez.sisa.academic_config.domain.model;

/**
 * Lifecycle status of an {@link AcademicPlan}. Deliberately mirrors
 * {@link DivisionStatus}/{@link ProgramStatus} (spec: "matching
 * AcademicDivision/AcademicProgram — NOT the domain doc's one-way
 * ACTIVE/DEPRECATED model").
 */
public enum PlanStatus {
	ACTIVE,
	INACTIVE
}
