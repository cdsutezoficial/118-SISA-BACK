package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.model.HighSchoolType;
import mx.edu.utez.sisa.admission.domain.model.OutreachChannel;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateEditFormUseCase.CandidateEditForm;
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
import mx.edu.utez.sisa.admission.domain.port.out.HighSchoolTypeRepository;
import mx.edu.utez.sisa.admission.domain.port.out.OutreachChannelRepository;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.admission.shared.exception.CandidateAlreadyExistsException;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.HighSchoolTypeNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.InvalidCandidateFichaDataException;
import mx.edu.utez.sisa.admission.shared.exception.OutreachChannelNotFoundException;
import mx.edu.utez.sisa.shared.model.Address;
import mx.edu.utez.sisa.shared.model.Gender;
import mx.edu.utez.sisa.shared.model.HealthProfile;
import mx.edu.utez.sisa.shared.model.MaritalStatus;
import mx.edu.utez.sisa.shared.model.Person;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Edición de la ficha por Servicios Escolares (Fase 7 · T2): lectura del
 * formulario y guardado.
 */
@ExtendWith(MockitoExtension.class)
class UpdateCandidateUseCaseImplTest {

	private static final UUID CANDIDATE_ID = UUID.randomUUID();
	private static final UUID PERSON_ID = UUID.randomUUID();
	private static final UUID CONFIG_ID = UUID.randomUUID();
	private static final UUID CHANNEL_ID = UUID.randomUUID();
	private static final UUID NEW_CHANNEL_ID = UUID.randomUUID();
	private static final UUID SCHOOL_TYPE_ID = UUID.randomUUID();
	private static final UUID STATE_ID = UUID.randomUUID();
	private static final UUID MUNICIPALITY_ID = UUID.randomUUID();

	@Mock
	private CandidateRepository candidateRepository;

	@Mock
	private CandidatePersonRepository candidatePersonRepository;

	@Mock
	private OutreachChannelRepository outreachChannelRepository;

	@Mock
	private HighSchoolTypeRepository highSchoolTypeRepository;

	@Mock
	private ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort;

	private UpdateCandidateUseCaseImpl useCase;

	private GetCandidateEditFormUseCaseImpl getEditForm;

	private Candidate candidate;

	private Person person;

	@BeforeEach
	void setUp() {
		useCase = new UpdateCandidateUseCaseImpl(candidateRepository, candidatePersonRepository,
				outreachChannelRepository, highSchoolTypeRepository);
		getEditForm = new GetCandidateEditFormUseCaseImpl(candidateRepository, candidatePersonRepository,
				programAdmissionConfigQueryPort);

		person = CandidatePersonMapper.newPerson(command("PEGJ900101HMSRRN09", "Juan", CONFIG_ID, CHANNEL_ID));
		ReflectionTestUtils.setField(person, "id", PERSON_ID);
		person.getHealthProfile().setBloodType(null);

		candidate = new Candidate(PERSON_ID, CONFIG_ID, "ADM-2026-000001", true, true, CHANNEL_ID);
		ReflectionTestUtils.setField(candidate, "id", CANDIDATE_ID);

		lenient().when(candidateRepository.findById(CANDIDATE_ID)).thenReturn(Optional.of(candidate));
		lenient().when(candidatePersonRepository.findById(PERSON_ID)).thenReturn(Optional.of(person));
		lenient().when(outreachChannelRepository.findById(any()))
				.thenReturn(Optional.of(Mockito.mock(OutreachChannel.class)));
		lenient().when(highSchoolTypeRepository.findById(any()))
				.thenReturn(Optional.of(Mockito.mock(HighSchoolType.class)));
	}

	@Test
	void updatesEveryFichaFieldInPlaceAndKeepsFolioStatusAndPayments() {
		Address originalAddress = person.getAddress();
		HealthProfile originalHealth = person.getHealthProfile();
		when(candidatePersonRepository.findByCurp("PEGJ900101HMSRRN10")).thenReturn(Optional.empty());

		useCase.update(CANDIDATE_ID, command("PEGJ900101HMSRRN10", "Juan Carlos", CONFIG_ID, NEW_CHANNEL_ID));

		assertThat(person.getCurp()).isEqualTo("PEGJ900101HMSRRN10");
		assertThat(person.getFirstName()).isEqualTo("Juan Carlos");
		// Perfiles hijos modificados en su lugar, no reemplazados (@MapsId).
		assertThat(person.getAddress()).isSameAs(originalAddress);
		assertThat(person.getHealthProfile()).isSameAs(originalHealth);
		assertThat(person.getAddress().getStreet()).isEqualTo("Calle Juan Carlos");
		assertThat(candidate.getOutreachChannelId()).isEqualTo(NEW_CHANNEL_ID);
		assertThat(candidate.getFolio()).isEqualTo("ADM-2026-000001");
		assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.REGISTERED);
		assertThat(candidate.isLlaveMxVerified()).isTrue();
		verify(candidatePersonRepository).save(person);
		verify(candidateRepository).save(candidate);
	}

	@Test
	void keepingTheSameCurpIsNotAConflict() {
		when(candidatePersonRepository.findByCurp("PEGJ900101HMSRRN09")).thenReturn(Optional.of(person));

		useCase.update(CANDIDATE_ID, command("PEGJ900101HMSRRN09", "Juan", CONFIG_ID, CHANNEL_ID));

		verify(candidatePersonRepository).save(person);
	}

	@Test
	void rejectsACurpThatBelongsToAnotherPerson() {
		Person other = new Person("OTRA900101HMSRRN01", "Otra", "Persona", null, null);
		ReflectionTestUtils.setField(other, "id", UUID.randomUUID());
		when(candidatePersonRepository.findByCurp("OTRA900101HMSRRN01")).thenReturn(Optional.of(other));

		assertThatThrownBy(() -> useCase.update(CANDIDATE_ID,
				command("OTRA900101HMSRRN01", "Juan", CONFIG_ID, CHANNEL_ID)))
				.isInstanceOf(CandidateAlreadyExistsException.class);
		verify(candidatePersonRepository, never()).save(any());
	}

	@Test
	void rejectsAProgramChangeUntilRepricingExists() {
		assertThatThrownBy(() -> useCase.update(CANDIDATE_ID,
				command("PEGJ900101HMSRRN09", "Juan", UUID.randomUUID(), CHANNEL_ID)))
				.isInstanceOf(InvalidCandidateFichaDataException.class);
		verify(candidatePersonRepository, never()).save(any());
	}

	@Test
	void rejectsAnUnknownOutreachChannel() {
		when(candidatePersonRepository.findByCurp(any())).thenReturn(Optional.of(person));
		when(outreachChannelRepository.findById(NEW_CHANNEL_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.update(CANDIDATE_ID,
				command("PEGJ900101HMSRRN09", "Juan", CONFIG_ID, NEW_CHANNEL_ID)))
				.isInstanceOf(OutreachChannelNotFoundException.class);
	}

	@Test
	void rejectsAnUnknownHighSchoolType() {
		when(candidatePersonRepository.findByCurp(any())).thenReturn(Optional.of(person));
		when(highSchoolTypeRepository.findById(SCHOOL_TYPE_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.update(CANDIDATE_ID,
				command("PEGJ900101HMSRRN09", "Juan", CONFIG_ID, CHANNEL_ID)))
				.isInstanceOf(HighSchoolTypeNotFoundException.class);
	}

	@Test
	void unknownCandidateIsNotFound() {
		UUID unknown = UUID.randomUUID();
		when(candidateRepository.findById(unknown)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.update(unknown, command("X", "Y", CONFIG_ID, CHANNEL_ID)))
				.isInstanceOf(CandidateNotFoundException.class);
	}

	@Test
	void editFormReturnsWhatRegistrationStoredWithCatalogIds() {
		when(programAdmissionConfigQueryPort.findById(CONFIG_ID)).thenReturn(Optional.empty());

		CandidateEditForm form = getEditForm.get(CANDIDATE_ID);

		assertThat(form.folio()).isEqualTo("ADM-2026-000001");
		assertThat(form.programChangeAllowed()).isFalse();
		assertThat(form.ficha().datosGenerales().curp()).isEqualTo("PEGJ900101HMSRRN09");
		assertThat(form.ficha().datosGenerales().birthStateId()).isEqualTo(STATE_ID);
		assertThat(form.ficha().domicilio().municipalityId()).isEqualTo(MUNICIPALITY_ID);
		assertThat(form.ficha().ingresos().workStartTime()).isEqualTo(LocalTime.of(9, 0));
		assertThat(form.ficha().antecedentesEscolares().schoolTypeId()).isEqualTo(SCHOOL_TYPE_ID);
		assertThat(form.ficha().antecedentesEscolares().cctConfirmed()).isTrue();
		assertThat(form.ficha().seleccionCarrera().admissionConfigId()).isEqualTo(CONFIG_ID);
		assertThat(form.ficha().seleccionCarrera().outreachChannelId()).isEqualTo(CHANNEL_ID);
	}

	@Test
	void storesForeignBirthAndHighSchoolState() {
		when(candidatePersonRepository.findByCurp(any())).thenReturn(Optional.of(person));
		RegisterCandidateCommand base = command("PEGJ900101HMSRRN09", "Juan", CONFIG_ID, CHANNEL_ID);
		DatosGenerales d = base.datosGenerales();
		AntecedentesEscolares a = base.antecedentesEscolares();
		RegisterCandidateCommand foreign = new RegisterCandidateCommand(
				new DatosGenerales(d.curp(), d.firstName(), d.lastName1(), d.lastName2(), d.birthDate(), d.gender(),
						"Extranjera", null, null, "Colombia", "Antioquia", "Medellín", d.maritalStatus(),
						d.nativeLanguage(), d.hasChildren()),
				base.domicilio(), base.contacto(), base.informacionComplementaria(), base.ingresos(),
				new AntecedentesEscolares(a.schoolName(), a.schoolTypeId(), false, null, null, "Colombia", "Antioquia",
						"Medellín", a.gpa(), a.cct(), a.cctConfirmed()),
				base.seleccionCarrera(), false);

		useCase.update(CANDIDATE_ID, foreign);

		assertThat(person.getBirthCountry()).isEqualTo("Colombia");
		assertThat(person.getBirthForeignState()).isEqualTo("Antioquia");
		assertThat(person.getBirthForeignMunicipality()).isEqualTo("Medellín");
		assertThat(person.getHighSchoolBackground().getForeignCountry()).isEqualTo("Colombia");
		assertThat(person.getHighSchoolBackground().getSchoolForeignState()).isEqualTo("Antioquia");
		assertThat(person.getHighSchoolBackground().getSchoolCity()).isEqualTo("Medellín");

		CandidateEditForm form = getEditForm.get(CANDIDATE_ID);
		assertThat(form.ficha().datosGenerales().birthCountry()).isEqualTo("Colombia");
		assertThat(form.ficha().datosGenerales().birthForeignState()).isEqualTo("Antioquia");
		assertThat(form.ficha().antecedentesEscolares().schoolForeignState()).isEqualTo("Antioquia");
	}

	@Test
	void legacyForeignBirthKeptTheCountryInTheStateColumn() {
		// Antes de birth_country el país se guardaba en birth_foreign_state.
		person.setBirthCountry(null);
		person.setBirthForeignState("Colombia");

		CandidateEditForm form = getEditForm.get(CANDIDATE_ID);

		assertThat(form.ficha().datosGenerales().birthCountry()).isEqualTo("Colombia");
		assertThat(form.ficha().datosGenerales().birthForeignState()).isNull();
	}

	private static RegisterCandidateCommand command(String curp, String firstName, UUID configId, UUID channelId) {
		return new RegisterCandidateCommand(
				new DatosGenerales(curp, firstName, "Pérez", "García", LocalDate.of(1990, 1, 1), Gender.M,
						"Mexicana", STATE_ID, MUNICIPALITY_ID, null, null, null, MaritalStatus.SOLTERO, "Español",
						false),
				new Domicilio("Calle " + firstName, "10", null, "Centro", "Cuernavaca", "62000", STATE_ID,
						MUNICIPALITY_ID),
				new Contacto("juan@example.com", "7771234567", "7777654321"),
				new InformacionComplementaria(false, null, false, null, false, null, false, null, false, false,
						false, false, false),
				new Ingresos(new BigDecimal("12000.00"), true, "Medio tiempo", "7770000000",
						new BigDecimal("5000.00"), "Empresa", "Auxiliar", LocalTime.of(9, 0), LocalTime.of(14, 0)),
				new AntecedentesEscolares("Prepa 1", SCHOOL_TYPE_ID, true, STATE_ID, MUNICIPALITY_ID, null, null,
						null, new BigDecimal("9.1"), "17ECT0001A", true),
				new SeleccionCarrera(configId, channelId, true), false);
	}
}
