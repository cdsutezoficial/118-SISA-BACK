package mx.edu.utez.sisa.academic_config.domain.model;

import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateGradeScaleException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateLevelNumberException;
import mx.edu.utez.sisa.academic_config.shared.exception.DuplicateSubjectCodeException;
import mx.edu.utez.sisa.academic_config.shared.exception.GradeScaleNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.InvalidGradeScaleEntriesException;
import mx.edu.utez.sisa.academic_config.shared.exception.PlanLevelHasSubjectsException;
import mx.edu.utez.sisa.academic_config.shared.exception.PlanLevelInUseException;
import mx.edu.utez.sisa.academic_config.shared.exception.PlanLevelNotFoundException;
import mx.edu.utez.sisa.academic_config.shared.exception.SubjectNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AcademicPlanTest {

	@Test
	void constructor_defaultsToActiveStatus() {
		AcademicPlan plan = newPlan();

		assertThat(plan.getStatus()).isEqualTo(PlanStatus.ACTIVE);
	}

	@Test
	void deactivate_transitionsActiveToInactive() {
		AcademicPlan plan = newPlan();

		plan.deactivate();

		assertThat(plan.getStatus()).isEqualTo(PlanStatus.INACTIVE);
	}

	@Test
	void deactivate_isIdempotentWhenAlreadyInactive() {
		AcademicPlan plan = newPlan();
		plan.deactivate();

		plan.deactivate();

		assertThat(plan.getStatus()).isEqualTo(PlanStatus.INACTIVE);
	}

	@Test
	void activate_transitionsInactiveToActive() {
		AcademicPlan plan = newPlan();
		plan.deactivate();

		plan.activate();

		assertThat(plan.getStatus()).isEqualTo(PlanStatus.ACTIVE);
	}

	@Test
	void activate_isIdempotentWhenAlreadyActive() {
		AcademicPlan plan = newPlan();

		plan.activate();

		assertThat(plan.getStatus()).isEqualTo(PlanStatus.ACTIVE);
	}

	@Test
	void updateDetails_changesFieldsButNotStatus() {
		AcademicPlan plan = newPlan();
		plan.deactivate();
		UUID levelId = UUID.randomUUID();

		plan.updateDetails("2023-A", "Enero 2023", "TIT-002", LocalDate.of(2023, 1, 1), 7,
				BigDecimal.valueOf(7.0), 2, true, levelId);

		assertThat(plan.getVersion()).isEqualTo("2023-A");
		assertThat(plan.getValidityPeriod()).isEqualTo("Enero 2023");
		assertThat(plan.getTitulationKey()).isEqualTo("TIT-002");
		assertThat(plan.getEffectiveFrom()).isEqualTo(LocalDate.of(2023, 1, 1));
		assertThat(plan.getTotalLevels()).isEqualTo(7);
		assertThat(plan.getMinPassingGrade()).isEqualByComparingTo(BigDecimal.valueOf(7.0));
		assertThat(plan.getMaxExtraordinaryExamsPerPeriod()).isEqualTo(2);
		assertThat(plan.isRequiresSocialService()).isTrue();
		assertThat(plan.getSocialServiceMinLevelId()).isEqualTo(levelId);
		assertThat(plan.getStatus()).isEqualTo(PlanStatus.INACTIVE);
	}

	@Test
	void addLevel_addsLevelToPlan() {
		AcademicPlan plan = newPlan();

		PlanLevel level = plan.addLevel(1, PlanLevelType.REGULAR, "Primer cuatrimestre");

		assertThat(plan.getLevels()).containsExactly(level);
		assertThat(level.getLevelNumber()).isEqualTo(1);
		assertThat(level.getType()).isEqualTo(PlanLevelType.REGULAR);
		assertThat(level.getDescription()).isEqualTo("Primer cuatrimestre");
	}

	@Test
	void addLevel_rejectsDuplicateLevelNumberWithinTheSamePlan() {
		AcademicPlan plan = newPlan();
		plan.addLevel(3, PlanLevelType.REGULAR, null);

		assertThatThrownBy(() -> plan.addLevel(3, PlanLevelType.INTERNSHIP, null))
				.isInstanceOf(DuplicateLevelNumberException.class);
	}

	@Test
	void updateLevel_rejectsUnknownLevelId() {
		AcademicPlan plan = newPlan();
		UUID unknownLevelId = UUID.randomUUID();

		assertThatThrownBy(() -> plan.updateLevel(unknownLevelId, 1, PlanLevelType.REGULAR, null))
				.isInstanceOf(PlanLevelNotFoundException.class);
	}

	@Test
	void updateLevel_rejectsDuplicateLevelNumberAgainstAnotherLevel() {
		AcademicPlan plan = newPlan();
		plan.addLevel(1, PlanLevelType.REGULAR, null);
		PlanLevel levelTwo = plan.addLevel(2, PlanLevelType.REGULAR, null);
		UUID levelTwoId = UUID.randomUUID();
		ReflectionTestUtils.setField(levelTwo, "id", levelTwoId);

		assertThatThrownBy(() -> plan.updateLevel(levelTwoId, 1, PlanLevelType.REGULAR, null))
				.isInstanceOf(DuplicateLevelNumberException.class);
	}

	@Test
	void removeLevel_succeedsWhenEmptyAndNotReferencedBySocialService() {
		AcademicPlan plan = newPlan();
		PlanLevel level = plan.addLevel(1, PlanLevelType.REGULAR, null);
		UUID levelId = UUID.randomUUID();
		ReflectionTestUtils.setField(level, "id", levelId);

		plan.removeLevel(levelId);

		assertThat(plan.getLevels()).isEmpty();
	}

	@Test
	void removeLevel_rejectsWhenLevelStillHasSubjects() {
		AcademicPlan plan = newPlan();
		PlanLevel level = plan.addLevel(1, PlanLevelType.REGULAR, null);
		UUID levelId = UUID.randomUUID();
		ReflectionTestUtils.setField(level, "id", levelId);
		plan.addSubject(levelId, "MAT101", "Matematicas I", 5, 4, 3, 1, SubjectType.CORE, true,
				UUID.randomUUID());

		assertThatThrownBy(() -> plan.removeLevel(levelId)).isInstanceOf(PlanLevelHasSubjectsException.class);
	}

	@Test
	void removeLevel_rejectsWhenLevelIsSocialServiceMinLevel() {
		AcademicPlan plan = newPlan();
		PlanLevel level = plan.addLevel(1, PlanLevelType.REGULAR, null);
		UUID levelId = UUID.randomUUID();
		ReflectionTestUtils.setField(level, "id", levelId);
		ReflectionTestUtils.setField(plan, "socialServiceMinLevelId", levelId);

		assertThatThrownBy(() -> plan.removeLevel(levelId)).isInstanceOf(PlanLevelInUseException.class);
	}

	@Test
	void removeLevel_rejectsUnknownLevelId() {
		AcademicPlan plan = newPlan();

		assertThatThrownBy(() -> plan.removeLevel(UUID.randomUUID()))
				.isInstanceOf(PlanLevelNotFoundException.class);
	}

	@Test
	void addSubject_addsSubjectUnderTheGivenLevel() {
		AcademicPlan plan = newPlan();
		PlanLevel level = plan.addLevel(1, PlanLevelType.REGULAR, null);
		UUID levelId = UUID.randomUUID();
		ReflectionTestUtils.setField(level, "id", levelId);
		UUID classificationId = UUID.randomUUID();

		Subject subject = plan.addSubject(levelId, "MAT101", "Matematicas I", 5, 4, 3, 1, SubjectType.CORE, true,
				classificationId);

		assertThat(level.getSubjects()).containsExactly(subject);
		assertThat(subject.getCode()).isEqualTo("MAT101");
		assertThat(subject.getClassificationId()).isEqualTo(classificationId);
		assertThat(subject.isRetakeable()).isTrue();
	}

	@Test
	void addSubject_rejectsDuplicateCodeWithinTheSamePlan() {
		AcademicPlan plan = newPlan();
		PlanLevel levelOne = plan.addLevel(1, PlanLevelType.REGULAR, null);
		UUID levelOneId = UUID.randomUUID();
		ReflectionTestUtils.setField(levelOne, "id", levelOneId);
		PlanLevel levelTwo = plan.addLevel(2, PlanLevelType.REGULAR, null);
		UUID levelTwoId = UUID.randomUUID();
		ReflectionTestUtils.setField(levelTwo, "id", levelTwoId);
		plan.addSubject(levelOneId, "MAT101", "Matematicas I", 5, 4, 3, 1, SubjectType.CORE, true, UUID.randomUUID());

		assertThatThrownBy(() -> plan.addSubject(levelTwoId, "MAT101", "Otra materia", 5, 4, 3, 1, SubjectType.CORE,
				true, UUID.randomUUID())).isInstanceOf(DuplicateSubjectCodeException.class);
	}

	@Test
	void addSubject_rejectsLevelIdBelongingToAnotherPlan() {
		AcademicPlan planA = newPlan();
		AcademicPlan planB = newPlan();
		PlanLevel levelOfB = planB.addLevel(1, PlanLevelType.REGULAR, null);
		UUID levelOfBId = UUID.randomUUID();
		ReflectionTestUtils.setField(levelOfB, "id", levelOfBId);

		assertThatThrownBy(() -> planA.addSubject(levelOfBId, "MAT101", "Matematicas I", 5, 4, 3, 1,
				SubjectType.CORE, true, UUID.randomUUID())).isInstanceOf(PlanLevelNotFoundException.class);
	}

	@Test
	void updateSubject_rejectsUnknownSubjectId() {
		AcademicPlan plan = newPlan();

		assertThatThrownBy(() -> plan.updateSubject(UUID.randomUUID(), "MAT101", "Matematicas I", 5, 4, 3, 1,
				SubjectType.CORE, true, UUID.randomUUID())).isInstanceOf(SubjectNotFoundException.class);
	}

	@Test
	void updateSubject_rejectsDuplicateCodeAgainstAnotherSubject() {
		AcademicPlan plan = newPlan();
		PlanLevel level = plan.addLevel(1, PlanLevelType.REGULAR, null);
		UUID levelId = UUID.randomUUID();
		ReflectionTestUtils.setField(level, "id", levelId);
		plan.addSubject(levelId, "MAT101", "Matematicas I", 5, 4, 3, 1, SubjectType.CORE, true, UUID.randomUUID());
		Subject subjectTwo = plan.addSubject(levelId, "MAT102", "Matematicas II", 5, 4, 3, 2, SubjectType.CORE, true,
				UUID.randomUUID());
		UUID subjectTwoId = UUID.randomUUID();
		ReflectionTestUtils.setField(subjectTwo, "id", subjectTwoId);

		assertThatThrownBy(() -> plan.updateSubject(subjectTwoId, "MAT101", "Matematicas II", 5, 4, 3, 2,
				SubjectType.CORE, true, UUID.randomUUID())).isInstanceOf(DuplicateSubjectCodeException.class);
	}

	@Test
	void removeSubject_removesItFromItsLevel() {
		AcademicPlan plan = newPlan();
		PlanLevel level = plan.addLevel(1, PlanLevelType.REGULAR, null);
		UUID levelId = UUID.randomUUID();
		ReflectionTestUtils.setField(level, "id", levelId);
		Subject subject = plan.addSubject(levelId, "MAT101", "Matematicas I", 5, 4, 3, 1, SubjectType.CORE, true,
				UUID.randomUUID());
		UUID subjectId = UUID.randomUUID();
		ReflectionTestUtils.setField(subject, "id", subjectId);

		plan.removeSubject(subjectId);

		assertThat(level.getSubjects()).isEmpty();
	}

	@Test
	void removeSubject_rejectsUnknownSubjectId() {
		AcademicPlan plan = newPlan();

		assertThatThrownBy(() -> plan.removeSubject(UUID.randomUUID())).isInstanceOf(SubjectNotFoundException.class);
	}

	@Test
	void hasLevel_trueForItsOwnLevelAndFalseForAnUnknownId() {
		AcademicPlan plan = newPlan();
		PlanLevel level = plan.addLevel(1, PlanLevelType.REGULAR, null);
		UUID levelId = UUID.randomUUID();
		ReflectionTestUtils.setField(level, "id", levelId);

		assertThat(plan.hasLevel(levelId)).isTrue();
		assertThat(plan.hasLevel(UUID.randomUUID())).isFalse();
	}

	@Test
	void setGradeScale_createsScaleWithExactCoverage() {
		AcademicPlan plan = newPlan();
		UUID classificationId = UUID.randomUUID();

		GradeScale scale = plan.setGradeScale(classificationId, BigDecimal.valueOf(0), BigDecimal.valueOf(100),
				List.of(entry(0, 69.9, "NP", "No competente", false), entry(70.0, 100, "CO", "Competente", true)));

		assertThat(plan.getGradeScales()).containsExactly(scale);
		assertThat(scale.getClassificationId()).isEqualTo(classificationId);
		assertThat(scale.getEntries()).hasSize(2);
	}

	@Test
	void setGradeScale_singleEntryCoveringWholeRangePasses() {
		AcademicPlan plan = newPlan();

		GradeScale scale = plan.setGradeScale(UUID.randomUUID(), BigDecimal.valueOf(0), BigDecimal.valueOf(100),
				List.of(entry(0, 100, "CO", "Competente", true)));

		assertThat(scale.getEntries()).hasSize(1);
	}

	@Test
	void setGradeScale_boundaryAdjacentEntriesPass() {
		AcademicPlan plan = newPlan();

		GradeScale scale = plan.setGradeScale(UUID.randomUUID(), BigDecimal.valueOf(0), BigDecimal.valueOf(100),
				List.of(entry(0, 69.9, "NP", "No competente", false), entry(70.0, 100, "CO", "Competente", true)));

		assertThat(scale.getEntries()).hasSize(2);
	}

	@Test
	void setGradeScale_rejectsAGapBetweenEntries() {
		AcademicPlan plan = newPlan();

		assertThatThrownBy(() -> plan.setGradeScale(UUID.randomUUID(), BigDecimal.valueOf(0), BigDecimal.valueOf(100),
				List.of(entry(0, 60, "NP", "No competente", false), entry(70, 100, "CO", "Competente", true))))
				.isInstanceOf(InvalidGradeScaleEntriesException.class).hasMessageContaining("Gap");
	}

	@Test
	void setGradeScale_rejectsAnOverlapBetweenEntries() {
		AcademicPlan plan = newPlan();

		assertThatThrownBy(() -> plan.setGradeScale(UUID.randomUUID(), BigDecimal.valueOf(0), BigDecimal.valueOf(100),
				List.of(entry(0, 75, "NP", "No competente", false), entry(70, 100, "CO", "Competente", true))))
				.isInstanceOf(InvalidGradeScaleEntriesException.class).hasMessageContaining("Overlap");
	}

	@Test
	void setGradeScale_rejectsEntriesNotStartingAtNumericMin() {
		AcademicPlan plan = newPlan();

		assertThatThrownBy(() -> plan.setGradeScale(UUID.randomUUID(), BigDecimal.valueOf(0), BigDecimal.valueOf(100),
				List.of(entry(1, 100, "CO", "Competente", true)))).isInstanceOf(InvalidGradeScaleEntriesException.class);
	}

	@Test
	void setGradeScale_rejectsEntriesNotEndingAtNumericMax() {
		AcademicPlan plan = newPlan();

		assertThatThrownBy(() -> plan.setGradeScale(UUID.randomUUID(), BigDecimal.valueOf(0), BigDecimal.valueOf(100),
				List.of(entry(0, 99, "CO", "Competente", true)))).isInstanceOf(InvalidGradeScaleEntriesException.class);
	}

	@Test
	void setGradeScale_rejectsEmptyEntries() {
		AcademicPlan plan = newPlan();

		assertThatThrownBy(
				() -> plan.setGradeScale(UUID.randomUUID(), BigDecimal.valueOf(0), BigDecimal.valueOf(100), List.of()))
				.isInstanceOf(InvalidGradeScaleEntriesException.class);
	}

	@Test
	void setGradeScale_rejectsDuplicateClassificationWithinTheSamePlan() {
		AcademicPlan plan = newPlan();
		UUID classificationId = UUID.randomUUID();
		plan.setGradeScale(classificationId, BigDecimal.valueOf(0), BigDecimal.valueOf(100),
				List.of(entry(0, 100, "CO", "Competente", true)));

		assertThatThrownBy(() -> plan.setGradeScale(classificationId, BigDecimal.valueOf(0), BigDecimal.valueOf(10),
				List.of(entry(0, 10, "AP", "Aprobado", true)))).isInstanceOf(DuplicateGradeScaleException.class);
	}

	@Test
	void updateGradeScale_replacesClassificationRangeAndEntries() {
		AcademicPlan plan = newPlan();
		GradeScale scale = plan.setGradeScale(UUID.randomUUID(), BigDecimal.valueOf(0), BigDecimal.valueOf(100),
				List.of(entry(0, 100, "CO", "Competente", true)));
		UUID scaleId = UUID.randomUUID();
		ReflectionTestUtils.setField(scale, "id", scaleId);
		UUID newClassificationId = UUID.randomUUID();

		plan.updateGradeScale(scaleId, newClassificationId, BigDecimal.valueOf(0), BigDecimal.valueOf(10),
				List.of(entry(0, 6.9, "NA", "No aprobado", false), entry(7.0, 10, "AP", "Aprobado", true)));

		assertThat(scale.getClassificationId()).isEqualTo(newClassificationId);
		assertThat(scale.getNumericMax()).isEqualByComparingTo(BigDecimal.valueOf(10));
		assertThat(scale.getEntries()).hasSize(2);
	}

	@Test
	void updateGradeScale_rejectsUnknownScaleId() {
		AcademicPlan plan = newPlan();

		assertThatThrownBy(() -> plan.updateGradeScale(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(0),
				BigDecimal.valueOf(100), List.of(entry(0, 100, "CO", "Competente", true))))
				.isInstanceOf(GradeScaleNotFoundException.class);
	}

	@Test
	void updateGradeScale_rejectsDuplicateClassificationAgainstAnotherScale() {
		AcademicPlan plan = newPlan();
		GradeScale scaleOne = plan.setGradeScale(UUID.randomUUID(), BigDecimal.valueOf(0), BigDecimal.valueOf(100),
				List.of(entry(0, 100, "CO", "Competente", true)));
		UUID scaleOneId = UUID.randomUUID();
		ReflectionTestUtils.setField(scaleOne, "id", scaleOneId);
		UUID classificationTwo = UUID.randomUUID();
		GradeScale scaleTwo = plan.setGradeScale(classificationTwo, BigDecimal.valueOf(0), BigDecimal.valueOf(10),
				List.of(entry(0, 10, "AP", "Aprobado", true)));
		ReflectionTestUtils.setField(scaleTwo, "id", UUID.randomUUID());

		assertThatThrownBy(() -> plan.updateGradeScale(scaleOneId, classificationTwo, BigDecimal.valueOf(0),
				BigDecimal.valueOf(100), List.of(entry(0, 100, "CO", "Competente", true))))
				.isInstanceOf(DuplicateGradeScaleException.class);
	}

	@Test
	void removeGradeScale_succeeds() {
		AcademicPlan plan = newPlan();
		GradeScale scale = plan.setGradeScale(UUID.randomUUID(), BigDecimal.valueOf(0), BigDecimal.valueOf(100),
				List.of(entry(0, 100, "CO", "Competente", true)));
		UUID scaleId = UUID.randomUUID();
		ReflectionTestUtils.setField(scale, "id", scaleId);

		plan.removeGradeScale(scaleId);

		assertThat(plan.getGradeScales()).isEmpty();
	}

	@Test
	void removeGradeScale_rejectsUnknownScaleId() {
		AcademicPlan plan = newPlan();

		assertThatThrownBy(() -> plan.removeGradeScale(UUID.randomUUID()))
				.isInstanceOf(GradeScaleNotFoundException.class);
	}

	/**
	 * PO-confirmed real example (José, 2026-07-20 follow-up): a 4-tier decimal
	 * scale with a {@code 0.1} step between adjacent entries must pass
	 * validation exactly as documented.
	 */
	@Test
	void setGradeScale_decimalFourTierScalePasses() {
		AcademicPlan plan = newPlan();

		GradeScale scale = plan.setGradeScale(UUID.randomUUID(), new BigDecimal("7.0"), new BigDecimal("10.0"),
				List.of(entry(7.0, 7.5, "X", "Excelente", true), entry(7.6, 8.5, "Y", "Muy bien", true),
						entry(8.6, 9.5, "Z", "Bien", true), entry(9.6, 10.0, "W", "Sobresaliente", true)));

		assertThat(scale.getEntries()).hasSize(4);
	}

	@Test
	void setGradeScale_decimalBoundaryOffByOneTenthIsAGap() {
		AcademicPlan plan = newPlan();

		assertThatThrownBy(() -> plan.setGradeScale(UUID.randomUUID(), new BigDecimal("7.0"), new BigDecimal("10.0"),
				List.of(entry(7.0, 7.5, "X", "Excelente", true), entry(7.7, 8.5, "Y", "Muy bien", true),
						entry(8.6, 9.5, "Z", "Bien", true), entry(9.6, 10.0, "W", "Sobresaliente", true))))
				.isInstanceOf(InvalidGradeScaleEntriesException.class).hasMessageContaining("Gap");
	}

	@Test
	void setGradeScale_decimalOverlapAtSharedBoundaryIsRejected() {
		AcademicPlan plan = newPlan();

		assertThatThrownBy(() -> plan.setGradeScale(UUID.randomUUID(), new BigDecimal("7.0"), new BigDecimal("10.0"),
				List.of(entry(7.0, 7.5, "X", "Excelente", true), entry(7.5, 8.5, "Y", "Muy bien", true),
						entry(8.6, 9.5, "Z", "Bien", true), entry(9.6, 10.0, "W", "Sobresaliente", true))))
				.isInstanceOf(InvalidGradeScaleEntriesException.class).hasMessageContaining("Overlap");
	}

	/**
	 * Robustness check: {@link GradeScale#validateEntries} compares boundaries
	 * with {@code BigDecimal.compareTo} (numeric, scale-insensitive), not
	 * {@code equals} (scale-sensitive) — a trailing-zero boundary like
	 * {@code "7.50"} must not be mistaken for a gap against an adjacent
	 * {@code "7.6"}.
	 */
	@Test
	void setGradeScale_toleratesTrailingZeroScaleDifferenceBetweenAdjacentEntries() {
		AcademicPlan plan = newPlan();

		GradeScale scale = plan.setGradeScale(UUID.randomUUID(), new BigDecimal("7.0"), new BigDecimal("10.0"),
				List.of(
						new GradeScaleEntryData(new BigDecimal("7.0"), new BigDecimal("7.50"), "X", "Excelente", true),
						new GradeScaleEntryData(new BigDecimal("7.6"), new BigDecimal("8.5"), "Y", "Muy bien", true),
						entry(8.6, 9.5, "Z", "Bien", true), entry(9.6, 10.0, "W", "Sobresaliente", true)));

		assertThat(scale.getEntries()).hasSize(4);
	}

	private static GradeScaleEntryData entry(double from, double to, String letter, String description, boolean passed) {
		return new GradeScaleEntryData(BigDecimal.valueOf(from), BigDecimal.valueOf(to), letter, description, passed);
	}

	private AcademicPlan newPlan() {
		return new AcademicPlan(UUID.randomUUID(), "2022-A", "Septiembre 2022", "TIT-001", LocalDate.of(2022, 9, 1),
				6, BigDecimal.valueOf(6.0), 3, false, null);
	}
}
