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

	private SubjectClassification newClassification() {
		return new SubjectClassification("Integradora", "INT");
	}
}
