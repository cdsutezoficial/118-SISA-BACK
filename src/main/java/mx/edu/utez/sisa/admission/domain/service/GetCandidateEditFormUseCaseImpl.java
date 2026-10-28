package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateEditFormUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.AntecedentesEscolares;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.Contacto;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.DatosGenerales;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.Domicilio;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.InformacionComplementaria;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.Ingresos;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.RegisterCandidateCommand;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.SeleccionCarrera;
import mx.edu.utez.sisa.admission.domain.port.out.CandidatePersonRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.shared.model.Address;
import mx.edu.utez.sisa.shared.model.DiversityProfile;
import mx.edu.utez.sisa.shared.model.EmploymentInfo;
import mx.edu.utez.sisa.shared.model.HealthProfile;
import mx.edu.utez.sisa.shared.model.HighSchoolBackground;
import mx.edu.utez.sisa.shared.model.Person;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Arma la ficha completa de un candidato para el formulario de edición.
 *
 * <p>Es la operación inversa de {@link CandidatePersonMapper#apply}: lee lo que
 * el registro guardó en la {@link Person} y lo devuelve con la forma del
 * comando de registro, para que el formulario pueda reenviarlo tal cual.
 */
public class GetCandidateEditFormUseCaseImpl implements GetCandidateEditFormUseCase {

	private final CandidateRepository candidateRepository;

	private final CandidatePersonRepository candidatePersonRepository;

	private final ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort;

	public GetCandidateEditFormUseCaseImpl(CandidateRepository candidateRepository,
			CandidatePersonRepository candidatePersonRepository,
			ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort) {
		this.candidateRepository = candidateRepository;
		this.candidatePersonRepository = candidatePersonRepository;
		this.programAdmissionConfigQueryPort = programAdmissionConfigQueryPort;
	}

	@Override
	@Transactional(readOnly = true)
	public CandidateEditForm get(UUID candidateId) {
		Candidate candidate = candidateRepository.findById(candidateId)
				.orElseThrow(() -> new CandidateNotFoundException("No existe el candidato: " + candidateId));
		Person person = candidatePersonRepository.findById(candidate.getPersonId())
				.orElseThrow(() -> new CandidateNotFoundException(
						"No existe la persona del candidato: " + candidateId));
		ProgramAdmissionConfigQueryPort.AdmissionConfigInfo config = programAdmissionConfigQueryPort
				.findById(candidate.getAdmissionConfigId()).orElse(null);

		return new CandidateEditForm(candidate.getId(), candidate.getFolio(), candidate.getStatus(),
				candidate.isLlaveMxVerified(), config == null ? null : config.programName(),
				config == null ? null : config.modality(), false, toCommand(candidate, person));
	}

	private static RegisterCandidateCommand toCommand(Candidate candidate, Person person) {
		return new RegisterCandidateCommand(datosGenerales(person), domicilio(person.getAddress()),
				new Contacto(person.getPersonalEmail(), person.getHomePhone(), person.getMobilePhone()),
				informacionComplementaria(person.getHealthProfile(), person.getDiversityProfile()),
				ingresos(person), antecedentes(person.getHighSchoolBackground()),
				new SeleccionCarrera(candidate.getAdmissionConfigId(), candidate.getOutreachChannelId(),
						candidate.isFirstChoice()),
				candidate.isLlaveMxVerified());
	}

	private static DatosGenerales datosGenerales(Person p) {
		// Registros anteriores a birth_country guardaban el PAÍS en
		// birth_foreign_state y no guardaban el estado: se leen así.
		boolean legacy = p.getBirthCountry() == null;
		String country = legacy ? p.getBirthForeignState() : p.getBirthCountry();
		String foreignState = legacy ? null : p.getBirthForeignState();
		return new DatosGenerales(p.getCurp(), p.getFirstName(), p.getLastName1(), p.getLastName2(),
				p.getBirthDate(), p.getGender(), p.getNationality(), p.getBirthStateId(), p.getBirthMunicipalityId(),
				country, foreignState, p.getBirthForeignMunicipality(), p.getMaritalStatus(), p.getNativeLanguage(),
				Boolean.TRUE.equals(p.getHasChildren()));
	}

	private static Domicilio domicilio(Address a) {
		if (a == null) {
			return null;
		}
		return new Domicilio(a.getStreet(), a.getExteriorNumber(), a.getInteriorNumber(), a.getNeighborhood(),
				a.getLocality(), a.getPostalCode(), a.getStateId(), a.getMunicipalityId());
	}

	private static InformacionComplementaria informacionComplementaria(HealthProfile h, DiversityProfile d) {
		return new InformacionComplementaria(h != null && h.isHasPreexistingCondition(),
				h == null ? null : h.getConditionDescription(), h != null && h.isHasDisability(),
				h == null ? null : h.getDisabilityDescription(), d != null && d.isParentsSpeakIndigenousLanguage(),
				d == null ? null : d.getParentsIndigenousLanguage(), d != null && d.isSpeaksIndigenousLanguage(),
				d == null ? null : d.getIndigenousLanguage(), d != null && d.isSelfIdentifiesIndigenous(),
				d != null && d.isSelfIdentifiesNonBinary(), d != null && d.isBelongsToLgbttiqCommunity(),
				d != null && d.isAfrodescendant(), d != null && d.isSelfIdentifiesAfrodescendant());
	}

	private static Ingresos ingresos(Person p) {
		EmploymentInfo e = p.getEmploymentInfo();
		return new Ingresos(p.getMonthlyFamilyIncome(), e != null && e.isEmployed(),
				e == null ? null : e.getEmploymentType(), e == null ? null : e.getWorkPhone(),
				e == null ? null : e.getMonthlyIncome(), e == null ? null : e.getCompanyName(),
				e == null ? null : e.getJobTitle(), e == null ? null : e.getWorkStartTime(),
				e == null ? null : e.getWorkEndTime());
	}

	private static AntecedentesEscolares antecedentes(HighSchoolBackground s) {
		if (s == null) {
			return null;
		}
		return new AntecedentesEscolares(s.getSchoolName(), s.getSchoolTypeId(), s.isStudiedInMexico(),
				s.getSchoolStateId(), s.getSchoolMunicipalityId(), s.getForeignCountry(), s.getSchoolForeignState(),
				s.getSchoolCity(), s.getGpa(), s.getCct(), Boolean.TRUE.equals(s.getCctConfirmed()));
	}
}
