package mx.edu.utez.sisa.academic_config.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentArea;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentAreaStatus;
import mx.edu.utez.sisa.academic_config.infrastructure.persistence.PaymentAreaJpaRepository;
import mx.edu.utez.sisa.identity.infrastructure.security.JwtService;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration coverage for the {@code /payment-areas} security
 * matchers: real H2, real JWT filter chain, no mocks. Same
 * {@code ADMIN}/{@code PERSONAL_FINANZAS} pair as {@code /payment-concepts} —
 * {@code SERVICIOS_ESCOLARES} is deliberately NOT granted.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentAreaControllerIT {

	/** Alimenta {@link #suffix()}; ver su javadoc para por qué es contador. */
	private static final java.util.concurrent.atomic.AtomicInteger SEQUENCE = new java.util.concurrent.atomic.AtomicInteger();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private PaymentAreaJpaRepository jpaRepository;

	@Test
	void adminCanList() throws Exception {
		jpaRepository.save(newArea("Lista Admin", "LA" + suffix()));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(get("/payment-areas").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray());
	}

	@Test
	void personalFinanzasCanList() throws Exception {
		jpaRepository.save(newArea("Lista Finanzas", "LF" + suffix()));
		String token = tokenFor(RoleType.PERSONAL_FINANZAS);

		mockMvc.perform(get("/payment-areas").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
	}

	@Test
	void serviciosEscolaresIsForbiddenOnList() throws Exception {
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(get("/payment-areas").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedListReturns401() throws Exception {
		mockMvc.perform(get("/payment-areas")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void adminCanCreate() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/payment-areas").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Area Admin", "AA" + suffix()))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void personalFinanzasCanCreate() throws Exception {
		String token = tokenFor(RoleType.PERSONAL_FINANZAS);

		mockMvc.perform(post("/payment-areas").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Area Finanzas", "AF" + suffix()))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void serviciosEscolaresIsForbiddenOnCreate() throws Exception {
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(post("/payment-areas").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Forbidden SE", "SE" + suffix()))))
				.andExpect(status().isForbidden());
	}

	@Test
	void otherRoleIsForbiddenOnCreate() throws Exception {
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(post("/payment-areas").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Forbidden Docente", "FD" + suffix()))))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedCreateReturns401() throws Exception {
		mockMvc.perform(post("/payment-areas").contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Unauth", "UN" + suffix()))))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void createWithDuplicateNameReturns409() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		String code = "DN" + suffix();
		mockMvc.perform(post("/payment-areas").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Area Duplicada Nombre", code))))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/payment-areas").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Area Duplicada Nombre", "D2" + suffix()))))
				.andExpect(status().isConflict())
				// Fase 11: el código estable es el contrato que el frontend ramifica
				// para pegar el error al campo `name`. Sin esta aserción, volver a
				// fusionar los dos 409 en un handler único pasaría el test.
				.andExpect(jsonPath("$.code").value("PAYMENT_AREA_NAME_DUPLICATE"));
	}

	@Test
	void createWithDuplicateCodeReturns409() throws Exception {
		String token = tokenFor(RoleType.ADMIN);
		String code = "DC" + suffix();
		mockMvc.perform(post("/payment-areas").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Area Codigo Uno", code))))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/payment-areas").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Area Codigo Dos", code))))
				.andExpect(status().isConflict())
				// Espejo del caso de nombre: aquí el código va al campo `code`.
				.andExpect(jsonPath("$.code").value("PAYMENT_AREA_CODE_DUPLICATE"));
	}

	@Test
	void createWithBlankNameReturns400() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/payment-areas").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("", "BN" + suffix()))))
				.andExpect(status().isBadRequest());
	}

	// ─── Fase 11: formato de `code` (2 a 5 alfanuméricos en mayúscula) ────────

	/**
	 * Los límites del regla de negocio del 2026-10-05. Se prueban los tres fallos
	 * de forma por separado del {@code @Size(min = 2, max = 5)} porque comparten
	 * mensaje: lo que cambia aquí es que el 400 llegue, no qué texto diga.
	 */
	@Test
	void createWithCodeOutsideTheAllowedFormatReturns400() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		// Una sola letra: por debajo del mínimo de 2.
		mockMvc.perform(post("/payment-areas").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Formato Corto", "A"))))
				.andExpect(status().isBadRequest());

		// Seis caracteres: por encima del máximo de 5.
		mockMvc.perform(post("/payment-areas").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Formato Largo", "ABCDEF"))))
				.andExpect(status().isBadRequest());

		// Minúscula y símbolo: el patrón es `^[A-Z0-9]{2,5}$`, sin `accents` ni
		// `guiones`. El navegador nunca manda minúscula porque `normalizeCode` la
		// sube antes; un cliente de API sí puede, y debe recibir el 400.
		mockMvc.perform(post("/payment-areas").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Formato Simbolo", "CO-L"))))
				.andExpect(status().isBadRequest());

		mockMvc.perform(post("/payment-areas").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Formato Minúscula", "col"))))
				.andExpect(status().isBadRequest());
	}

	/** Los dos extremos válidos del rango, para que el test no sólo acote por fuera. */
	@Test
	void createWithCodeAtTheFormatBoundariesIsAccepted() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/payment-areas").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Clave Minima", "AB"))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("AB"));

		mockMvc.perform(post("/payment-areas").header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Clave Maxima", "ABC12"))))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("ABC12"));
	}

	@Test
	void adminCanGetById() throws Exception {
		PaymentArea saved = jpaRepository.save(newArea("Get Admin", "GA" + suffix()));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(get("/payment-areas/{id}", saved.getId()).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(saved.getId().toString()));
	}

	@Test
	void serviciosEscolaresIsForbiddenOnGetById() throws Exception {
		PaymentArea saved = jpaRepository.save(newArea("Get SE", "GS" + suffix()));
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(get("/payment-areas/{id}", saved.getId()).header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedGetByIdReturns401() throws Exception {
		PaymentArea saved = jpaRepository.save(newArea("Get Unauth", "GU" + suffix()));

		mockMvc.perform(get("/payment-areas/{id}", saved.getId())).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void getByIdWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(get("/payment-areas/{id}", UUID.randomUUID()).header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound());
	}

	@Test
	void adminCanUpdate() throws Exception {
		PaymentArea saved = jpaRepository.save(newArea("Update Admin", "UA" + suffix()));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(put("/payment-areas/{id}", saved.getId()).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Area Renombrada", saved.getCode()))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Area Renombrada"));
	}

	@Test
	void serviciosEscolaresIsForbiddenOnUpdate() throws Exception {
		PaymentArea saved = jpaRepository.save(newArea("Update SE", "US" + suffix()));
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(put("/payment-areas/{id}", saved.getId()).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Forbidden", saved.getCode()))))
				.andExpect(status().isForbidden());
	}

	@Test
	void updateWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(put("/payment-areas/{id}", UUID.randomUUID()).header("Authorization", "Bearer " + token)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody("Fantasma", "FA" + suffix()))))
				.andExpect(status().isNotFound());
	}

	@Test
	void adminCanChangeStatus() throws Exception {
		PaymentArea saved = jpaRepository.save(newArea("Status Admin", "SA" + suffix()));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(patch("/payment-areas/{id}/status", saved.getId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(PaymentAreaStatus.INACTIVE))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
	}

	@Test
	void personalFinanzasCanChangeStatus() throws Exception {
		PaymentArea saved = jpaRepository.save(newArea("Status Finanzas", "SF" + suffix()));
		String token = tokenFor(RoleType.PERSONAL_FINANZAS);

		mockMvc.perform(patch("/payment-areas/{id}/status", saved.getId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(PaymentAreaStatus.INACTIVE))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
	}

	@Test
	void serviciosEscolaresIsForbiddenOnChangeStatus() throws Exception {
		PaymentArea saved = jpaRepository.save(newArea("Status SE", "SS" + suffix()));
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(patch("/payment-areas/{id}/status", saved.getId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(PaymentAreaStatus.INACTIVE))))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedChangeStatusReturns401() throws Exception {
		PaymentArea saved = jpaRepository.save(newArea("Status Unauth", "SU" + suffix()));

		mockMvc.perform(patch("/payment-areas/{id}/status", saved.getId()).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(PaymentAreaStatus.INACTIVE))))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void changeStatusWithUnknownIdReturns404() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(patch("/payment-areas/{id}/status", UUID.randomUUID())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(new ChangeStatusBody(PaymentAreaStatus.INACTIVE))))
				.andExpect(status().isNotFound());
	}

	@Test
	void optionsAreAccessibleToAnyAuthenticatedRole() throws Exception {
		jpaRepository.save(newArea("Opciones Docente", "OD" + suffix()));
		String token = tokenFor(RoleType.DOCENTE);

		mockMvc.perform(get("/payment-areas/options").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
	}

	@Test
	void unauthenticatedOptionsReturns401() throws Exception {
		mockMvc.perform(get("/payment-areas/options")).andExpect(status().isUnauthorized());
	}

	private String tokenFor(RoleType role) {
		return jwtService.sign(UUID.randomUUID().toString(), Set.of(role.name()));
	}

	/**
	 * Sufijo de 3 caracteres en base36 mayúscula, para que el código completo
	 * quepa en el {@code VARCHAR(5)} de {@code PaymentArea.code}: la regla de
	 * negocio del 2026-10-05 lo limita a <b>2 a 5 alfanuméricos en mayúscula</b>.
	 *
	 * <p>Antes era {@code UUID.randomUUID().substring(0, 8)}, o sea 8 hex en
	 * minúscula: con el prefijo de 2 letras daba 10 caracteres, y con los de 3
	 * daba 11. Los dos casos pasaban la validación cuando esta IT se escribió
	 * (no había {@code @Size} ni {@code @Pattern}) y hoy no pasarían —ni caben en
	 * la columna—, así que el generador tenía que cambiar con la regla.
	 *
	 * <p>Es un contador y no un azar a propósito: con 3 caracteres aleatorios
	 * sobre el alfabeto de 36 hay 46 656 combinaciones, y con ~40 fixtures en
	 * esta clase la probabilidad de que dos choquen en una misma ejecución es de
	 * alrededor del 1.7 %. Un contador es único por construcción, y como la base
	 * se recrea entre ejecuciones ({@code ddl-auto=create-drop}) no hace falta
	 * que dos ejecuciones distintas se distinguieran.
	 *
	 * <p>El {@code floorMod} recorta a los 3 dígitos de base36 que caben. Con las
	 * ~40 fixtures de esta clase nunca se llega al tope y el recorte es
	 * inofensivo, pero sin él un {@code substring} con un índice mayor que 3
	 * reventaría con {@code StringIndexOutOfBoundsException} en lugar de dar un
	 * código repetido y un fallo de clave única que sí se lee.
	 */
	private static String suffix() {
		int value = Math.floorMod(SEQUENCE.getAndIncrement(), 36 * 36 * 36);
		String base36 = Integer.toString(value, Character.MAX_RADIX).toUpperCase(Locale.ROOT);
		return "000".substring(base36.length()) + base36;
	}

	private static PaymentArea newArea(String name, String code) {
		return new PaymentArea(name, code, "Descripcion");
	}

	private static CreateBody validBody(String name, String code) {
		return new CreateBody(name, code, "Descripcion");
	}

	private record CreateBody(String name, String code, String description) {
	}

	private record ChangeStatusBody(PaymentAreaStatus status) {
	}
}
