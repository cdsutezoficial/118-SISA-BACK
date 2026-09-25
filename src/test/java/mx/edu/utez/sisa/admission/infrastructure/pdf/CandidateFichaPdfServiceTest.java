package mx.edu.utez.sisa.admission.infrastructure.pdf;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import mx.edu.utez.sisa.admission.domain.model.AdmissionPaymentStatus;
import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateFichaUseCase.FichaData;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateFichaUseCase.FichaData.AntecedentesEscolares;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateFichaUseCase.FichaData.DatosGenerales;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateFichaUseCase.FichaData.Domicilio;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateFichaUseCase.FichaData.InformacionComplementaria;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateFichaUseCase.FichaData.Ingresos;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateFichaUseCase.FichaData.SeleccionCarrera;
import mx.edu.utez.sisa.shared.model.EmploymentType;
import mx.edu.utez.sisa.shared.model.Gender;
import mx.edu.utez.sisa.shared.model.MaritalStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandidateFichaPdfServiceTest {

	private final CandidateFichaPdfService service = new CandidateFichaPdfService();

	@Test
	void rendersFullPaso4FichaWithResolvedLabelsAndPayment() throws Exception {
		byte[] pdf = service.render(fullFicha());

		assertTrue(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1).startsWith("%PDF-"));

		try (PdfReader reader = new PdfReader(pdf)) {
			assertTrue(reader.getNumberOfPages() >= 1);
			String text = text(reader);
			assertTrue(text.contains("FICHA DE ADMISIÓN"));
			assertTrue(text.contains("Período: Enero – Abril 2026"));
			assertTrue(text.contains("Folio"));
			assertTrue(text.contains("ADM-2026-000001"));
			assertTrue(text.contains("Datos Generales"));
			assertTrue(text.contains("Femenino"));
			assertTrue(text.contains("Soltero"));
			assertTrue(text.contains("Domicilio Actual"));
			assertTrue(text.contains("Código Postal"));
			assertTrue(text.contains("59510"));
			assertTrue(text.contains("Contacto"));
			assertTrue(text.contains("Correo Electrónico"));
			assertTrue(text.contains("ana.salas@example.com"));
			assertTrue(text.contains("Información Complementaria"));
			assertTrue(text.contains("LGBTTTIQ+"));
			assertTrue(text.contains("Ingresos"));
			assertTrue(text.contains("Permanente"));
			assertTrue(text.contains("Selección de Carrera"));
			assertTrue(text.contains("Mixta"));
			assertTrue(text.contains("Medio de Difusión"));
			assertTrue(text.contains("Sí, es mi primera opción"));
			assertTrue(text.contains("Antecedentes Escolares"));
			assertTrue(text.contains("Tipo de Bachillerato"));
			assertTrue(text.contains("8.9"));
			assertTrue(text.contains("Orden de pago (EVO)"));
			assertTrue(text.contains("TESTUTEZ123456"));
		}
	}

	@Test
	void rendersMinimalFichaWithoutFailing() throws Exception {
		byte[] pdf = service.render(minimalFicha());

		try (PdfReader reader = new PdfReader(pdf)) {
			assertTrue(reader.getNumberOfPages() >= 1);
			String text = text(reader);
			assertTrue(text.contains("FICHA DE ADMISIÓN"));
			assertTrue(text.contains("Referencia de pago"));
			assertTrue(text.contains("REF-0001"));
		}
	}

	private static String text(PdfReader reader) throws Exception {
		PdfTextExtractor extractor = new PdfTextExtractor(reader);
		StringBuilder text = new StringBuilder();
		for (int i = 1; i <= reader.getNumberOfPages(); i++) {
			text.append(extractor.getTextFromPage(i));
		}
		return text.toString();
	}

	private static FichaData fullFicha() {
		return new FichaData(
				UUID.fromString("11111111-1111-1111-1111-111111111111"),
				"ADM-2026-000001",
				CandidateStatus.REGISTERED,
				Instant.parse("2026-01-20T12:00:00Z"),
				UUID.fromString("22222222-2222-2222-2222-222222222222"),
				"Licenciatura en Enfermería",
				"SASC930515MJCLNN09",
				"Ana",
				"Salas",
				"Corona",
				"ana.salas@example.com",
				"351 516 23 41",
				"351 100 20 30",
				"REFA-2026-000001",
				new BigDecimal("750.00"),
				LocalDate.of(2026, 3, 10),
				AdmissionPaymentStatus.PAID,
				"REC-2026-0042",
				Instant.parse("2026-02-02T14:30:00Z"),
				"TESTUTEZ123456",
				new DatosGenerales(LocalDate.of(1993, 5, 15), Gender.F, "Mexicana", "Michoacán de Ocampo", "Jiquilpan",
						MaritalStatus.SOLTERO, "Español", true),
				new Domicilio("Av. Lázaro Cárdenas", "100", "A", "Centro", "Jiquilpan", "59510", "Michoacán de Ocampo",
						"Jiquilpan"),
				new InformacionComplementaria(true, "Asma leve", false, null, true, "Purépecha", true, "Purépecha",
						true, false, true, true, true),
				new Ingresos(new BigDecimal("8500.00"), true, EmploymentType.PERMANENT, "351 516 23 41",
						new BigDecimal("6500.00"), "Ferretería López", "Cajero", LocalTime.of(9, 0), LocalTime.of(18, 0)),
				new SeleccionCarrera("Mixta", "Amigo que estudia aquí", true, "Enero – Abril 2026"),
				new AntecedentesEscolares("CBTis 121", "Bachillerato General", true, "Michoacán de Ocampo", "Jiquilpan",
						null, null, new BigDecimal("8.90"), "16DCT0121B"));
	}

	private static FichaData minimalFicha() {
		return new FichaData(
				UUID.fromString("33333333-3333-3333-3333-333333333333"),
				"ADM-2026-000099",
				CandidateStatus.REGISTERED,
				Instant.parse("2026-01-20T12:00:00Z"),
				null,
				null,
				"",
				"",
				"",
				"",
				"",
				"",
				"",
				"REF-0001",
				null,
				null,
				AdmissionPaymentStatus.PENDING,
				null,
				null,
				null,
				new DatosGenerales(null, null, "", "", "", null, "", false),
				new Domicilio("", "", "", "", "", "", "", ""),
				new InformacionComplementaria(false, null, false, null, false, null, false, null, false, false, false,
						false, false),
				new Ingresos(null, false, null, "", null, "", "", null, null),
				new SeleccionCarrera("", "", false, ""),
				new AntecedentesEscolares("", "", true, "", "", null, "", null, ""));
	}
}