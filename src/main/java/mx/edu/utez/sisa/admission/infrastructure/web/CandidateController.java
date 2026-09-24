package mx.edu.utez.sisa.admission.infrastructure.web;

import jakarta.validation.Valid;
import mx.edu.utez.sisa.admission.domain.port.in.ConfirmAdmissionPaymentUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateFichaUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.InitiateFichaPaymentUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.AntecedentesEscolares;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.Contacto;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.DatosGenerales;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.Domicilio;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.InformacionComplementaria;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.Ingresos;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.RegisterCandidateCommand;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.SeleccionCarrera;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateFichaUseCase.FichaData;
import mx.edu.utez.sisa.admission.infrastructure.notification.CandidateFichaMailService;
import mx.edu.utez.sisa.admission.infrastructure.pdf.CandidateFichaPdfService;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.CandidateFichaResponse;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.CandidateRegistrationResponse;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.CheckoutInitiationResponse;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.PaymentConfirmationResponse;
import mx.edu.utez.sisa.admission.infrastructure.web.dto.RegisterCandidateRequest;
import mx.edu.utez.sisa.admission.shared.exception.CandidateNotFoundException;
import mx.edu.utez.sisa.admission.shared.exception.InvalidCandidateFichaDataException;
import mx.edu.utez.sisa.shared.model.EmploymentType;
import mx.edu.utez.sisa.shared.model.Gender;
import mx.edu.utez.sisa.shared.model.MaritalStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;

/**
 * Public portal endpoints for the admission ticket. Deliberately the ONLY
 * controller in the repo registered for anonymous access:
 * {@code identity.SecurityFilterConfig} grants {@code POST /candidates},
 * the ficha read/PDF and the payment endpoints {@code permitAll} (the
 * applicant has no session); every admin-facing endpoint stays on the
 * {@code ADMIN}/{@code SERVICIOS_ESCOLARES} matchers.
 *
 * <p>This controller is the WEB layer's translation boundary: it maps the
 * frontend ficha payload ({@code RegisterCandidateRequest}, string-typed:
 * {@code fechaNacimiento} {@code dd/MM/yyyy}, {@code sexo}
 * {"Femenino"/"Masculino"/"Hombre"/"Mujer"}, {@code estadoCivil}
 * {"Soltero/a", …}, {@code tipoTrabajo} {"Tiempo completo"/"Medio tiempo"},
 * {@code horaInicio}/{@code horaFin} {@code HH:mm}, {@code cctConfirmacion})
 * into the enum-typed domain command ({@code Gender}, {@code MaritalStatus},
 * {@code EmploymentType}, {@code LocalDate}/{@code LocalTime},
 * {@code cctConfirmed}), keeping the domain port purist. String→enum and
 * text→date/time mappings live HERE, not in {@code RegisterCandidateUseCaseImpl}.
 *
 * <p>The {@code promo} field is deliberately not included: {@code modalidad}
 * is defined by the chosen program, not stored on {@code Candidate}
 * ({@code 03-admision.md}).
 */
@RestController
@RequestMapping("/candidates")
public class CandidateController {

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

	private final RegisterCandidateUseCase registerCandidateUseCase;

	private final ConfirmAdmissionPaymentUseCase confirmAdmissionPaymentUseCase;

	private final InitiateFichaPaymentUseCase initiateFichaPaymentUseCase;

	private final GetCandidateFichaUseCase getCandidateFichaUseCase;

	private final CandidateFichaMailService fichaMailService;

	private final CandidateFichaPdfService fichaPdfService;

	public CandidateController(RegisterCandidateUseCase registerCandidateUseCase,
			ConfirmAdmissionPaymentUseCase confirmAdmissionPaymentUseCase,
			InitiateFichaPaymentUseCase initiateFichaPaymentUseCase,
			GetCandidateFichaUseCase getCandidateFichaUseCase, CandidateFichaMailService fichaMailService,
			CandidateFichaPdfService fichaPdfService) {
		this.registerCandidateUseCase = registerCandidateUseCase;
		this.confirmAdmissionPaymentUseCase = confirmAdmissionPaymentUseCase;
		this.initiateFichaPaymentUseCase = initiateFichaPaymentUseCase;
		this.getCandidateFichaUseCase = getCandidateFichaUseCase;
		this.fichaMailService = fichaMailService;
		this.fichaPdfService = fichaPdfService;
	}

	@PostMapping
	public ResponseEntity<CandidateRegistrationResponse> register(
			@Valid @RequestBody RegisterCandidateRequest request) {
		RegisterCandidateCommand command = toCommand(request);
		RegisterCandidateUseCase.CandidateRegistrationResult result = registerCandidateUseCase.register(command);
		return ResponseEntity.status(HttpStatus.CREATED).body(CandidateRegistrationResponse.from(result));
	}

	/**
	 * Public "Pagar en línea" trigger (webhook EVO stand-in): confirms the
	 * ticket payment ({@code PENDING → PAID}) and transitions the candidate to
	 * {@code PAID}, then sends the confirmation email. The email is
	 * best-effort (async, swallowed on failure) and never blocks the response.
	 */
	@PostMapping("/{id}/payments/confirm")
	public ResponseEntity<PaymentConfirmationResponse> confirmPayment(@PathVariable UUID id) {
		ConfirmAdmissionPaymentUseCase.ConfirmPaymentResult result = confirmAdmissionPaymentUseCase.confirm(id);
		FichaData ficha = getCandidateFichaUseCase.get(id);
		if (ficha != null && ficha.email() != null && !ficha.email().isBlank()) {
			fichaMailService.sendPaymentConfirmation(ficha.email(), fullName(ficha), ficha.folio(),
					ficha.programName(), result.amount(), result.referenceNumber(), ficha.deadline(),
					result.receiptNumber());
		}
		return ResponseEntity.ok(PaymentConfirmationResponse.from(result));
	}

	/**
	 * Public "Pagar en línea" trigger (Fase 4): initiates the EVO Hosted
	 * Checkout session for the ficha — {@code order.id} (prefix + folio),
	 * amount/currency from the payment concept, return/cancelUrl from config —
	 * and persists {@code order.id} + {@code session.id} on the payment so the
	 * return step (Fase 5) can verify the result against the gateway. {@code 404}
	 * if candidate/payment missing, {@code 409} if already paid, {@code 502} if
	 * the gateway cannot start the session.
	 */
	@PostMapping("/{id}/payments/checkout")
	public ResponseEntity<CheckoutInitiationResponse> initiateCheckout(@PathVariable UUID id) {
		return ResponseEntity.ok(CheckoutInitiationResponse.from(initiateFichaPaymentUseCase.initiateCheckout(id)));
	}

	/**
	 * Resends (or sends for the first time) the payment-instructions email from
	 * the ficha screen's "Enviar instrucciones a mi correo" button. Best-effort
	 * like the confirmation email. {@code 204} regardless of mail outcome — the
	 * button's success is "the request was accepted for delivery".
	 */
	@PostMapping("/{id}/send-instructions")
	public ResponseEntity<Void> sendInstructions(@PathVariable UUID id) {
		FichaData ficha = getCandidateFichaUseCase.get(id);
		if (ficha == null) {
			throw new CandidateNotFoundException("No existe el candidato: " + id);
		}
		if (ficha.email() != null && !ficha.email().isBlank()) {
			fichaMailService.sendPaymentInstructions(ficha.email(), fullName(ficha), ficha.folio(),
					ficha.programName(), ficha.amount(), ficha.referenceNumber(), ficha.deadline());
		}
		return ResponseEntity.noContent().build();
	}

	/**
	 * Full ficha projection for the screen refresh / direct-open case (the
	 * ficha route is only reachable via {@code navigate} state today; a reload
	 * loses it, so the frontend re-fetches by {@code id}).
	 */
	@GetMapping("/{id}")
	public ResponseEntity<CandidateFichaResponse> getFicha(@PathVariable UUID id) {
		FichaData ficha = getCandidateFichaUseCase.get(id);
		if (ficha == null) {
			throw new CandidateNotFoundException("No existe el candidato: " + id);
		}
		return ResponseEntity.ok(CandidateFichaResponse.from(ficha));
	}

	@GetMapping("/{id}/ficha.pdf")
	public ResponseEntity<byte[]> downloadFichaPdf(@PathVariable UUID id) {
		FichaData ficha = getCandidateFichaUseCase.get(id);
		if (ficha == null) {
			throw new CandidateNotFoundException("No existe el candidato: " + id);
		}
		byte[] pdf = fichaPdfService.render(ficha);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=\"ficha-" + ficha.folio() + ".pdf\"")
				.contentType(MediaType.APPLICATION_PDF).body(pdf);
	}

	private static RegisterCandidateCommand toCommand(RegisterCandidateRequest request) {
		return new RegisterCandidateCommand(
				toDatosGenerales(request.datosGenerales()),
				toDomicilio(request.domicilio()),
				toContacto(request.contacto()),
				toInformacionComplementaria(request.informacionComplementaria()),
				toIngresos(request.ingresos()),
				toAntecedentesEscolares(request.antecedentesEscolares()),
				toSeleccionCarrera(request.seleccionCarrera()),
				request.llaveMxVerified());
	}

	private static DatosGenerales toDatosGenerales(RegisterCandidateRequest.DatosGenerales d) {
		return new DatosGenerales(d.curp(), d.nombres(), d.apellidoPaterno(), d.apellidoMaterno(),
				parseDate(d.fechaNacimiento()), toGender(d.sexo()), d.nacionalidad(), d.birthStateId(),
				d.birthMunicipalityId(), d.paisNacimiento(), d.ciudadNacimiento(), toMaritalStatus(d.estadoCivil()),
				d.lenguaNatal(), d.tieneHijos());
	}

	private static Domicilio toDomicilio(RegisterCandidateRequest.Domicilio dom) {
		return new Domicilio(dom.calle(), dom.numeroExterior(), dom.numeroInterior(), dom.colonia(), dom.localidad(),
				dom.codigoPostal(), dom.stateId(), dom.municipalityId());
	}

	private static Contacto toContacto(RegisterCandidateRequest.Contacto c) {
		return new Contacto(c.personalEmail(), c.telefonoCasa(), c.celular());
	}

	private static InformacionComplementaria toInformacionComplementaria(
			RegisterCandidateRequest.InformacionComplementaria info) {
		return new InformacionComplementaria(info.tieneEnfermedadPreexistente(), info.descripcionEnfermedad(),
				info.tieneDiscapacidad(), info.descripcionDiscapacidad(), info.padresHablanLenguaIndigena(),
				info.lenguaIndigenaPadres(), info.hablaLenguaIndigena(), info.lenguaIndigenaPropia(),
				info.seIdentificaIndigena(), info.seIdentificaNoBinario(), info.perteneceComunidadLgbttiq(),
				info.esAfrodescendiente(), info.esAfrodescendiente() && Boolean.TRUE.equals(info.seIdentificaAfrodescendiente()));
	}

	private static Ingresos toIngresos(RegisterCandidateRequest.Ingresos i) {
		return new Ingresos(i.ingresoMensualFamiliar(), i.trabaja(), toEmploymentType(i.tipoTrabajo()),
				i.telefonoTrabajo(), i.ingresoMensual(), i.nombreEmpresa(), i.puesto(), parseTime(i.horaInicio()),
				parseTime(i.horaFin()));
	}

	private static AntecedentesEscolares toAntecedentesEscolares(RegisterCandidateRequest.AntecedentesEscolares a) {
		boolean cctConfirmed = a.cct() != null && !a.cct().isBlank() && a.cct().equals(a.cctConfirmacion());
		return new AntecedentesEscolares(a.nombrePreparatoria(), a.schoolTypeId(), a.estudioBachilleratoEnMexico(),
				a.schoolStateId(), a.schoolMunicipalityId(), a.paisPreparatoria(), a.ciudadPreparatoria(), a.promedio(),
				a.cct(), cctConfirmed);
	}

	private static SeleccionCarrera toSeleccionCarrera(RegisterCandidateRequest.SeleccionCarrera s) {
		return new SeleccionCarrera(s.admissionConfigId(), s.outreachChannelId(), s.isFirstChoice());
	}

	private static LocalDate parseDate(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(value.trim(), DATE_FORMATTER);
		} catch (DateTimeParseException ex) {
			throw new InvalidCandidateFichaDataException("fechaNacimiento debe tener formato dd/MM/yyyy: " + value);
		}
	}

	private static LocalTime parseTime(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return LocalTime.parse(value.trim(), TIME_FORMATTER);
		} catch (DateTimeParseException ex) {
			throw new InvalidCandidateFichaDataException("hora (horario laboral) debe tener formato HH:mm: " + value);
		}
	}

	/**
	 * Maps the front's {@code sexo} labels — "Femenino"/"Mujer" → {@code F},
	 * "Masculino"/"Hombre" → {@code M}, anything else → {@code NB}. Case and
	 * surrounding whitespace-insensitive.
	 */
	private static Gender toGender(String value) {
		if (value == null) {
			return null;
		}
		String normalized = normalize(value);
		return switch (normalized) {
			case "femenino", "mujer" -> Gender.F;
			case "masculino", "hombre" -> Gender.M;
			default -> Gender.NB;
		};
	}

	/**
	 * Maps the front's {@code estadoCivil} labels — "Soltero/a" → {@code SOLTERO},
	 * "Casado/a" → {@code CASADO}, "Unión libre" → {@code UNION_LIBRE},
	 * "Divorciado/a" → {@code DIVORCIADO}, "Viudo/a" → {@code VIUDO}, anything
	 * else → {@code OTRO}.
	 */
	private static MaritalStatus toMaritalStatus(String value) {
		if (value == null) {
			return null;
		}
		String normalized = normalize(value).replace("/a", "");
		return switch (normalized) {
			case "soltero" -> MaritalStatus.SOLTERO;
			case "casado" -> MaritalStatus.CASADO;
			case "unión libre", "union libre" -> MaritalStatus.UNION_LIBRE;
			case "divorciado" -> MaritalStatus.DIVORCIADO;
			case "viudo" -> MaritalStatus.VIUDO;
			default -> MaritalStatus.OTRO;
		};
	}

	/**
	 * Maps the front's {@code tipoTrabajo} labels — "Tiempo completo" →
	 * {@code PERMANENT}, "Medio tiempo" → {@code TEMPORARY}, anything else →
	 * {@code null} (EmploymentType is nullable).
	 */
	private static EmploymentType toEmploymentType(String value) {
		if (value == null) {
			return null;
		}
		String normalized = normalize(value);
		return switch (normalized) {
			case "tiempo completo", "trabajo de tiempo completo" -> EmploymentType.PERMANENT;
			case "medio tiempo", "trabajo de medio tiempo" -> EmploymentType.TEMPORARY;
			default -> null;
		};
	}

	private static String normalize(String value) {
		return value.trim().toLowerCase(Locale.ROOT);
	}

	private static String fullName(FichaData ficha) {
		return (ficha.firstName() == null ? "" : ficha.firstName()) + " "
				+ (ficha.lastName1() == null ? "" : ficha.lastName1()) + " "
				+ (ficha.lastName2() == null ? "" : ficha.lastName2());
	}
}