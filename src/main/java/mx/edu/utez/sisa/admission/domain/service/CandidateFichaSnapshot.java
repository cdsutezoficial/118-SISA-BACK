package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateAuditPort;
import mx.edu.utez.sisa.shared.model.Address;
import mx.edu.utez.sisa.shared.model.DiversityProfile;
import mx.edu.utez.sisa.shared.model.EmploymentInfo;
import mx.edu.utez.sisa.shared.model.HealthProfile;
import mx.edu.utez.sisa.shared.model.HighSchoolBackground;
import mx.edu.utez.sisa.shared.model.Person;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Los datos editables de la ficha como texto, campo por campo, para comparar
 * antes y después de {@code PUT /candidates/{id}} (F4).
 *
 * <p>La lista es explícita y sigue a {@link CandidatePersonMapper}: un campo
 * nuevo en la ficha se agrega aquí también, y la prueba de esta clase lo
 * recuerda. Los nombres son los del formulario ({@code address.street}, no la
 * columna), porque son los que lee quien revisa el log.
 */
final class CandidateFichaSnapshot {

	private CandidateFichaSnapshot() {
	}

	static Map<String, String> of(Person person, Candidate candidate) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("curp", text(person.getCurp()));
		values.put("firstName", text(person.getFirstName()));
		values.put("lastName1", text(person.getLastName1()));
		values.put("lastName2", text(person.getLastName2()));
		values.put("birthDate", text(person.getBirthDate()));
		values.put("gender", text(person.getGender()));
		values.put("nationality", text(person.getNationality()));
		values.put("birthStateId", text(person.getBirthStateId()));
		values.put("birthMunicipalityId", text(person.getBirthMunicipalityId()));
		values.put("birthCountry", text(person.getBirthCountry()));
		values.put("birthForeignState", text(person.getBirthForeignState()));
		values.put("birthForeignMunicipality", text(person.getBirthForeignMunicipality()));
		values.put("maritalStatus", text(person.getMaritalStatus()));
		values.put("nativeLanguage", text(person.getNativeLanguage()));
		values.put("hasChildren", text(person.getHasChildren()));
		values.put("personalEmail", text(person.getPersonalEmail()));
		values.put("homePhone", text(person.getHomePhone()));
		values.put("mobilePhone", text(person.getMobilePhone()));
		values.put("monthlyFamilyIncome", text(person.getMonthlyFamilyIncome()));
		address(values, person.getAddress());
		health(values, person.getHealthProfile());
		diversity(values, person.getDiversityProfile());
		employment(values, person.getEmploymentInfo());
		school(values, person.getHighSchoolBackground());
		values.put("admissionConfigId", text(candidate.getAdmissionConfigId()));
		values.put("outreachChannelId", text(candidate.getOutreachChannelId()));
		values.put("isFirstChoice", text(candidate.isFirstChoice()));
		return values;
	}

	/** Solo los campos cuyo valor cambió, en el orden del formulario. */
	static List<CandidateAuditPort.Change> diff(Map<String, String> before, Map<String, String> after) {
		List<CandidateAuditPort.Change> changes = new ArrayList<>();
		after.forEach((field, value) -> {
			String previous = before.get(field);
			if (!Objects.equals(previous, value)) {
				changes.add(new CandidateAuditPort.Change(field, previous, value));
			}
		});
		return changes;
	}

	private static void address(Map<String, String> values, Address a) {
		boolean has = a != null;
		values.put("address.street", has ? text(a.getStreet()) : null);
		values.put("address.exteriorNumber", has ? text(a.getExteriorNumber()) : null);
		values.put("address.interiorNumber", has ? text(a.getInteriorNumber()) : null);
		values.put("address.neighborhood", has ? text(a.getNeighborhood()) : null);
		values.put("address.locality", has ? text(a.getLocality()) : null);
		values.put("address.postalCode", has ? text(a.getPostalCode()) : null);
		values.put("address.stateId", has ? text(a.getStateId()) : null);
		values.put("address.municipalityId", has ? text(a.getMunicipalityId()) : null);
	}

	private static void health(Map<String, String> values, HealthProfile h) {
		boolean has = h != null;
		values.put("health.hasPreexistingCondition", has ? text(h.isHasPreexistingCondition()) : null);
		values.put("health.conditionDescription", has ? text(h.getConditionDescription()) : null);
		values.put("health.hasDisability", has ? text(h.isHasDisability()) : null);
		values.put("health.disabilityDescription", has ? text(h.getDisabilityDescription()) : null);
	}

	private static void diversity(Map<String, String> values, DiversityProfile d) {
		boolean has = d != null;
		values.put("diversity.parentsSpeakIndigenousLanguage", has ? text(d.isParentsSpeakIndigenousLanguage()) : null);
		values.put("diversity.speaksIndigenousLanguage", has ? text(d.isSpeaksIndigenousLanguage()) : null);
		values.put("diversity.selfIdentifiesIndigenous", has ? text(d.isSelfIdentifiesIndigenous()) : null);
		values.put("diversity.selfIdentifiesNonBinary", has ? text(d.isSelfIdentifiesNonBinary()) : null);
		values.put("diversity.belongsToLgbttiqCommunity", has ? text(d.isBelongsToLgbttiqCommunity()) : null);
		values.put("diversity.isAfrodescendant", has ? text(d.isAfrodescendant()) : null);
		values.put("diversity.selfIdentifiesAfrodescendant", has ? text(d.isSelfIdentifiesAfrodescendant()) : null);
		values.put("diversity.parentsIndigenousLanguage", has ? text(d.getParentsIndigenousLanguage()) : null);
		values.put("diversity.indigenousLanguage", has ? text(d.getIndigenousLanguage()) : null);
	}

	private static void employment(Map<String, String> values, EmploymentInfo e) {
		boolean has = e != null;
		values.put("employment.isEmployed", has ? text(e.isEmployed()) : null);
		values.put("employment.employmentType", has ? text(e.getEmploymentType()) : null);
		values.put("employment.companyName", has ? text(e.getCompanyName()) : null);
		values.put("employment.jobTitle", has ? text(e.getJobTitle()) : null);
		values.put("employment.workPhone", has ? text(e.getWorkPhone()) : null);
		values.put("employment.monthlyIncome", has ? text(e.getMonthlyIncome()) : null);
		values.put("employment.workStartTime", has ? text(e.getWorkStartTime()) : null);
		values.put("employment.workEndTime", has ? text(e.getWorkEndTime()) : null);
	}

	private static void school(Map<String, String> values, HighSchoolBackground s) {
		boolean has = s != null;
		values.put("school.schoolName", has ? text(s.getSchoolName()) : null);
		values.put("school.schoolCity", has ? text(s.getSchoolCity()) : null);
		values.put("school.schoolTypeId", has ? text(s.getSchoolTypeId()) : null);
		values.put("school.gpa", has ? text(s.getGpa()) : null);
		values.put("school.studiedInMexico", has ? text(s.isStudiedInMexico()) : null);
		values.put("school.schoolStateId", has ? text(s.getSchoolStateId()) : null);
		values.put("school.schoolMunicipalityId", has ? text(s.getSchoolMunicipalityId()) : null);
		values.put("school.cct", has ? text(s.getCct()) : null);
		values.put("school.cctConfirmed", has ? text(s.getCctConfirmed()) : null);
		values.put("school.foreignCountry", has ? text(s.getForeignCountry()) : null);
		values.put("school.schoolForeignState", has ? text(s.getSchoolForeignState()) : null);
	}

	/** {@code 1500} y {@code 1500.00} son el mismo monto: no cuentan como cambio. */
	private static String text(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof BigDecimal decimal) {
			return decimal.stripTrailingZeros().toPlainString();
		}
		return value.toString();
	}
}
