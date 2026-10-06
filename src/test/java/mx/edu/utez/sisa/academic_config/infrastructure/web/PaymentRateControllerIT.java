package mx.edu.utez.sisa.academic_config.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConcept;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentConceptType;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentRate;
import mx.edu.utez.sisa.academic_config.domain.model.PaymentRateStatus;
import mx.edu.utez.sisa.academic_config.infrastructure.persistence.PaymentConceptJpaRepository;
import mx.edu.utez.sisa.academic_config.infrastructure.persistence.PaymentRateJpaRepository;
import mx.edu.utez.sisa.identity.infrastructure.security.JwtService;
import mx.edu.utez.sisa.shared.model.RoleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration coverage for the nested
 * {@code /payment-concepts/{conceptId}/rates} security matchers: real H2,
 * real JWT filter chain, no mocks — mirroring
 * {@code PaymentConceptControllerIT}'s style. Regression guard for the
 * matcher investigation documented in {@code SecurityFilterConfig}'s Javadoc:
 * GET is covered by the existing wildcarded {@code /payment-concepts/**}
 * matcher, but the write verb needed a dedicated matcher since the existing
 * POST matcher is an exact (non-wildcarded) pattern.
 *
 * <p>
 * The write verb is {@code PUT} of the complete set rather than a {@code POST}
 * of one rate, so these tests exercise the reconciliation end to end: that a
 * changed amount deactivates the row it replaces instead of closing it on a
 * date, and that the history keeps both.
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
	void adminCanReconcileRates() throws Exception {
		PaymentConcept concept = conceptJpaRepository.save(newConcept());
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(put("/payment-concepts/{conceptId}/rates", concept.getId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(body(1500))))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items[0].conceptId").value(concept.getId().toString()))
				.andExpect(jsonPath("$.items[0].amount").value(1500))
				.andExpect(jsonPath("$.items[0].status").value("ACTIVE"));
	}

	@Test
	void personalFinanzasCanReconcileRates() throws Exception {
		PaymentConcept concept = conceptJpaRepository.save(newConcept());
		String token = tokenFor(RoleType.PERSONAL_FINANZAS);

		mockMvc.perform(put("/payment-concepts/{conceptId}/rates", concept.getId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(body(1500))))
				.andExpect(status().isOk());
	}

	@Test
	void serviciosEscolaresIsForbiddenOnReconcile() throws Exception {
		// Regression guard: the nested write matcher grants the same
		// ADMIN/PERSONAL_FINANZAS pair as /payment-concepts itself, NOT
		// SERVICIOS_ESCOLARES.
		PaymentConcept concept = conceptJpaRepository.save(newConcept());
		String token = tokenFor(RoleType.SERVICIOS_ESCOLARES);

		mockMvc.perform(put("/payment-concepts/{conceptId}/rates", concept.getId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(body(1500))))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedReconcileReturns401() throws Exception {
		PaymentConcept concept = conceptJpaRepository.save(newConcept());

		mockMvc.perform(put("/payment-concepts/{conceptId}/rates", concept.getId()).contentType("application/json")
				.content(objectMapper.writeValueAsString(body(1500))))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void reconcileWithNonExistentConceptReturns400() throws Exception {
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(put("/payment-concepts/{conceptId}/rates", UUID.randomUUID())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(body(1500))))
				.andExpect(status().isBadRequest());
	}

	/**
	 * The history guarantee, end to end. A second, different amount for the same
	 * destination deactivates the first row and inserts a new ACTIVE one, keeping
	 * both — this used to be asserted by checking that the first row's
	 * {@code validTo} had been set to the day before the second opened.
	 */
	@Test
	void reconcilingADifferentAmountDeactivatesThePreviousRowAndKeepsIt() throws Exception {
		PaymentConcept concept = conceptJpaRepository.save(newConcept());
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(put("/payment-concepts/{conceptId}/rates", concept.getId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(body(1000)))).andExpect(status().isOk());

		mockMvc.perform(put("/payment-concepts/{conceptId}/rates", concept.getId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(body(1500)))).andExpect(status().isOk());

		List<PaymentRate> history = rateJpaRepository.findHistoryByConceptId(concept.getId());

		assertThat(history).hasSize(2);
		assertThat(history).extracting(PaymentRate::getStatus)
				.containsExactlyInAnyOrder(PaymentRateStatus.ACTIVE, PaymentRateStatus.INACTIVE);
		// Compare by value, not by equals: the persisted column carries scale 2
		// (1500.00), so a scale-0 literal would fail an otherwise correct match.
		assertThat(history).extracting(PaymentRate::getAmount)
				.usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
				.containsExactlyInAnyOrder(BigDecimal.valueOf(1000), BigDecimal.valueOf(1500));
	}

	/**
	 * And re-sending the same amount is a no-op: no row churn, no fake history
	 * entry for a save that changed nothing.
	 */
	@Test
	void reconcilingTheSameAmountLeavesTheHistoryAlone() throws Exception {
		PaymentConcept concept = conceptJpaRepository.save(newConcept());
		String token = tokenFor(RoleType.ADMIN);

		mockMvc.perform(put("/payment-concepts/{conceptId}/rates", concept.getId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(body(1000)))).andExpect(status().isOk());
		mockMvc.perform(put("/payment-concepts/{conceptId}/rates", concept.getId())
				.header("Authorization", "Bearer " + token).contentType("application/json")
				.content(objectMapper.writeValueAsString(body(1000)))).andExpect(status().isOk());

		assertThat(rateJpaRepository.findHistoryByConceptId(concept.getId())).hasSize(1);
	}

	@Test
	void adminCanListRates() throws Exception {
		PaymentConcept concept = conceptJpaRepository.save(newConcept());
		rateJpaRepository.save(new PaymentRate(concept.getId(), null, null, BigDecimal.valueOf(1000), null,
				LocalDateTime.of(2026, 1, 1, 8, 0)));
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

	/**
	 * A fresh concept per test, and {@code code} is unique, so the code is
	 * suffixed too — otherwise the second test in the class collides with the
	 * first one's row.
	 */
	private static PaymentConcept newConcept() {
		String suffix = UUID.randomUUID().toString();
		return new PaymentConcept("Inscripcion Rate IT " + suffix, "RIT-" + suffix.substring(0, 8), "Descripcion",
				"Politicas", PaymentConceptType.ENROLLMENT, null, false, null, null, false, null, null);
	}

	private static ReconcileBody body(int amount) {
		return new ReconcileBody(
				List.of(new DraftBody(null, mx.edu.utez.sisa.shared.model.AcademicLevel.LICENCIATURA,
						BigDecimal.valueOf(amount), null)));
	}

	private record ReconcileBody(List<DraftBody> rates) {
	}

	private record DraftBody(UUID programId, mx.edu.utez.sisa.shared.model.AcademicLevel level, BigDecimal amount,
			UUID periodId) {
	}
}