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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandidateFichaPdfServiceTest {

	/**
	 * The notice text lives in the service (single source of truth). The PDF
	 * extractor breaks the string across lines, so the assertions match on its
	 * two halves rather than the whole sentence.
	 */
	private static final String NON_OFFICIAL_NOTICE_PART_1 = "Copia para el candidato";

	private static final String NON_OFFICIAL_NOTICE_PART_2 = "sin validez oficial";

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
			assertTrue(text.contains("Negocio propio"));
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

	/**
	 * The applicant downloads this herself, so it must never read like an
	 * official certificate. Asserted on the notice under the title.
	 */
	@Test
	void rendersTheNonOfficialNotice() throws Exception {
		byte[] pdf = service.render(fullFicha());

		try (PdfReader reader = new PdfReader(pdf)) {
			String text = text(reader);
			assertTrue(text.contains(NON_OFFICIAL_NOTICE_PART_1), "expected the non-official notice");
			assertTrue(text.contains(NON_OFFICIAL_NOTICE_PART_2), "expected the non-official notice");
		}
	}

	/**
	 * A notice on page 1 alone is worthless once the ficha is split and a single
	 * page is forwarded, so the footer must repeat it on EVERY page.
	 */
	@Test
	void repeatsTheNonOfficialNoticeOnEveryPage() throws Exception {
		byte[] pdf = service.render(fullFicha());

		try (PdfReader reader = new PdfReader(pdf)) {
			PdfTextExtractor extractor = new PdfTextExtractor(reader);
			assertTrue(reader.getNumberOfPages() >= 2, "fixture must span several pages to be meaningful");
			for (int page = 1; page <= reader.getNumberOfPages(); page++) {
				String pageText = extractor.getTextFromPage(page);
				assertTrue(pageText.contains(NON_OFFICIAL_NOTICE_PART_1),
						"page " + page + " is missing the non-official notice");
				assertTrue(pageText.contains("Página " + page),
						"page " + page + " is missing its page number");
			}
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

	/**
	 * The applicant carries this PDF to ventanilla, so a date printed under the
	 * wrong label is the failure that gets argued about at the window. The
	 * registration row and the "Fecha límite de pago" row get their own labels,
	 * with the fixture's two different days (10/03 registration, 03/03 the
	 * ficha's visible plazo) so a swap cannot pass.
	 */
	@Test
	void printsBothWindowsUnderTheirOwnLabels() throws Exception {
		byte[] pdf = service.render(fullFicha());

		try (PdfReader reader = new PdfReader(pdf)) {
			String text = text(reader);
			assertTrue(text.contains("Fecha límite de inscripción"), "missing the registration-window row");
			assertTrue(text.contains("Fecha límite de pago"), "missing the payment-window row");
			assertTrue(text.contains("10/03/2026"), "missing the registration deadline");
			assertTrue(text.contains("03/03/2026"), "missing the ficha's visible payment deadline");
		}
	}

	/**
	 * The concept's own closing day is an engine boundary and is not printed:
	 * only the ficha's visible plazo belongs under "Fecha límite de pago". The
	 * fixture's {@code paymentClosesOn} (05/03) must therefore never appear.
	 */
	@Test
	void neverPrintsTheConceptsOwnClosingDayAsThePaymentDeadline() throws Exception {
		byte[] pdf = service.render(fullFicha());

		try (PdfReader reader = new PdfReader(pdf)) {
			assertFalse(text(reader).contains("05/03/2026"),
					"the concept's available_until is not the date promised to the applicant");
		}
	}

	/**
	 * When the ficha's visible plazo is the same day as the sales window there is
	 * only one date to state, so the payment row is dropped instead of printing
	 * the same day twice under two labels.
	 */
	@Test
	void omitsThePaymentRowWhenItMatchesTheRegistrationDeadline() throws Exception {
		byte[] pdf = service.render(fichaWithPaymentDeadline(LocalDate.of(2026, 3, 10)));

		try (PdfReader reader = new PdfReader(pdf)) {
			assertFalse(text(reader).contains("Fecha límite de pago"),
					"an equal deadline must not be repeated under a second label");
		}
	}

	/**
	 * A ficha nobody has started paying for has no order, and the row has to go with
	 * it.
	 *
	 * <p>The order id is {@code null} for every candidate between registering and
	 * clicking "Pagar" — which is exactly who this PDF is printed for. Asserting the
	 * absence, and not just that the render succeeds, is what pins it: a placeholder
	 * or a bare "null" would satisfy {@link #rendersMinimalFichaWithoutFailing()} and
	 * still tell the applicant at the window that something went missing. The
	 * counterpart is asserted by
	 * {@link #rendersFullPaso4FichaWithResolvedLabelsAndPayment()}, so the row cannot
	 * simply have been dropped for good.
	 */
	@Test
	void omitsTheEvoOrderRowWhenNoCheckoutWasEverStarted() throws Exception {
		byte[] pdf = service.render(minimalFicha());

		try (PdfReader reader = new PdfReader(pdf)) {
			String text = text(reader);
			assertFalse(text.contains("Orden de pago"),
					"a ficha with no order must not carry an empty order row");
			assertFalse(text.contains("null"),
					"the missing order must be omitted, not printed as null");
			assertTrue(text.contains("Referencia de pago"),
					"the row that exists must still be printed");
		}
	}

	/**
	 * No closing date on the concept means the period has no end, so the row is
	 * dropped. A dashed "Fecha límite de pago: -" would instead read as a date
	 * that went missing, which is a different and wrong story.
	 */
	@Test
	void omitsThePaymentWindowRowWhenTheConceptHasNoClosingDate() throws Exception {
		byte[] pdf = service.render(minimalFicha());

		try (PdfReader reader = new PdfReader(pdf)) {
			String text = text(reader);
			assertFalse(text.contains("Fecha límite de pago"),
					"the payment-window row must be omitted, not dashed");
		}
	}

	/**
	 * The applicant reads this at the window and at home, so the status has to be
	 * Spanish prose. It used to print {@code paymentStatus().name()}, which put
	 * "PAID"/"PENDING" in the document. Asserting the negative matters: a label
	 * that happens to contain the enum name somewhere else would pass otherwise.
	 */
	@Test
	void printsThePaymentStatusAsSpanishTextAndNeverAsTheEnumName() throws Exception {
		try (PdfReader reader = new PdfReader(service.render(fullFicha()))) {
			String text = text(reader);
			assertTrue(text.contains("Estado de pago"));
			assertTrue(text.contains("Pagado"), "PAID must be written as prose");
			assertFalse(text.contains("PAID"), "the enum name leaked into the document");
		}
		try (PdfReader reader = new PdfReader(service.render(minimalFicha()))) {
			String text = text(reader);
			assertTrue(text.contains("Pendiente"), "PENDING must be written as prose");
			assertFalse(text.contains("PENDING"), "the enum name leaked into the document");
		}
	}

	/**
	 * Regression guard for a silent data loss, not a layout change: the employment
	 * type used to go through a switch that only knew "tiempo completo" and "medio
	 * tiempo" and returned null for everything else, so "Negocio propio" never
	 * reached the PDF. The field is free text now, and whatever was written has to
	 * come out verbatim.
	 */
	@Test
	void keepsTheEmploymentTypeExactlyAsTheApplicantWroteIt() throws Exception {
		byte[] pdf = service.render(fullFicha());

		try (PdfReader reader = new PdfReader(pdf)) {
			assertTrue(text(reader).contains("Tipo de Trabajo"));
			assertTrue(text(reader).contains("Negocio propio"),
					"the employment type is free text and must survive verbatim");
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
		return fullFicha(LocalDate.of(2026, 3, 3));
	}

	/** Same fixture as {@link #fullFicha()} but with a chosen visible payment deadline. */
	private static FichaData fichaWithPaymentDeadline(LocalDate paymentDeadline) {
		return fullFicha(paymentDeadline);
	}

	private static FichaData fullFicha(LocalDate paymentDeadline) {
		return new FichaData(
				UUID.fromString("11111111-1111-1111-1111-111111111111"),
				"ADM-2026-000001",
				CandidateStatus.REGISTERED,
				Instant.parse("2026-01-20T12:00:00Z"),
				UUID.fromString("22222222-2222-2222-2222-222222222222"),
				"Licenciatura en Enfermería",
				"División de Ciencias de la Salud",
				"SASC930515MJCLNN09",
				"Ana",
				"Salas",
				"Corona",
				"ana.salas@example.com",
				"351 516 23 41",
				"351 100 20 30",
				"REFA-2026-000001",
				new BigDecimal("750.00"),
				// registrationDeadline, paymentClosesOn, paymentDeadline:
				// different days on purpose, because the PDF prints the first and
				// the last under separate labels and equal values would let a swap
				// through unnoticed. paymentClosesOn (05/03) is an engine boundary
				// and must never be the printed "Fecha límite de pago".
				LocalDate.of(2026, 3, 10),
				LocalDate.of(2026, 3, 5),
				paymentDeadline,
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
new Ingresos(new BigDecimal("8500.00"), true, "Negocio propio", "351 516 23 41",
					new BigDecimal("6500.00"), "Ferretería López", "Cajero", LocalTime.of(9, 0), LocalTime.of(18, 0)),
				new SeleccionCarrera("Mixta", "Amigo que estudia aquí", true, "Enero – Abril 2026"),
				new AntecedentesEscolares("CBTis 121", "Bachillerato General", true, "Michoacán de Ocampo", "Jiquilpan",
						null, null, new BigDecimal("8.90"), "16DCT0121B"),
				null);
	}

	private static FichaData minimalFicha() {
		return new FichaData(
				UUID.fromString("33333333-3333-3333-3333-333333333333"),
				"ADM-2026-000099",
				CandidateStatus.REGISTERED,
				Instant.parse("2026-01-20T12:00:00Z"),
				null,
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
				new AntecedentesEscolares("", "", true, "", "", null, "", null, ""),
				null);
	}
}
