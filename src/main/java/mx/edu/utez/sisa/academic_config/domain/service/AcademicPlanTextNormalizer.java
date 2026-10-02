package mx.edu.utez.sisa.academic_config.domain.service;

final class AcademicPlanTextNormalizer {

	private AcademicPlanTextNormalizer() {
	}

	static String required(String value) {
		return value.trim();
	}
}