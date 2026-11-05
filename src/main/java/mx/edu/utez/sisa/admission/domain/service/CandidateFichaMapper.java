package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.model.CandidateAddress;
import mx.edu.utez.sisa.admission.domain.model.CandidateDiversityProfile;
import mx.edu.utez.sisa.admission.domain.model.CandidateEmploymentInfo;
import mx.edu.utez.sisa.admission.domain.model.CandidateHealthProfile;
import mx.edu.utez.sisa.admission.domain.model.CandidateHighSchoolBackground;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.AntecedentesEscolares;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.Contacto;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.DatosGenerales;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.Domicilio;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.InformacionComplementaria;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.Ingresos;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.RegisterCandidateCommand;

public final class CandidateFichaMapper {

	private CandidateFichaMapper() {
	}

	public static void apply(Candidate candidate, RegisterCandidateCommand command) {
		applyDatosGenerales(candidate, command.datosGenerales());
		applyContacto(candidate, command.contacto());
		candidate.setMonthlyFamilyIncome(command.ingresos() == null ? null : command.ingresos().monthlyFamilyIncome());
		applyDomicilio(candidate, command.domicilio());
		applyInformacionComplementaria(candidate, command.informacionComplementaria());
		applyIngresos(candidate, command.ingresos());
		applyAntecedentes(candidate, command.antecedentesEscolares());
	}

	private static void applyDatosGenerales(Candidate candidate, DatosGenerales datos) {
		candidate.setCurp(datos.curp());
		candidate.setFirstName(datos.firstName());
		candidate.setLastName1(datos.lastName1());
		candidate.setLastName2(datos.lastName2());
		candidate.setBirthDate(datos.birthDate());
		candidate.setGender(datos.gender());
		candidate.setNationality(datos.nationality());
		candidate.setBirthStateId(datos.birthStateId());
		candidate.setBirthMunicipalityId(datos.birthMunicipalityId());
		candidate.setBirthCountry(datos.birthCountry());
		candidate.setBirthForeignState(datos.birthForeignState());
		candidate.setBirthForeignMunicipality(datos.birthForeignMunicipality());
		candidate.setMaritalStatus(datos.maritalStatus());
		candidate.setNativeLanguage(datos.nativeLanguage());
		candidate.setHasChildren(datos.hasChildren());
	}

	private static void applyContacto(Candidate candidate, Contacto contacto) {
		if (contacto == null) {
			return;
		}
		candidate.setPersonalEmail(contacto.personalEmail());
		candidate.setHomePhone(contacto.homePhone());
		candidate.setMobilePhone(contacto.mobilePhone());
	}

	private static void applyDomicilio(Candidate candidate, Domicilio domicilio) {
		if (domicilio == null) {
			return;
		}
		CandidateAddress address = candidate.getAddress();
		if (address == null) {
			candidate.setAddress(new CandidateAddress(domicilio.street(), domicilio.exteriorNumber(),
					domicilio.interiorNumber(), domicilio.neighborhood(), domicilio.locality(), domicilio.postalCode(),
					domicilio.stateId(), domicilio.municipalityId(), null, null));
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

	private static void applyInformacionComplementaria(Candidate candidate, InformacionComplementaria info) {
		if (info == null) {
			return;
		}
		CandidateHealthProfile health = candidate.getHealthProfile();
		if (health == null) {
			candidate.setHealthProfile(new CandidateHealthProfile(info.hasPreexistingCondition(),
					info.conditionDescription(), info.hasDisability(), info.disabilityDescription(), null));
		} else {
			health.setHasPreexistingCondition(info.hasPreexistingCondition());
			health.setConditionDescription(info.conditionDescription());
			health.setHasDisability(info.hasDisability());
			health.setDisabilityDescription(info.disabilityDescription());
		}

		CandidateDiversityProfile diversity = candidate.getDiversityProfile();
		if (diversity == null) {
			candidate.setDiversityProfile(new CandidateDiversityProfile(info.parentsSpeakIndigenousLanguage(),
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

	private static void applyIngresos(Candidate candidate, Ingresos ingresos) {
		if (ingresos == null) {
			return;
		}
		CandidateEmploymentInfo employment = candidate.getEmploymentInfo();
		if (employment == null) {
			candidate.setEmploymentInfo(new CandidateEmploymentInfo(ingresos.isEmployed(), ingresos.employmentType(),
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

	private static void applyAntecedentes(Candidate candidate, AntecedentesEscolares antecedentes) {
		if (antecedentes == null) {
			return;
		}
		CandidateHighSchoolBackground school = candidate.getHighSchoolBackground();
		if (school == null) {
			candidate.setHighSchoolBackground(new CandidateHighSchoolBackground(antecedentes.schoolName(),
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
