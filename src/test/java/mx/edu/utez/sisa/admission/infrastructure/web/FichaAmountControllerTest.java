package mx.edu.utez.sisa.admission.infrastructure.web;

import mx.edu.utez.sisa.admission.domain.port.in.GetFichaAmountUseCase;
import mx.edu.utez.sisa.admission.domain.port.in.GetFichaAmountUseCase.FichaAmountQuote;
import mx.edu.utez.sisa.admission.shared.exception.AmbiguousFichaPaymentConceptException;
import mx.edu.utez.sisa.admission.shared.exception.FichaPaymentConceptNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Thin-controller tests for the public ficha-price quote: the catalog amount
 * on {@code 200}, and the strict-resolution failures projected as {@code 409}
 * (no active enrollment concept, or an ambiguous price).
 */
@WebMvcTest(FichaAmountController.class)
@AutoConfigureMockMvc(addFilters = false)
class FichaAmountControllerTest {

	private static final UUID CONFIG_ID = UUID.randomUUID();

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private GetFichaAmountUseCase getFichaAmountUseCase;

	@MockitoBean
	private mx.edu.utez.sisa.identity.infrastructure.security.JwtService jwtService;

	@MockitoBean
	private mx.edu.utez.sisa.identity.infrastructure.security.PermissionCache permissionCache;

	@Test
	void returnsTheCatalogAmount() throws Exception {
		when(getFichaAmountUseCase.quote(CONFIG_ID)).thenReturn(new FichaAmountQuote(new BigDecimal("1578.00"),
				"Inscripción", "Ingeniería en Desarrollo y Gestión de Software"));

		mockMvc.perform(get("/program-admission-configs/{id}/ficha-amount", CONFIG_ID)).andExpect(status().isOk())
				.andExpect(jsonPath("$.amount").value(1578.00))
				.andExpect(jsonPath("$.currency").value("MXN"))
				.andExpect(jsonPath("$.conceptName").value("Inscripción"))
				.andExpect(jsonPath("$.programName").value("Ingeniería en Desarrollo y Gestión de Software"));
	}

	@Test
	void missingConceptIs409() throws Exception {
		when(getFichaAmountUseCase.quote(CONFIG_ID)).thenThrow(new FichaPaymentConceptNotFoundException(
				"No existe un concepto de ENROLLMENT activo para el programa: " + CONFIG_ID));

		mockMvc.perform(get("/program-admission-configs/{id}/ficha-amount", CONFIG_ID))
				.andExpect(status().isConflict());
	}

	@Test
	void ambiguousConceptIs409() throws Exception {
		when(getFichaAmountUseCase.quote(CONFIG_ID)).thenThrow(
				new AmbiguousFichaPaymentConceptException("Existen varios conceptos de ENROLLMENT activos"));

		mockMvc.perform(get("/program-admission-configs/{id}/ficha-amount", CONFIG_ID))
				.andExpect(status().isConflict());
	}
}
