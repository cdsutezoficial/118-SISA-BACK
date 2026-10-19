package mx.edu.utez.sisa.shared.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for the public postal-code lookup.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PostalCodeControllerIT {

	@MockitoBean
	private JavaMailSender mailSender;

	@Autowired
	private MockMvc mockMvc;

	@Test
	void publicLookupReturnsBothSettlementsForPostalCode01030() throws Exception {
		mockMvc.perform(get("/postal-codes/01030")).andExpect(status().isOk())
				.andExpect(jsonPath("$.postalCode").value("01030"))
				.andExpect(jsonPath("$.message")
						.value("Se encontraron 2 colonias para este código postal. Selecciona la tuya."))
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[?(@.settlementName == 'Axotla')].localityName")
						.value("Ciudad de México"))
				.andExpect(jsonPath("$.items[?(@.settlementName == 'Axotla')].municipalityName")
						.value("Álvaro Obregón"))
				.andExpect(jsonPath("$.items[?(@.settlementName == 'Florida')].stateName")
						.value("Ciudad de México"));
	}

	@Test
	void localityFallsBackToMunicipalityWhenSepomexCityIsBlank() throws Exception {
		mockMvc.perform(get("/postal-codes/62765")).andExpect(status().isOk())
				.andExpect(jsonPath("$.postalCode").value("62765"))
				.andExpect(jsonPath("$.items[0].localityName").value("Emiliano Zapata"));
	}

	@Test
	void unknownPostalCodeReturnsAnEmptyListWithAMessage() throws Exception {
		mockMvc.perform(get("/postal-codes/99999")).andExpect(status().isOk())
				.andExpect(jsonPath("$.postalCode").value("99999"))
				.andExpect(jsonPath("$.message").value(
						"No encontramos colonias registradas para este código postal. Puedes capturar tu domicilio manualmente."))
				.andExpect(jsonPath("$.items.length()").value(0));
	}

	@Test
	void malformedPostalCodeReturnsBadRequestWithAMessage() throws Exception {
		mockMvc.perform(get("/postal-codes/abc")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("El código postal debe tener 5 dígitos."));
	}
}