package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.model.HighSchoolType;
import mx.edu.utez.sisa.admission.domain.model.OutreachChannel;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateFichaUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.AdmissionPaymentRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidatePersonRepository;
import mx.edu.utez.sisa.admission.domain.port.out.CandidateRepository;
import mx.edu.utez.sisa.admission.domain.port.out.HighSchoolTypeRepository;
import mx.edu.utez.sisa.admission.domain.port.out.OutreachChannelRepository;
import mx.edu.utez.sisa.admission.domain.port.out.PlaceNameLookupPort;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort.AdmissionConfigInfo;
import mx.edu.utez.sisa.shared.model.Address;
import mx.edu.utez.sisa.shared.model.DiversityProfile;
import mx.edu.utez.sisa.shared.model.EmploymentInfo;
import mx.edu.utez.sisa.shared.model.HealthProfile;
import mx.edu.utez.sisa.shared.model.HighSchoolBackground;
import mx.edu.utez.sisa.shared.model.Person;
import mx.edu.utez.sisa.shared.model.ProgramModality;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Read-only assembler behind {@code GET /candidates/{id}}, the ficha PDF and
 * the payment emails (see {@link GetCandidateFichaUseCase} for the consumer
 * map). No write access — a pure query interactor, so it is safe to call from
 * the public portal. Missing {@code Candidate} or its {@code ADMISSION_FICHA}
 * payment yields an empty optional (the web layer maps to 404); catalog names
 * resolve through minimal lookup ports and fall back to {@code null} if a
 * reference row disappears (the PDF renders the "—" fallback, the emails only
 * consume the always-present core fields).
 *
 * <p>Fase 7: the assembler now loads the {@code Person} child profiles
 * (address, health, diversity, employment, high-school background — reached
 * via the lazy {@code @OneToOne} getters) and resolves every catalog id to its
 * display name (state/municipality via {@link PlaceNameLookupPort}; program
 * modality + period via {@link ProgramAdmissionConfigQueryPort}; outreach
 * channel and high-school type via their repositories), so the ficha PDF
 * (Fase 8) can mirror {@code CandidatoRegistro.tsx} without id plumbing.
 */
public class GetCandidateFichaUseCaseImpl implements GetCandidateFichaUseCase {

	private final CandidateRepository candidateRepository;

	private final CandidatePersonRepository candidatePersonRepository;

	private final AdmissionPaymentRepository admissionPaymentRepository;

	private final ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort;

	private final PlaceNameLookupPort placeNameLookupPort;

	private final OutreachChannelRepository outreachChannelRepository;

	private final HighSchoolTypeRepository highSchoolTypeRepository;

	public GetCandidateFichaUseCaseImpl(CandidateRepository candidateRepository,
			CandidatePersonRepository candidatePersonRepository,
			AdmissionPaymentRepository admissionPaymentRepository,
			ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort, PlaceNameLookupPort placeNameLookupPort,
			OutreachChannelRepository outreachChannelRepository, HighSchoolTypeRepository highSchoolTypeRepository) {
		this.candidateRepository = candidateRepository;
		this.candidatePersonRepository = candidatePersonRepository;
		this.admissionPaymentRepository = admissionPaymentRepository;
		this.programAdmissionConfigQueryPort = programAdmissionConfigQueryPort;
		this.placeNameLookupPort = placeNameLookupPort;
		this.outreachChannelRepository = outreachChannelRepository;
		this.highSchoolTypeRepository = highSchoolTypeRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public FichaData get(UUID candidateId) {
		return candidateRepository.findById(candidateId)
				.flatMap(this::assemble)
				.orElse(null);
	}

	private java.util.Optional<FichaData> assemble(Candidate candidate) {
		AdmissionPayment payment = admissionPaymentRepository.findByCandidateId(candidate.getId()).orElse(null);
		if (payment == null) {
			return java.util.Optional.empty();
		}
		Person person = candidatePersonRepository.findById(candidate.getPersonId()).orElse(null);
		if (person == null) {
			return java.util.Optional.empty();
		}
		AdmissionConfigInfo config = programAdmissionConfigQueryPort.findById(candidate.getAdmissionConfigId())
				.orElse(null);
		String programName = config == null ? null : config.programName();
		String modalityName = modalityLabel(config == null ? null : config.modality());
		String periodName = config == null ? null : config.periodName();
		String outreachChannelName = resolveOutreachChannel(candidate.getOutreachChannelId());
		Address address = person.getAddress();
		HealthProfile health = person.getHealthProfile();
		DiversityProfile diversity = person.getDiversityProfile();
		EmploymentInfo employment = person.getEmploymentInfo();
		HighSchoolBackground school = person.getHighSchoolBackground();
		return java.util.Optional.of(new FichaData(candidate.getId(), candidate.getFolio(), candidate.getStatus(),
				candidate.getRegisteredAt(), candidate.getAdmissionConfigId(), programName, person.getCurp(),
				person.getFirstName(), person.getLastName1(), person.getLastName2(), person.getPersonalEmail(),
				person.getHomePhone(), person.getMobilePhone(), payment.getReferenceNumber(), payment.getAmount(),
				payment.getPaymentDeadline(), payment.getPaymentStatus(), payment.getReceiptNumber(), payment.getPaidAt(),
				payment.getOrderId(),
				new FichaData.DatosGenerales(person.getBirthDate(), person.getGender(), person.getNationality(),
						resolveState(person.getBirthStateId()), resolveMunicipality(person.getBirthMunicipalityId()),
						person.getMaritalStatus(), person.getNativeLanguage(), Boolean.TRUE.equals(person.getHasChildren())),
				address == null ? emptyDomicilio()
						: new FichaData.Domicilio(address.getStreet(), address.getExteriorNumber(),
								address.getInteriorNumber(), address.getNeighborhood(), address.getLocality(),
								address.getPostalCode(), resolveState(address.getStateId()),
								resolveMunicipality(address.getMunicipalityId())),
				health == null ? emptyInformacionComplementaria()
						: new FichaData.InformacionComplementaria(health.isHasPreexistingCondition(),
								health.getConditionDescription(), health.isHasDisability(),
								health.getDisabilityDescription(),
								diversity != null && diversity.isParentsSpeakIndigenousLanguage(),
								diversity == null ? null : diversity.getParentsIndigenousLanguage(),
								diversity != null && diversity.isSpeaksIndigenousLanguage(),
								diversity == null ? null : diversity.getIndigenousLanguage(),
								diversity != null && diversity.isSelfIdentifiesIndigenous(),
								diversity != null && diversity.isSelfIdentifiesNonBinary(),
								diversity != null && diversity.isBelongsToLgbttiqCommunity(),
								diversity != null && diversity.isAfrodescendant(),
								diversity != null && diversity.isSelfIdentifiesAfrodescendant()),
				employment == null ? emptyIngresos()
						: new FichaData.Ingresos(person.getMonthlyFamilyIncome(), employment.isEmployed(),
								employment.getEmploymentType(), employment.getWorkPhone(),
								employment.getMonthlyIncome(), employment.getCompanyName(), employment.getJobTitle(),
								employment.getWorkStartTime(), employment.getWorkEndTime()),
				new FichaData.SeleccionCarrera(modalityName, outreachChannelName, candidate.isFirstChoice(),
						periodName),
				school == null ? emptyAntecedentesEscolares()
						: new FichaData.AntecedentesEscolares(school.getSchoolName(),
								resolveSchoolType(school.getSchoolTypeId()), school.isStudiedInMexico(),
								resolveState(school.getSchoolStateId()),
								resolveMunicipality(school.getSchoolMunicipalityId()), school.getForeignCountry(),
								school.getSchoolCity(), school.getGpa(), school.getCct())));
	}

	private String resolveState(UUID stateId) {
		return stateId == null ? null : placeNameLookupPort.findStateName(stateId).orElse(null);
	}

	private String resolveMunicipality(UUID municipalityId) {
		return municipalityId == null ? null : placeNameLookupPort.findMunicipalityName(municipalityId).orElse(null);
	}

	private String resolveOutreachChannel(UUID channelId) {
		if (channelId == null) {
			return null;
		}
		OutreachChannel channel = outreachChannelRepository.findById(channelId).orElse(null);
		return channel == null ? null : channel.getName();
	}

	private String resolveSchoolType(UUID schoolTypeId) {
		if (schoolTypeId == null) {
			return null;
		}
		HighSchoolType type = highSchoolTypeRepository.findById(schoolTypeId).orElse(null);
		return type == null ? null : type.getName();
	}

	/**
	 * Spanish display label for the program modality — the PDF renders
	 * "Presencial"/"Mixta" instead of the enum. {@code null} when no program.
	 */
	private static String modalityLabel(ProgramModality modality) {
		if (modality == null) {
			return null;
		}
		return switch (modality) {
			case PRESENCIAL -> "Presencial";
			case MIXTA -> "Mixta";
		};
	}

	private static FichaData.Domicilio emptyDomicilio() {
		return new FichaData.Domicilio(null, null, null, null, null, null, null, null);
	}

	private static FichaData.InformacionComplementaria emptyInformacionComplementaria() {
		return new FichaData.InformacionComplementaria(false, null, false, null, false, null, false, null, false, false,
				false, false, false);
	}

	private static FichaData.Ingresos emptyIngresos() {
		return new FichaData.Ingresos(null, false, null, null, null, null, null, null, null);
	}

	private static FichaData.AntecedentesEscolares emptyAntecedentesEscolares() {
		return new FichaData.AntecedentesEscolares(null, null, false, null, null, null, null, null, null);
	}
}