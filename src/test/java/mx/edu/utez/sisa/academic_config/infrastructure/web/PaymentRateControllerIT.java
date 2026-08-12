package mx.edu.utez.sisa.academic_config.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentRate;
import mx.edu.utez.sisa.academic_config.infrastructure.persistence.PaymentConceptJpaRepository;
import mx.edu.utez.sisa.academic_config.infrastructure.persistence.PaymentRateJpaRepository;
import mx.edu.utez.sisa.identity.infrastructure.security.JwtService;
import mx.edu.utez.sisa.shared.model.AcademicLevel;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration coverage for the nested
 * {@code /payment-concepts/{conceptId}/rates} security matchers: real H2,
 * real JWT filter chain, no mocks — mirroring
 * {@code PaymentConceptControllerIT}'s style. Regression guard for the
 * matcher investigation documented in {@code SecurityFilterConfig}'s Javadoc:
 * GET is covered by the existing wildcarded {@code /payment-concepts/**}
 * matcher, but POST needed a dedicated new matcher since the existing POST
 * matcher is an exact (non-wildcarded) pattern.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentRateControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private PaymentConceptJpaRepository conceptJpaRepository;

	@Autowired
	private PaymentRateJpaRepository rateJpaRepository;

	@Test
	void adminCanSetRate() throws Exception {
		PaymentConcept concept = conceptJpaRepository.save(newConcept());
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/payment-concepts/{conceptId}/rates", concept.getId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody())))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.conceptId").value(concept.getId().toString()))
				.andExpect(jsonPath("$.amount").value(1500));
	}

	@Test
	void personalFinanzasCanSetRate() throws Exception {
		PaymentConcept concept = conceptJpaRepository.save(newConcept());
		String token = tokenFor(RoleType.PERSONAL_FINANZAS);

		mockMvc.perform(post("/payment-concepts/{conceptId}/rates", concept.getId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody())))
				.andExpect(status().isCreated());
	}

	@Test
	void serviciosEscolaresIsForbiddenOnSetRate() throws Exception {
		// Regression guard: the nested POST matcher grants the same
		// ADMIN/PERSONAL_FINANZAS pair as /payment-concepts itself, NOT
		// SERVICIOS_ESCOLARES.
		PaymentConcept concept = conceptJpaRepository.save(newConcept());
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(post("/payment-concepts/{conceptId}/rates", concept.getId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody())))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedSetRateReturns401() throws Exception {
		PaymentConcept concept = conceptJpaRepository.save(newConcept());

		mockMvc.perform(post("/payment-concepts/{conceptId}/rates", concept.getId()).contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody())))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void setRateWithNonExistentConceptReturns400() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/payment-concepts/{conceptId}/rates", UUID.randomUUID())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(validBody())))
				.andExpect(status().isBadRequest());
	}

	@Test
	void creatingASecondContinuousRateClosesTheFirst() throws Exception {
		PaymentConcept concept = conceptJpaRepository.save(newConcept());
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(post("/payment-concepts/{conceptId}/rates", concept.getId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(
						new CreateBody(null, null, BigDecimal.valueOf(1000), null, LocalDate.of(2025, 1, 1)))))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/payment-concepts/{conceptId}/rates", concept.getId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(
						new CreateBody(null, null, BigDecimal.valueOf(1500), null, LocalDate.of(2026, 6, 1)))))
				.andExpect(status().isCreated());

		List<PaymentRate> history = rateJpaRepository.findHistoryByConceptId(concept.getId());
		assertThat(history).hasSize(2);
		assertThat(history.stream().map(PaymentRate::getValidTo))
				.contains(LocalDate.of(2026, 5, 31));
	}

	@Test
	void adminCanListRates() throws Exception {
		PaymentConcept concept = conceptJpaRepository.save(newConcept());
		rateJpaRepository.save(new PaymentRate(concept.getId(), null, null, BigDecimal.valueOf(1000), null,
				LocalDate.of(2026, 1, 1)));
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(get("/payment-concepts/{conceptId}/rates", concept.getId())
				.header("Authorization", "Bearer " + token)).andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isNotEmpty());
	}

	@Test
	void serviciosEscolaresIsForbiddenOnListRates() throws Exception {
		PaymentConcept concept = conceptJpaRepository.save(newConcept());
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(get("/payment-concepts/{conceptId}/rates", concept.getId())
				.header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedListRatesReturns401() throws Exception {
		PaymentConcept concept = conceptJpaRepository.save(newConcept());

		mockMvc.perform(get("/payment-concepts/{conceptId}/rates", concept.getId()))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
	}

	private String tokenFor(RoleType role) {
		return jwtService.sign(UUID.randomUUID().toString(), Set.of(role.name()));
	}

	private static PaymentConcept newConcept() {
		return new PaymentConcept("Inscripcion Rate IT", "Descripcion", "Politicas", PaymentConceptType.ENROLLMENT,
				true, false, null, null, false, null, null);
	}

	private static CreateBody validBody() {
		return new CreateBody(null, AcademicLevel.LICENCIATURA, BigDecimal.valueOf(1500), null,
				LocalDate.of(2026, 1, 1));
	}

	private record CreateBody(UUID programId, AcademicLevel level, BigDecimal amount, UUID periodId,
			LocalDate validFrom) {
	}
}
