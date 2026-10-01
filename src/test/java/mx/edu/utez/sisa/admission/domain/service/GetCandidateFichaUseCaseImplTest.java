package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPayment;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentConcept;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.HighSchoolType;
import mx.edu.utez.sisa.admission.domain.model.OutreachChannel;
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
import mx.edu.utez.sisa.shared.model.Gender;
import mx.edu.utez.sisa.shared.model.HealthProfile;
import mx.edu.utez.sisa.shared.model.HighSchoolBackground;
import mx.edu.utez.sisa.shared.model.MaritalStatus;
import mx.edu.utez.sisa.shared.model.Person;
import mx.edu.utez.sisa.shared.model.ProgramModality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCandidateFichaUseCaseImplTest {

	// Carried by the projection; this use case only needs the program name, so
	// the window/quota just have to be plausible. The rules they gate are covered
	// in RegisterCandidateUseCaseImplTest.
	private static final Instant WINDOW_OPEN = Instant.parse("2026-01-01T00:00:00Z");
	private static final Instant WINDOW_CLOSE = Instant.parse("2026-12-31T23:59:59Z");
	private static final int MAX_CANDIDATES = 40;

	/** The registration window's closing day, snapshotted onto the ticket. */
	private static final LocalDate REGISTRATION_DEADLINE = LocalDate.of(2026, 12, 31);

	/**
	 * The tuition concept's {@code available_until}. A different day from
	 * {@link #REGISTRATION_DEADLINE} on purpose: the PDF prints the two under
	 * separate labels, and equal values would hide a swap.
	 */
	private static final LocalDate PAYMENT_CLOSES_ON = LocalDate.of(2026, 12, 20);

	/**
	 * The ficha's own plazo: registered 01/12 + 10 days → 11/12, before the
	 * sales window. Deliberately a third, distinct day so a mix-up between the
	 * visible payment deadline and either window cannot pass unnoticed.
	 */
	private static final ZoneId ZONE = ZoneId.of("America/Mexico_City");

	private static final Instant REGISTERED_AT = LocalDate.of(2026, 12, 1).atTime(12, 0).atZone(ZONE).toInstant();

	private static final int DEADLINE_DAYS = 10;

	/** The earlier of {@link #REGISTRATION_DEADLINE} and {@link #REGISTERED_AT} + 10. */
	private static final LocalDate PAYMENT_DEADLINE = LocalDate.of(2026, 12, 11);

	private static final BigDecimal LIVE_AMOUNT = new BigDecimal("550.00");

	private static final Clock CLOCK = Clock.fixed(REGISTERED_AT, ZONE);

	@Mock
	private CandidateRepository candidateRepository;

	@Mock
	private CandidatePersonRepository candidatePersonRepository;

	@Mock
	private AdmissionPaymentRepository paymentRepository;

	@Mock
	private ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort;

	@Mock
	private PlaceNameLookupPort placeNameLookupPort;

	@Mock
	private OutreachChannelRepository outreachChannelRepository;

	@Mock
	private HighSchoolTypeRepository highSchoolTypeRepository;

	@Mock
	private FichaAmountResolver fichaAmountResolver;

	private GetCandidateFichaUseCaseImpl useCase;

	private UUID candidateId;
	private UUID personId;
	private UUID configId;
	private UUID programId;

	@BeforeEach
	void setUp() {
		useCase = new GetCandidateFichaUseCaseImpl(candidateRepository, candidatePersonRepository, paymentRepository,
				programAdmissionConfigQueryPort, placeNameLookupPort, outreachChannelRepository,
				highSchoolTypeRepository, fichaAmountResolver, CLOCK, DEADLINE_DAYS);
		candidateId = UUID.randomUUID();
		personId = UUID.randomUUID();
		configId = UUID.randomUUID();
		programId = UUID.randomUUID();

		var candidate = new mx.edu.utez.sisa.admission.domain.model.Candidate(personId, configId, "ADM-2026-000001",
				true, true, null);
		ReflectionTestUtils.setField(candidate, "id", candidateId);
		ReflectionTestUtils.setField(candidate, "registeredAt", REGISTERED_AT);
		lenient().when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));

		Person person = new Person("CURP0000000000000000", "Juan", "Perez", "Lopez", null);
		person.setPersonalEmail("juan@correo.com");
		ReflectionTestUtils.setField(person, "id", personId);
		lenient().when(candidatePersonRepository.findById(personId)).thenReturn(Optional.of(person));

		lenient().when(paymentRepository.findByCandidateId(candidateId)).thenReturn(Optional
				.of(new AdmissionPayment(candidateId, AdmissionPaymentConcept.ADMISSION_FICHA, new BigDecimal("500.00"),
						"REF-20260922-000001", REGISTRATION_DEADLINE)));

		lenient().when(programAdmissionConfigQueryPort.findById(configId)).thenReturn(Optional
				.of(new AdmissionConfigInfo(configId, ProgramAdmissionConfigStatus.OPEN, programId, "Mecatrónica",
						ProgramModality.PRESENCIAL, "2026-2", WINDOW_OPEN, WINDOW_CLOSE, MAX_CANDIDATES)));

		// The payment window is read live, so the PDF's "Fecha límite de pago" can
		// differ from the registration window and a test can tell them apart.
		lenient().when(fichaAmountResolver.paymentClosesOn(programId)).thenReturn(PAYMENT_CLOSES_ON);

		// The amount is resolved live from the catalog too: a price edit after
		// the ficha was issued has to show up on the reprint.
		lenient().when(fichaAmountResolver.resolve(programId, LocalDate.now(CLOCK)))
				.thenReturn(new FichaAmountResolver.FichaAmount(LIVE_AMOUNT, "Admisión"));
	}

	/**
	 * The PDF prints the registration window, the live price and the date the
	 * applicant pays by, each under its own name, so the use case has to carry
	 * all three. They are pinned to different days on purpose: a mix-up between
	 * them would be invisible if they were equal.
	 */
	@Test
	void get_carriesTheRegistrationDeadlineThePaymentWindowAndTheVisiblePaymentDeadline() {
		var ficha = useCase.get(candidateId);

		assertThat(ficha).isNotNull();
		assertThat(ficha.registrationDeadline()).isEqualTo(REGISTRATION_DEADLINE);
		assertThat(ficha.paymentClosesOn()).isEqualTo(PAYMENT_CLOSES_ON);
		// The one shown under "Fecha límite de pago": the ficha's own plazo, not
		// the concept's available_until.
		assertThat(ficha.paymentDeadline()).isEqualTo(PAYMENT_DEADLINE);
	}

	/** The amount is the catalog's live price, not the number frozen at registration. */
	@Test
	void get_pricesTheFichaLiveFromTheCatalog() {
		assertThat(useCase.get(candidateId).amount()).isEqualByComparingTo(LIVE_AMOUNT);
	}

	@Test
	void get_assemblesFichaWithPersonPaymentAndProgramName() {
		var ficha = useCase.get(candidateId);

		assertThat(ficha).isNotNull();
		assertThat(ficha.folio()).isEqualTo("ADM-2026-000001");
		assertThat(ficha.programName()).isEqualTo("Mecatrónica");
		assertThat(ficha.email()).isEqualTo("juan@correo.com");
		assertThat(ficha.referenceNumber()).isEqualTo("REF-20260922-000001");
		assertThat(ficha.paymentStatus()).isEqualTo(AdmissionPaymentStatus.PENDING);
	}

	@Test
	void get_unknownCandidateReturnsNull() {
		UUID missing = UUID.randomUUID();
		when(candidateRepository.findById(missing)).thenReturn(Optional.empty());

		assertThat(useCase.get(missing)).isNull();
	}

	@Test
	void get_assemblesFullPaso4SectionsWithResolvedNames() {
		UUID stateId = UUID.randomUUID();
		UUID municipalityId = UUID.randomUUID();
		UUID channelId = UUID.randomUUID();
		UUID schoolTypeId = UUID.randomUUID();

		// Enrich the shared Person fixture with the Fase 7 profile fields.
		var candidate = new mx.edu.utez.sisa.admission.domain.model.Candidate(personId, configId, "ADM-2026-000001",
				true, true, channelId);
		ReflectionTestUtils.setField(candidate, "id", candidateId);
		ReflectionTestUtils.setField(candidate, "registeredAt", REGISTERED_AT);
		when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));

		Person person = new Person("CURP0000000000000000", "Juan", "Perez", "Lopez", null);
		person.setPersonalEmail("juan@correo.com");
		person.setBirthDate(LocalDate.of(2006, 3, 14));
		person.setGender(Gender.M);
		person.setNationality("Mexicana");
		person.setBirthStateId(stateId);
		person.setBirthMunicipalityId(municipalityId);
		person.setMaritalStatus(MaritalStatus.SOLTERO);
		person.setNativeLanguage("Español");
		person.setHasChildren(false);
		person.setMonthlyFamilyIncome(new BigDecimal("12000.00"));
		person.setAddress(new Address("Av. Lázaro Cárdenas", "100", "", "Centro", "Jiquilpan", "59510", stateId,
				municipalityId, null, null));
		person.setHealthProfile(new HealthProfile(true, "Asma", false, null, null));
		person.setDiversityProfile(new DiversityProfile(true, false, true, false, false, false, false, "Náhuatl", null));
		person.setEmploymentInfo(new EmploymentInfo(true, null, "Ferretería López", "Cajero",
				"3515123456", new BigDecimal("6500.00"), LocalTime.of(9, 0), LocalTime.of(18, 0)));
		person.setHighSchoolBackground(new HighSchoolBackground("CBTis 121", "Jiquilpan", schoolTypeId,
				new BigDecimal("8.9"), true, stateId, municipalityId, "16DCT0121B", true, null, null, null, null));
		when(candidatePersonRepository.findById(personId)).thenReturn(Optional.of(person));

		// Payment carries the EVO order id when an online checkout ran.
		AdmissionPayment payment = new AdmissionPayment(candidateId, AdmissionPaymentConcept.ADMISSION_FICHA,
				new BigDecimal("500.00"), "REF-20260922-000001", REGISTRATION_DEADLINE);
		ReflectionTestUtils.setField(payment, "orderId", "TESTUTEZ-ADM-2026-000001");
		when(paymentRepository.findByCandidateId(candidateId)).thenReturn(Optional.of(payment));

		when(placeNameLookupPort.findStateName(stateId)).thenReturn(Optional.of("Michoacán"));
		when(placeNameLookupPort.findMunicipalityName(municipalityId)).thenReturn(Optional.of("Jiquilpan"));
		when(outreachChannelRepository.findById(channelId))
				.thenReturn(Optional.of(new OutreachChannel("Portal de admisión")));
		when(highSchoolTypeRepository.findById(schoolTypeId))
				.thenReturn(Optional.of(new HighSchoolType("Bachillerato Tecnológico")));

		var ficha = useCase.get(candidateId);

		assertThat(ficha).isNotNull();
		// Datos generales + nombres de catálogos resueltos.
		assertThat(ficha.datosGenerales().birthDate()).isEqualTo(LocalDate.of(2006, 3, 14));
		assertThat(ficha.datosGenerales().gender()).isEqualTo(Gender.M);
		assertThat(ficha.datosGenerales().birthStateName()).isEqualTo("Michoacán");
		assertThat(ficha.datosGenerales().birthMunicipalityName()).isEqualTo("Jiquilpan");
		assertThat(ficha.datosGenerales().maritalStatus()).isEqualTo(MaritalStatus.SOLTERO);
		// Domicilio con nombres.
		assertThat(ficha.domicilio().street()).isEqualTo("Av. Lázaro Cárdenas");
		assertThat(ficha.domicilio().stateName()).isEqualTo("Michoacán");
		// Info complementaria.
		assertThat(ficha.informacionComplementaria().hasPreexistingCondition()).isTrue();
		assertThat(ficha.informacionComplementaria().conditionDescription()).isEqualTo("Asma");
		assertThat(ficha.informacionComplementaria().parentsSpeakIndigenousLanguage()).isTrue();
		assertThat(ficha.informacionComplementaria().parentsIndigenousLanguage()).isEqualTo("Náhuatl");
		// Ingresos.
		assertThat(ficha.ingresos().isEmployed()).isTrue();
		assertThat(ficha.ingresos().companyName()).isEqualTo("Ferretería López");
		assertThat(ficha.ingresos().workStartTime()).isEqualTo(LocalTime.of(9, 0));
		// Selección de carrera con modalidad/periodo/canal resueltos.
		assertThat(ficha.seleccionCarrera().modalityName()).isEqualTo("Presencial");
		assertThat(ficha.seleccionCarrera().periodName()).isEqualTo("2026-2");
		assertThat(ficha.seleccionCarrera().outreachChannelName()).isEqualTo("Portal de admisión");
		assertThat(ficha.seleccionCarrera().isFirstChoice()).isTrue();
		// Antecedentes escolares.
		assertThat(ficha.antecedentesEscolares().schoolName()).isEqualTo("CBTis 121");
		assertThat(ficha.antecedentesEscolares().schoolTypeName()).isEqualTo("Bachillerato Tecnológico");
		assertThat(ficha.antecedentesEscolares().schoolStateName()).isEqualTo("Michoacán");
		assertThat(ficha.antecedentesEscolares().cct()).isEqualTo("16DCT0121B");
		// Pago expone el orderId de EVO.
		assertThat(ficha.orderId()).isEqualTo("TESTUTEZ-ADM-2026-000001");
	}

	@Test
	void get_withoutProfilesResolvesNullSectionsWithoutFailing() {
		var ficha = useCase.get(candidateId);

		assertThat(ficha).isNotNull();
		assertThat(ficha.domicilio().street()).isNull();
		assertThat(ficha.informacionComplementaria().hasPreexistingCondition()).isFalse();
		assertThat(ficha.antecedentesEscolares().schoolName()).isNull();
		assertThat(ficha.ingresos().isEmployed()).isFalse();
		assertThat(ficha.orderId()).isNull();
	}
}