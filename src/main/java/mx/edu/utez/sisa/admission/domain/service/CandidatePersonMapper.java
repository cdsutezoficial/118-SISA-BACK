package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.AntecedentesEscolares;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.Contacto;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.DatosGenerales;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.Domicilio;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.InformacionComplementaria;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.Ingresos;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.RegisterCandidateCommand;
import mx.edu.utez.sisa.shared.model.Address;
import mx.edu.utez.sisa.shared.model.DiversityProfile;
import mx.edu.utez.sisa.shared.model.EmploymentInfo;
import mx.edu.utez.sisa.shared.model.HealthProfile;
import mx.edu.utez.sisa.shared.model.HighSchoolBackground;
import mx.edu.utez.sisa.shared.model.Person;

/**
 * Vuelca los datos de la ficha de admisión sobre una {@link Person}.
 *
 * <p>Lo comparten el registro (persona nueva) y la edición por Servicios
 * Escolares (persona existente), para que los dos caminos guarden exactamente
 * los mismos campos.
 *
 * <p>En la edición los perfiles hijos ({@code Address}, {@code HealthProfile},
 * etc.) se modifican <em>en su lugar</em> en vez de reemplazarse: comparten la
 * llave de la persona ({@code @MapsId}) y un reemplazo haría que Hibernate
 * insertara el nuevo antes de borrar el viejo, chocando por llave duplicada.
 * Además así se conservan los campos que la ficha no captura (tipo de sangre,
 * área académica, etc.).
 */
public final class CandidatePersonMapper {

	private CandidatePersonMapper() {
	}

	/** Persona nueva a partir de la ficha (registro). */
	public static Person newPerson(RegisterCandidateCommand command) {
		DatosGenerales datos = command.datosGenerales();
		Person person = new Person(datos.curp(), datos.firstName(), datos.lastName1(), datos.lastName2(), null);
		apply(person, command);
		return person;
	}

	/** Sobrescribe en {@code person} todo lo que captura la ficha (edición). */
	public static void apply(Person person, RegisterCandidateCommand command) {
		applyDatosGenerales(person, command.datosGenerales());
		applyContacto(person, command.contacto());
		person.setMonthlyFamilyIncome(command.ingresos() == null ? null : command.ingresos().monthlyFamilyIncome());
		applyDomicilio(person, command.domicilio());
		applyInformacionComplementaria(person, command.informacionComplementaria());
		applyIngresos(person, command.ingresos());
		applyAntecedentes(person, command.antecedentesEscolares());
	}

	private static void applyDatosGenerales(Person person, DatosGenerales datos) {
		person.setCurp(datos.curp());
		person.setFirstName(datos.firstName());
		person.setLastName1(datos.lastName1());
		person.setLastName2(datos.lastName2());
		person.setBirthDate(datos.birthDate());
		person.setGender(datos.gender());
		person.setNationality(datos.nationality());
		person.setBirthStateId(datos.birthStateId());
		person.setBirthMunicipalityId(datos.birthMunicipalityId());
		person.setBirthCountry(datos.birthCountry());
		person.setBirthForeignState(datos.birthForeignState());
		person.setBirthForeignMunicipality(datos.birthForeignMunicipality());
		person.setMaritalStatus(datos.maritalStatus());
		person.setNativeLanguage(datos.nativeLanguage());
		person.setHasChildren(datos.hasChildren());
	}

	private static void applyContacto(Person person, Contacto contacto) {
		if (contacto == null) {
			return;
		}
		person.setPersonalEmail(contacto.personalEmail());
		person.setHomePhone(contacto.homePhone());
		person.setMobilePhone(contacto.mobilePhone());
	}

	private static void applyDomicilio(Person person, Domicilio domicilio) {
		if (domicilio == null) {
			return;
		}
		Address address = person.getAddress();
		if (address == null) {
			person.setAddress(new Address(domicilio.street(), domicilio.exteriorNumber(), domicilio.interiorNumber(),
					domicilio.neighborhood(), domicilio.locality(), domicilio.postalCode(), domicilio.stateId(),
					domicilio.municipalityId(), null, null));
			return;
		}
		address.setStreet(domicilio.street());
		address.setExteriorNumber(domicilio.exteriorNumber());
		address.setInteriorNumber(domicilio.interiorNumber());
		address.setNeighborhood(domicilio.neighborhood());
		address.setLocality(domicilio.locality());
		address.setPostalCode(domicilio.postalCode());
		address.setStateId(domicilio.stateId());
		address.setMunicipalityId(domicilio.municipalityId());
	}

	private static void applyInformacionComplementaria(Person person, InformacionComplementaria info) {
		if (info == null) {
			return;
		}
		HealthProfile health = person.getHealthProfile();
		if (health == null) {
			person.setHealthProfile(new HealthProfile(info.hasPreexistingCondition(), info.conditionDescription(),
					info.hasDisability(), info.disabilityDescription(), null));
		} else {
			health.setHasPreexistingCondition(info.hasPreexistingCondition());
			health.setConditionDescription(info.conditionDescription());
			health.setHasDisability(info.hasDisability());
			health.setDisabilityDescription(info.disabilityDescription());
		}

		DiversityProfile diversity = person.getDiversityProfile();
		if (diversity == null) {
			person.setDiversityProfile(new DiversityProfile(info.parentsSpeakIndigenousLanguage(),
					info.speaksIndigenousLanguage(), info.selfIdentifiesIndigenous(), info.selfIdentifiesNonBinary(),
					info.belongsToLgbttiqCommunity(), info.isAfrodescendant(), info.selfIdentifiesAfrodescendant(),
					info.parentsIndigenousLanguage(), info.indigenousLanguage()));
		} else {
			diversity.setParentsSpeakIndigenousLanguage(info.parentsSpeakIndigenousLanguage());
			diversity.setSpeaksIndigenousLanguage(info.speaksIndigenousLanguage());
			diversity.setSelfIdentifiesIndigenous(info.selfIdentifiesIndigenous());
			diversity.setSelfIdentifiesNonBinary(info.selfIdentifiesNonBinary());
			diversity.setBelongsToLgbttiqCommunity(info.belongsToLgbttiqCommunity());
			diversity.setAfrodescendant(info.isAfrodescendant());
			diversity.setSelfIdentifiesAfrodescendant(info.selfIdentifiesAfrodescendant());
			diversity.setParentsIndigenousLanguage(info.parentsIndigenousLanguage());
			diversity.setIndigenousLanguage(info.indigenousLanguage());
		}
	}

	private static void applyIngresos(Person person, Ingresos ingresos) {
		if (ingresos == null) {
			return;
		}
		EmploymentInfo employment = person.getEmploymentInfo();
		if (employment == null) {
			person.setEmploymentInfo(new EmploymentInfo(ingresos.isEmployed(), ingresos.employmentType(),
					ingresos.companyName(), ingresos.jobTitle(), ingresos.workPhone(), ingresos.monthlyIncome(),
					ingresos.workStartTime(), ingresos.workEndTime()));
			return;
		}
		employment.setEmployed(ingresos.isEmployed());
		employment.setEmploymentType(ingresos.employmentType());
		employment.setCompanyName(ingresos.companyName());
		employment.setJobTitle(ingresos.jobTitle());
		employment.setWorkPhone(ingresos.workPhone());
		employment.setMonthlyIncome(ingresos.monthlyIncome());
		employment.setWorkStartTime(ingresos.workStartTime());
		employment.setWorkEndTime(ingresos.workEndTime());
	}

	private static void applyAntecedentes(Person person, AntecedentesEscolares antecedentes) {
		if (antecedentes == null) {
			return;
		}
		HighSchoolBackground school = person.getHighSchoolBackground();
		if (school == null) {
			person.setHighSchoolBackground(new HighSchoolBackground(antecedentes.schoolName(),
					antecedentes.schoolCity(), antecedentes.schoolTypeId(), antecedentes.gpa(),
					antecedentes.studiedInMexico(), antecedentes.schoolStateId(), antecedentes.schoolMunicipalityId(),
					antecedentes.cct(), antecedentes.cctConfirmed(), antecedentes.foreignCountry(),
					antecedentes.schoolForeignState(), null, null));
			return;
		}
		school.setSchoolName(antecedentes.schoolName());
		school.setSchoolCity(antecedentes.schoolCity());
		school.setSchoolTypeId(antecedentes.schoolTypeId());
		school.setGpa(antecedentes.gpa());
		school.setStudiedInMexico(antecedentes.studiedInMexico());
		school.setSchoolStateId(antecedentes.schoolStateId());
		school.setSchoolMunicipalityId(antecedentes.schoolMunicipalityId());
		school.setCct(antecedentes.cct());
		school.setCctConfirmed(antecedentes.cctConfirmed());
		school.setForeignCountry(antecedentes.foreignCountry());
		school.setSchoolForeignState(antecedentes.schoolForeignState());
	}
}
