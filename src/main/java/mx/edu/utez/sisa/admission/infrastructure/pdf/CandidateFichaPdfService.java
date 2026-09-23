package mx.edu.utez.sisa.admission.infrastructure.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import mx.edu.utez.sisa.admission.domain.port.in.GetCandidateFichaUseCase.FichaData;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Renders the admission ficha (screen 13) as a downloadable PDF via OpenPDF
 * (LGPL — the approved PDF library for the SISA repo), served by
 * {@code GET /candidates/{id}/ficha.pdf}. Pure presentation: layouts the
 * {@link FichaData} produced by {@code GetCandidateFichaUseCase}; no writes,
 * no state, deliberately a stateless singleton bean.
 *
 * <p>The layout is a single labeled table (folio, applicant, program, amount,
 * reference, deadline, receipt when paid) — matching the ficha's on-screen
 * content so the download and the portal render the same data.
 */
@Component
public class CandidateFichaPdfService {

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	public byte[] render(FichaData ficha) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Document document = new Document();
		PdfWriter.getInstance(document, out);
		document.open();
		document.add(title());
		document.add(rowsTable(ficha));
		document.close();
		return out.toByteArray();
	}

	private static Paragraph title() {
		Paragraph title = new Paragraph("FICHA DE ADMISIÓN", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16));
		title.setAlignment(Element.ALIGN_CENTER);
		return title;
	}

	private static PdfPTable rowsTable(FichaData ficha) {
		PdfPTable table = new PdfPTable(2);
		table.setWidthPercentage(100);
		row(table, "Folio", ficha.folio());
		row(table, "Nombre", fullName(ficha));
		row(table, "CURP", ficha.curp());
		row(table, "Programa", ficha.programName() == null ? "-" : ficha.programName());
		row(table, "Monto a pagar", "$" + amount(ficha.amount()) + " MXN");
		row(table, "Referencia de pago", ficha.referenceNumber());
		row(table, "Fecha límite de pago", date(ficha.deadline()));
		row(table, "Estado de pago", ficha.paymentStatus().name());
		if (ficha.receiptNumber() != null) {
			row(table, "Recibo", ficha.receiptNumber());
			row(table, "Fecha de pago", datetime(ficha.paidAt()));
		}
		return table;
	}

	private static void row(PdfPTable table, String label, String value) {
		Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
		Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
		Paragraph labelCell = new Paragraph(label, labelFont);
		Paragraph valueCell = new Paragraph(value, valueFont);
		table.addCell(labelCell);
		table.addCell(valueCell);
	}

	private static String fullName(FichaData ficha) {
		return (ficha.firstName() == null ? "" : ficha.firstName()) + " "
				+ (ficha.lastName1() == null ? "" : ficha.lastName1()) + " "
				+ (ficha.lastName2() == null ? "" : ficha.lastName2());
	}

	private static String amount(BigDecimal amount) {
		return amount == null ? "0.00" : amount.toPlainString();
	}

	private static String date(LocalDate date) {
		return date == null ? "-" : date.format(DATE_FORMATTER);
	}

	private static String datetime(Instant instant) {
		return instant == null ? "-" : instant.atZone(ZoneId.systemDefault()).format(DATE_FORMATTER);
	}
}