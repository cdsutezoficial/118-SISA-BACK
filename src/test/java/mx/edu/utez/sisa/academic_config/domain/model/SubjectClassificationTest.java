package mx.edu.utez.sisa.academic_config.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubjectClassificationTest {

	@Test
	void constructor_defaultsToActiveStatus() {
		SubjectClassification classification = newClassification();

		assertThat(classification.getStatus()).isEqualTo(ClassificationStatus.ACTIVE);
	}

	@Test
	void constructor_setsNameAndCode() {
		SubjectClassification classification = newClassification();

		assertThat(classification.getName()).isEqualTo("Integradora");
		assertThat(classification.getCode()).isEqualTo("INT");
	}

	@Test
	void deactivate_transitionsAnActiveClassificationToInactive() {
		SubjectClassification classification = newClassification();

		classification.deactivate();

		assertThat(classification.getStatus()).isEqualTo(ClassificationStatus.INACTIVE);
	}

	@Test
	void deactivate_isIdempotentOnAnAlreadyInactiveClassification() {
		SubjectClassification classification = newClassification();
		classification.deactivate();

		classification.deactivate();

		assertThat(classification.getStatus()).isEqualTo(ClassificationStatus.INACTIVE);
	}

	@Test
	void activate_transitionsAnInactiveClassificationToActive() {
		SubjectClassification classification = newClassification();
		classification.deactivate();

		classification.activate();

		assertThat(classification.getStatus()).isEqualTo(ClassificationStatus.ACTIVE);
	}

	@Test
	void activate_isIdempotentOnAnAlreadyActiveClassification() {
		SubjectClassification classification = newClassification();

		classification.activate();

		assertThat(classification.getStatus()).isEqualTo(ClassificationStatus.ACTIVE);
	}

	private SubjectClassification newClassification() {
		return new SubjectClassification("Integradora", "INT");
	}
}
