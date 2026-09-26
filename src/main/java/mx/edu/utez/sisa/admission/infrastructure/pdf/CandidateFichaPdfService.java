package mx.edu.utez.sisa.admission.infrastructure.pdf;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.RGBColor;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateFichaUseCase.FichaData;
import mx.edu.utez.sisa.shared.model.EmploymentType;
import mx.edu.utez.sisa.shared.model.Gender;
import mx.edu.utez.sisa.shared.model.MaritalStatus;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Renders the admission ficha (screen 13) as a downloadable PDF via OpenPDF
 * (LGPL — the approved PDF library for the SISA repo), served by
 * {@code GET /candidates/{id}/ficha.pdf}. Pure presentation: layouts the
 * {@link FichaData} produced by {@code GetCandidateFichaUseCase}; no writes,
 * no state, deliberately a stateless singleton bean.
 *
 * <p>Fase 8 restructured the layout to mirror the "Paso 4" confirmation of
 * {@code CandidatoRegistro.tsx}: title + period, then the payment block
 * (folio, program, amount, reference, deadline, status, receipt, EVO order)
 * and the sectioned form — Datos Generales, Domicilio Actual, Contacto,
 * Información Complementaria, Ingresos, Selección de Carrera and Antecedentes
 * Escolares. Catalog ids reach the PDF already resolved to display names by
 * the use case (Fase 7); enum labels (sexo/estado civil/tipo de trabajo)
 * resolve here so the download reads the same Spanish as the portal.
 *
 * <p><b>Non-official copy.</b> The applicant can download this from her own
 * ficha, so it must never be mistakable for the official record: the
 * {@link #NON_OFFICIAL_NOTICE} banner sits directly under the title and the
 * same text is repeated in the footer of EVERY page by
 * {@link NonOfficialWatermark} — a single notice on page 1 of a 3-page ficha
 * would be lost the moment the page is forwarded on its own.
 */
@Component
public class CandidateFichaPdfService {

	private static final RGBColor BRAND = new RGBColor(0x00, 0x95, 0x74);
	private static final RGBColor LABEL_GRAY = new RGBColor(0x6B, 0x72, 0x80);
	private static final RGBColor VALUE_DARK = new RGBColor(0x33, 0x33, 0x33);
	private static final RGBColor PAYMENT_BG = new RGBColor(0xF3, 0xF4, 0xF6);

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

	/**
	 * The applicant downloads this file herself, so it is explicitly NOT the
	 * official ficha. Wording is fixed by the business: the only valid ficha is
	 * the one the Universidad issues.
	 */
	private static final String NON_OFFICIAL_NOTICE = "Copia para el candidato — documento sin validez oficial. "
			+ "La ficha oficial es la expedida por la Universidad.";

	/** Bottom margin reserved for the repeating footer written by NonOfficialWatermark. */
	private static final float FOOTER_MARGIN = 28f;

	public byte[] render(FichaData ficha) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Document document = new Document();
		// reserve room for the per-page footer before any content is written
		document.setMargins(document.leftMargin(), document.rightMargin(), document.topMargin(),
				document.bottomMargin() + FOOTER_MARGIN);
		PdfWriter writer = PdfWriter.getInstance(document, out);
		writer.setPageEvent(new NonOfficialWatermark());
		document.open();
		document.add(title(ficha));
		document.add(nonOfficialNotice());
		document.add(pagoRows(ficha));
		heading(document, "Datos Generales");
		document.add(datosGenerales(ficha));
		heading(document, "Domicilio Actual");
		document.add(domicilio(ficha));
		heading(document, "Contacto");
		document.add(contacto(ficha));
		heading(document, "Información Complementaria");
		document.add(informacionComplementaria(ficha));
		heading(document, "Ingresos");
		document.add(ingresos(ficha));
		heading(document, "Selección de Carrera");
		document.add(seleccionCarrera(ficha));
		heading(document, "Antecedentes Escolares");
		document.add(antecedentesEscolares(ficha));
		document.close();
		return out.toByteArray();
	}

	/**
	 * Boxed notice right under the title. A colored background plus a border
	 * keeps it visually distinct from the "FICHA DE ADMISIÓN" heading, which is
	 * the point: this must not read like an official certificate.
	 */
	private static PdfPTable nonOfficialNotice() {
		Paragraph text = new Paragraph(NON_OFFICIAL_NOTICE,
				FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, BRAND));
		text.setAlignment(Element.ALIGN_CENTER);
		PdfPCell cell = new PdfPCell(text);
		cell.setBackgroundColor(PAYMENT_BG);
		cell.setBorderWidth(0.5f);
		cell.setBorderColor(BRAND);
		cell.setPadding(6);
		cell.setHorizontalAlignment(Element.ALIGN_CENTER);
		PdfPTable table = new PdfPTable(1);
		table.setWidthPercentage(100);
		table.setSpacingAfter(10);
		table.addCell(cell);
		return table;
	}

	/**
	 * Stamps the non-official notice plus the page number into the footer of
	 * every page, so a single forwarded page still carries the disclaimer.
	 *
	 * <p>Written through {@link ColumnText} on the writer's direct content
	 * stream rather than {@code document.add}. Adding to the document from
	 * {@code onEndPage} recurses: the footer can overflow the remaining space,
	 * which triggers a page break, which fires {@code onEndPage} again, which
	 * adds another footer — infinite loop (observed as a {@code StackOverflowError}).
	 * Direct content bypasses the document's flow entirely, so no recursion is
	 * possible. {@link #FOOTER_MARGIN} is still reserved so the last table row
	 * does not sit on top of the footer.
	 */
	private static final class NonOfficialWatermark extends PdfPageEventHelper {

		@Override
		public void onEndPage(PdfWriter writer, Document document) {
			float left = document.leftMargin();
			float width = writer.getPageSize().getWidth() - left - document.rightMargin();

			ColumnText column = new ColumnText(writer.getDirectContent());
			// (phrase, llx, lly, urx, ury, leading, alignment) — an absolute box
			// near the bottom of the page, outside the document's flow
			column.setSimpleColumn(footerText(writer), left, 16f, left + width, 16f + 24f, 8f,
					Element.ALIGN_CENTER);
			// a non-OK status only means the box was too small; the notice text
			// is one short line, so there is nothing useful to do about it here
			column.go();
		}

		private static Phrase footerText(PdfWriter writer) {
			return new Phrase(NON_OFFICIAL_NOTICE + "   ·   Página " + writer.getPageNumber(),
					FontFactory.getFont(FontFactory.HELVETICA, 7, LABEL_GRAY));
		}
	}

	private static Paragraph title(FichaData ficha) {
		Paragraph title = new Paragraph("FICHA DE ADMISIÓN", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, VALUE_DARK));
		title.setAlignment(Element.ALIGN_CENTER);
		title.add(new Chunk("\nPeríodo: " + text(ficha.seleccionCarrera().periodName()),
				FontFactory.getFont(FontFactory.HELVETICA, 10, LABEL_GRAY)));
		title.setSpacingAfter(10);
		return title;
	}

	private static void heading(Document document, String title) {
		Chunk titleChunk = new Chunk(title, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, BRAND));
		titleChunk.setUnderline(0.6f, -2f);
		Paragraph heading = new Paragraph(titleChunk);
		heading.setSpacingBefore(18);
		heading.setSpacingAfter(8);
		document.add(heading);
	}

	private static PdfPTable pagoRows(FichaData ficha) {
		PdfPTable table = sectionTable();
		pagoRow(table, "Folio", ficha.folio());
		pagoRow(table, "Carrera", ficha.programName());
		pagoRow(table, "Monto a pagar", ficha.amount());
		pagoRow(table, "Referencia de pago", ficha.referenceNumber());
		pagoRow(table, "Fecha límite de pago", ficha.deadline());
		pagoRow(table, "Estado de pago", ficha.paymentStatus().name());
		if (ficha.receiptNumber() != null) {
			pagoRow(table, "Recibo", ficha.receiptNumber());
			pagoRow(table, "Fecha de pago", ficha.paidAt());
		}
		if (ficha.orderId() != null) {
			pagoRow(table, "Orden de pago (EVO)", ficha.orderId());
		}
		return table;
	}

	private static PdfPTable datosGenerales(FichaData ficha) {
		FichaData.DatosGenerales datos = ficha.datosGenerales();
		PdfPTable table = sectionTable();
		row(table, "Nombre Completo", fullName(ficha));
		row(table, "CURP", ficha.curp());
		row(table, "Fecha de Nacimiento", datos.birthDate());
		row(table, "Sexo", datos.gender());
		row(table, "Nacionalidad", datos.nationality());
		row(table, "Estado de Nacimiento", datos.birthStateName());
		row(table, "Municipio de Nacimiento", datos.birthMunicipalityName());
		row(table, "Estado Civil", datos.maritalStatus());
		row(table, "Lengua Natal", datos.nativeLanguage());
		row(table, "¿Tiene Hijos?", datos.hasChildren());
		return table;
	}

	private static PdfPTable domicilio(FichaData ficha) {
		FichaData.Domicilio domicilio = ficha.domicilio();
		PdfPTable table = sectionTable();
		row(table, "Calle", domicilio.street());
		row(table, "Número Exterior", domicilio.exteriorNumber());
		row(table, "Número Interior", domicilio.interiorNumber());
		row(table, "Colonia", domicilio.neighborhood());
		row(table, "Estado", domicilio.stateName());
		row(table, "Municipio", domicilio.municipalityName());
		row(table, "Localidad", domicilio.locality());
		row(table, "Código Postal", domicilio.postalCode());
		return table;
	}

	private static PdfPTable contacto(FichaData ficha) {
		PdfPTable table = sectionTable();
		row(table, "Correo Electrónico", ficha.email());
		row(table, "Teléfono Casa", ficha.homePhone());
		row(table, "Celular", ficha.mobilePhone());
		return table;
	}

	private static PdfPTable informacionComplementaria(FichaData ficha) {
		FichaData.InformacionComplementaria info = ficha.informacionComplementaria();
		PdfPTable table = sectionTable();
		row(table, "¿Enfermedad o Diagnóstico Preexistente?", info.hasPreexistingCondition());
		if (info.hasPreexistingCondition()) {
			row(table, "Enfermedad o Diagnóstico", info.conditionDescription());
		}
		row(table, "¿Discapacidad?", info.hasDisability());
		if (info.hasDisability()) {
			row(table, "Discapacidad", info.disabilityDescription());
		}
		row(table, "¿Padres Hablan Lengua Indígena?", info.parentsSpeakIndigenousLanguage());
		if (info.parentsSpeakIndigenousLanguage()) {
			row(table, "Lengua de los Padres", info.parentsIndigenousLanguage());
		}
		row(table, "¿Habla Lengua Indígena?", info.speaksIndigenousLanguage());
		if (info.speaksIndigenousLanguage()) {
			row(table, "Lengua que Habla", info.indigenousLanguage());
		}
		row(table, "¿Se Identifica Indígena?", info.selfIdentifiesIndigenous());
		row(table, "¿Se Identifica No Binario?", info.selfIdentifiesNonBinary());
		row(table, "¿Pertenece a la Comunidad LGBTTTIQ+?", info.belongsToLgbttiqCommunity());
		row(table, "¿Es Afrodescendiente?", info.isAfrodescendant());
		if (info.isAfrodescendant()) {
			row(table, "¿Se Identifica Afrodescendiente?", info.selfIdentifiesAfrodescendant());
		}
		return table;
	}

	private static PdfPTable ingresos(FichaData ficha) {
		FichaData.Ingresos ingresos = ficha.ingresos();
		PdfPTable table = sectionTable();
		row(table, "Ingreso Mensual Familiar", ingresos.monthlyFamilyIncome());
		row(table, "¿Trabaja?", ingresos.isEmployed());
		if (ingresos.isEmployed()) {
			row(table, "Tipo de Trabajo", ingresos.employmentType());
			row(table, "Teléfono de Trabajo", ingresos.workPhone());
			row(table, "Ingreso Mensual Propio", ingresos.monthlyIncome());
			row(table, "Nombre de la Empresa", ingresos.companyName());
			row(table, "Puesto", ingresos.jobTitle());
			row(table, "Hora de Inicio", ingresos.workStartTime());
			row(table, "Hora de Fin", ingresos.workEndTime());
		}
		return table;
	}

	private static PdfPTable seleccionCarrera(FichaData ficha) {
		FichaData.SeleccionCarrera seleccion = ficha.seleccionCarrera();
		PdfPTable table = sectionTable();
		row(table, "Modalidad", seleccion.modalityName());
		row(table, "Carrera", ficha.programName());
		row(table, "Medio de Difusión", seleccion.outreachChannelName());
		row(table, "¿Primera Opción?", seleccion.isFirstChoice() ? "Sí, es mi primera opción" : "No, es mi segunda opción");
		return table;
	}

	private static PdfPTable antecedentesEscolares(FichaData ficha) {
		FichaData.AntecedentesEscolares antecedentes = ficha.antecedentesEscolares();
		PdfPTable table = sectionTable();
		row(table, "Preparatoria de Procedencia", antecedentes.schoolName());
		row(table, "Tipo de Bachillerato", antecedentes.schoolTypeName());
		row(table, "¿Bachillerato en México?", antecedentes.studiedInMexico());
		if (antecedentes.studiedInMexico()) {
			row(table, "Estado de la Preparatoria", antecedentes.schoolStateName());
			row(table, "Municipio de la Preparatoria", antecedentes.schoolMunicipalityName());
		} else {
			row(table, "País de la Preparatoria", antecedentes.foreignCountry());
			row(table, "Estado de la Preparatoria", antecedentes.schoolStateName());
			row(table, "Ciudad de la Preparatoria", antecedentes.schoolCity());
		}
		row(table, "Promedio", antecedentes.gpa() == null ? null : antecedentes.gpa().toPlainString());
		row(table, "Clave de Centro de Trabajo (CCT)", antecedentes.cct());
		return table;
	}

	private static PdfPTable sectionTable() {
		PdfPTable table = new PdfPTable(2);
		table.setWidthPercentage(100);
		table.setWidths(new float[] { 0.36f, 0.64f });
		return table;
	}

	private static void pagoRow(PdfPTable table, String label, Object value) {
		row(table, label, value, PAYMENT_BG);
	}

	private static void row(PdfPTable table, String label, Object value) {
		row(table, label, value, null);
	}

	private static void row(PdfPTable table, String label, Object value, RGBColor background) {
		Paragraph labelCellText = new Paragraph(label, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, LABEL_GRAY));
		Paragraph valueCellText = new Paragraph(text(value), FontFactory.getFont(FontFactory.HELVETICA, 10, VALUE_DARK));
		PdfPCell labelCell = new PdfPCell(labelCellText);
		PdfPCell valueCell = new PdfPCell(valueCellText);
		labelCell.setBorder(0);
		valueCell.setBorder(0);
		labelCell.setPadding(3);
		valueCell.setPadding(3);
		if (background != null) {
			labelCell.setBackgroundColor(background);
			valueCell.setBackgroundColor(background);
		}
		table.addCell(labelCell);
		table.addCell(valueCell);
	}

	private static String fullName(FichaData ficha) {
		return (ficha.firstName() == null ? "" : ficha.firstName()) + " "
				+ (ficha.lastName1() == null ? "" : ficha.lastName1()) + " "
				+ (ficha.lastName2() == null ? "" : ficha.lastName2());
	}

	private static String text(Object value) {
		if (value == null) {
			return "-";
		}
		if (value instanceof LocalDate date) {
			return date.format(DATE_FORMATTER);
		}
		if (value instanceof Instant instant) {
			return instant.atZone(ZoneId.systemDefault()).format(DATE_FORMATTER);
		}
		if (value instanceof LocalTime time) {
			return time.format(TIME_FORMATTER);
		}
		if (value instanceof BigDecimal amount) {
			return "$" + amount.toPlainString() + " MXN";
		}
		if (value instanceof Boolean yesNo) {
			return yesNo ? "Sí" : "No";
		}
		if (value instanceof Gender gender) {
			return sexLabel(gender);
		}
		if (value instanceof MaritalStatus maritalStatus) {
			return maritalStatusLabel(maritalStatus);
		}
		if (value instanceof EmploymentType employmentType) {
			return employmentTypeLabel(employmentType);
		}
		if (value instanceof String string) {
			return string.isEmpty() ? "-" : string;
		}
		return String.valueOf(value);
	}

	private static String sexLabel(Gender gender) {
		return switch (gender) {
			case M -> "Masculino";
			case F -> "Femenino";
			case NB -> "No binario";
		};
	}

	private static String maritalStatusLabel(MaritalStatus maritalStatus) {
		return switch (maritalStatus) {
			case SOLTERO -> "Soltero";
			case CASADO -> "Casado";
			case UNION_LIBRE -> "Unión libre";
			case DIVORCIADO -> "Divorciado";
			case VIUDO -> "Viudo";
			case OTRO -> "Otro";
		};
	}

	private static String employmentTypeLabel(EmploymentType employmentType) {
		return switch (employmentType) {
			case PERMANENT -> "Permanente";
			case TEMPORARY -> "Temporal";
		};
	}
}