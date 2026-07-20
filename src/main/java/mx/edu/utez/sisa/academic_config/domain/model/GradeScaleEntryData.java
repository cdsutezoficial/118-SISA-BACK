package mx.edu.utez.sisa.academic_config.domain.model;

import java.math.BigDecimal;

/**
 * Input value object carrying one {@link GradeScaleEntry}'s data across the
 * use-case → {@link AcademicPlan} boundary (design.md — Decision: "Boundary
 * enforcement — no path to children except through the plan"). Deliberately
 * NOT a JPA entity and NOT a {@code domain.port.in} DTO — the domain model
 * must not depend on the web/port-in layer, and this carries no identity
 * ({@code id}/{@code scaleId}) since it only exists to describe an entry
 * before {@link GradeScaleEntry} is constructed (mirrors how
 * {@link AcademicPlan#addSubject} takes flat primitives rather than a
 * pre-built {@link Subject}).
 */
public record GradeScaleEntryData(BigDecimal fromValue, BigDecimal toValue, String letter, String description,
		boolean passed) {
}
