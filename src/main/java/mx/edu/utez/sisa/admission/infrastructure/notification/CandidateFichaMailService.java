package mx.edu.utez.sisa.admission.infrastructure.notification;

import jakarta.activation.DataHandler;
import jakarta.mail.BodyPart;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Sends the applicant's admission-ficha confirmation email, triggered once
 * {@code ConfirmAdmissionPaymentUseCase} marks the ticket {@code PAID}.
 *
 * <p>There is deliberately no payment-INSTRUCTIONS email: the ficha is paid
 * online only (EVO Hosted Checkout), so the portal no longer offers an
 * "Enviar instrucciones a mi correo" button and
 * {@code POST /candidates/{id}/send-instructions} is gone. The amount,
 * reference and receipt the applicant needs are shown on the ficha screen
 * itself and re-derivable at any time from the folio.
 *
 * <h2>Best-effort</h2>
 * <p>A failed SMTP send is logged with the FULL stack trace and swallowed — it
 * must never fail the HTTP response nor delay it. To keep the HTTP answer fast,
 * delivery fires on a separate thread via {@link CompletableFuture#runAsync}; no
 * {@code @EnableAsync} is needed for that. When {@code sisa.mail.fail-fast=true}
 * (dev-only flag) delivery runs synchronously, so a broken SMTP setup surfaces
 * promptly in development.
 *
 * <h2>Why a template and not inline String.format</h2>
 * <p>The body comes from {@code resources/templates} with {@code {{placeholders}}}
 * filled in. Two reasons, not aesthetics:
 * <ul>
 * <li>A text-only receipt lands in spam far more often, and Gmail clips long
 *     {@code text/plain} bodies into a "view entire message" link.</li>
 * <li>Presentation had to be designed and reviewed as a template; rebuilding it
 *     with {@code String.format} in Java puts it in a place nobody looks before
 *     sending to every applicant.</li>
 * </ul>
 *
 * <h2>Why the MIME tree is built by hand</h2>
 * <p>{@link MimeMessageHelper} cannot express this message. It has one "main
 * part", and every {@code setText} call overwrites it — so a second
 * {@code setText(html, true)} silently DISCARDS the plain text instead of adding
 * an alternative (verified against spring-context-support 6.2.19: the resulting
 * tree contained only {@code text/html}). {@code addInline} in between is worse
 * still: it moves the main part aside and leaves a single body. Neither mistake
 * fails loudly; the mail just quietly loses its plain-text alternative, which is
 * exactly what costs deliverability.
 * <p>So the tree is assembled explicitly, which is the RFC-correct shape for one
 * body plus an inline resource:
 * <pre>
 * multipart/related
 *  └─ multipart/alternative
 *      ├─ text/plain          (first: clients that can render both pick richest)
 *      └─ text/html           (references cid:utez-logo)
 *  └─ image/png              (Content-ID: &lt;utez-logo&gt;)
 * </pre>
 * {@code MimeMessageHelper} is still used for {@code To}/{@code Subject} so
 * address validation and RFC 2047 encoding of the accented subject stay in one
 * place. The {@code From} is left unset on purpose: {@code JavaMailSenderImpl}
 * fills it from {@code spring.mail.username}/{@code defaultFrom}.
 *
 * <p>The logo is inlined by <b>CID</b>, not linked by URL: it needs no public host,
 * and it does not tell the recipient's mail client to phone home to a third
 * party when the message renders.
 *
 * <p>If a template is missing from the classpath the mail still goes out with a
 * minimal inline body. A missing resource must never cost an applicant their
 * payment receipt, so the template is an enhancement, not a dependency.
 *
 * <p>SMTP configuration lives in {@code .env} ({@code spring.mail.*}),
 * consumed by {@code application.properties}' {@code spring.config.import}.
 */
@Component
public class CandidateFichaMailService {

	private static final Logger log = LoggerFactory.getLogger(CandidateFichaMailService.class);

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	/** Content-ID the HTML template references the logo by. */
	private static final String LOGO_CID = "utez-logo";

	/** Placeholder key for the logo markup, as opposed to a data value. */
	private static final String LOGO_KEY = "logo";

	private static final String LOGO_CLASSPATH = "assets/utez-logo.png";

	private static final String HTML_TEMPLATE = "templates/ficha-pago-confirmacion.html";

	private static final String TEXT_TEMPLATE = "templates/ficha-pago-confirmacion.txt";

	private static final String NOTICE_HTML_TEMPLATE = "templates/ficha-aviso.html";

	private static final String NOTICE_TEXT_TEMPLATE = "templates/ficha-aviso.txt";

	private static final String GREEN = "#009574";

	private static final String AMBER = "#B45309";

	private static final String ROW_LABEL_STYLE = "padding:6px 20px; font-family:Arial,Helvetica,sans-serif; "
			+ "font-size:13px; line-height:20px; color:#6B7280; width:45%;";

	private static final String ROW_VALUE_STYLE = "padding:6px 20px; font-family:Arial,Helvetica,sans-serif; "
			+ "font-size:13px; line-height:20px; color:#333333;";

	private static final String UTF_8 = StandardCharsets.UTF_8.name();

	private final JavaMailSender mailSender;

	private final boolean failFast;

	public CandidateFichaMailService(JavaMailSender mailSender,
			@Value("${sisa.mail.fail-fast:false}") boolean failFast) {
		this.mailSender = mailSender;
		this.failFast = failFast;
	}

	/**
	 * Sends the confirmation that the ficha was paid (candidate now {@code PAID}):
	 * folio, amount, reference, receipt and payment date. Asynchronous unless
	 * {@code fail-fast} — best-effort, never fails the HTTP response.
	 *
	 * @param paidAt when the gateway-confirmed payment landed. Passed in rather
	 *               than read from the clock: a re-confirmation or a queued send
	 *               would otherwise print today's date on an older receipt.
	 */
	public void sendPaymentConfirmation(String to, String name, String folio, String program,
			BigDecimal amount, String reference, Instant paidAt, String receipt) {
		sendPaymentConfirmation(to, name, folio, program, amount, reference, paidAt, receipt, null);
	}

	/**
	 * The receipt, divided by charge when the charge had received money before (E6):
	 * the career's total, what was paid earlier (with its receipt and date) and what was
	 * paid now. A career-change difference also gets its own subject and wording.
	 *
	 * @param breakdown {@code null} for a payment that settled its whole charge at once
	 */
	public void sendPaymentConfirmation(String to, String name, String folio, String program,
			BigDecimal amount, String reference, Instant paidAt, String receipt, Breakdown breakdown) {
		Map<String, String> values = values(name, folio, program, amount, reference, paidAt, receipt);
		boolean difference = breakdown != null && breakdown.programChange();
		values.put("titulo", difference ? "¡Pago de la diferencia confirmado!" : "¡Pago de ficha confirmado!");
		values.put("subtitulo", difference
				? "Pagaste la diferencia por tu cambio de carrera. Tu ficha queda pagada en la carrera nueva."
				: "Tu registro de Aspirante quedó completado. Guarda esta pantalla o descarga tu comprobante.");
		String amountLabel = breakdown != null && breakdown.previouslyPaid() != null
				&& breakdown.previouslyPaid().signum() > 0 ? "Pagado en este momento" : "Monto pagado";
		Map<String, String[]> raw = new LinkedHashMap<>();
		raw.put("montoEtiqueta", new String[] { amountLabel, padLabel(amountLabel) });
		raw.put("desglose", breakdownRows(breakdown));
		String rendered = render(TEXT_TEMPLATE, values, raw, false);
		// Plantilla ausente: el recibo sale igual, con el cuerpo mínimo. Perder el
		// formato es aceptable; perder el comprobante, no.
		String text = rendered != null ? rendered
				: minimalTextBody(name, folio, program, amount, reference, paidAt, receipt);
		// El HTML es opcional: si falta su plantilla se manda solo texto, que es
		// justo lo que un cliente de correo sin soporte HTML necesita.
		String html = render(HTML_TEMPLATE, values, raw, true);
		String subject = difference ? "Pago de la diferencia por cambio de carrera — " + folio
				: "Confirmación de pago de tu ficha — " + folio;
		dispatch(() -> doSend(to, subject, html, text));
	}

	/**
	 * E6: the lines of a divided receipt. {@code previousReceipt} and
	 * {@code previousPaidAt} identify the earlier charge the money came from, when a
	 * career change carried it.
	 */
	public record Breakdown(BigDecimal total, BigDecimal previouslyPaid, String previousReceipt,
			Instant previousPaidAt, boolean programChange) {
	}

	/**
	 * F3: a career change left a difference to pay. Says how much, by when, and where,
	 * so the applicant learns it without having to open the portal.
	 *
	 * @param portalUrl the portal's payment page, or {@code null} when not configured
	 */
	public void sendProgramChangeDifference(String to, String name, String folio, String program,
			BigDecimal newAmount, BigDecimal alreadyPaid, BigDecimal toPay, Instant dueAt, String portalUrl) {
		Map<String, String> values = noticeValues(name, folio, program, AMBER, "Tienes una diferencia por pagar",
				"Se cambió tu carrera y el costo de la nueva es mayor a lo que ya pagaste.",
				"paga la diferencia desde el portal con tu folio antes de la fecha límite. "
						+ "Mientras no la pagues, tu ficha queda pendiente.");
		List<String[]> rows = List.of(row("Total de la carrera", money(newAmount)),
				row("Ya pagado", money(alreadyPaid)), row("Por pagar", money(toPay)),
				row("Fecha límite", formatInstant(dueAt)));
		sendNotice(to, "Diferencia por pagar por cambio de carrera — " + folio, values, rows, portalUrl);
	}

	/**
	 * F3: a career change left money over, kept as the person's saldo a favor (it does
	 * not expire, D7).
	 */
	public void sendCreditLeft(String to, String name, String folio, String program, BigDecimal credit) {
		Map<String, String> values = noticeValues(name, folio, program, GREEN, "Tienes saldo a favor",
				"Se cambió tu carrera y la nueva cuesta menos de lo que ya pagaste.",
				"el monto quedó como saldo a favor a tu nombre y no caduca. "
						+ "Puedes usarlo en tus siguientes pagos.");
		sendNotice(to, "Saldo a favor por cambio de carrera — " + folio, values,
				List.<String[]>of(row("Saldo a favor", money(credit))), null);
	}

	private void sendNotice(String to, String subject, Map<String, String> values, List<String[]> rows,
			String portalUrl) {
		Map<String, String[]> raw = new LinkedHashMap<>();
		raw.put("filas", rows(rows));
		raw.put("enlace", link(portalUrl));
		String text = render(NOTICE_TEXT_TEMPLATE, values, raw, false);
		if (text == null) {
			StringBuilder fallback = new StringBuilder(values.get("titulo")).append("\n\n");
			rows.forEach(r -> fallback.append(r[0]).append(": ").append(r[1]).append('\n'));
			text = fallback.toString();
		}
		String html = render(NOTICE_HTML_TEMPLATE, values, raw, true);
		String body = text;
		dispatch(() -> doSend(to, subject, html, body));
	}

	private static Map<String, String> noticeValues(String name, String folio, String program, String color,
			String title, String subtitle, String message) {
		Map<String, String> values = values(name, folio, program, null, null, null, null);
		values.put("titulo", title);
		values.put("subtitulo", subtitle);
		values.put("mensaje", message);
		values.put("color", color);
		return values;
	}

	private static String[] row(String label, String value) {
		return new String[] { label, value };
	}

	/** {html, text} for a run of label/value rows; values are ours and escaped. */
	private static String[] rows(List<String[]> rows) {
		StringBuilder html = new StringBuilder();
		StringBuilder text = new StringBuilder();
		for (String[] r : rows) {
			html.append("<tr><td style=\"").append(ROW_LABEL_STYLE).append("\">").append(escapeHtml(r[0]))
					.append("</td><td style=\"").append(ROW_VALUE_STYLE).append("\">")
					.append(escapeHtml(collapseWhitespace(r[1]))).append("</td></tr>");
			text.append("  ").append(padLabel(r[0])).append(' ').append(collapseWhitespace(r[1])).append('\n');
		}
		return new String[] { html.toString(), text.toString() };
	}

	private static String[] breakdownRows(Breakdown breakdown) {
		if (breakdown == null || breakdown.previouslyPaid() == null || breakdown.previouslyPaid().signum() <= 0) {
			return new String[] { "", "" };
		}
		String earlier = breakdown.previousReceipt() == null ? money(breakdown.previouslyPaid())
				: money(breakdown.previouslyPaid()) + " (" + breakdown.previousReceipt() + ", "
						+ formatInstant(breakdown.previousPaidAt()) + ")";
		return rows(List.of(row(breakdown.programChange() ? "Total de la carrera" : "Total de la ficha",
				money(breakdown.total())), row("Pagado anteriormente", earlier)));
	}

	private static String[] link(String url) {
		if (url == null || url.isBlank()) {
			return new String[] { "", "" };
		}
		String safe = escapeHtml(url.trim());
		return new String[] { "<p style=\"margin:12px 0 0 0; font-family:Arial,Helvetica,sans-serif; font-size:13px;\">"
				+ "<a href=\"" + safe + "\" style=\"color:#009574; font-weight:bold;\">Ir al portal de pago</a></p>",
				"Portal de pago: " + url.trim() + "\n" };
	}

	private static String padLabel(String label) {
		StringBuilder padded = new StringBuilder(label).append(' ');
		while (padded.length() < 27) {
			padded.append('.');
		}
		return padded.toString();
	}

	private static String money(BigDecimal amount) {
		return "$" + formatAmount(amount) + " MXN";
	}

	private void dispatch(Runnable send) {
		if (failFast) {
			send.run();
			return;
		}
		CompletableFuture.runAsync(send);
	}

	private void doSend(String to, String subject, String htmlBody, String textBody) {
		try {
			MimeMessage message = mailSender.createMimeMessage();
			// Only the envelope here; the content is assembled below.
			MimeMessageHelper helper = new MimeMessageHelper(message, false, UTF_8);
			helper.setTo(to);
			helper.setSubject(subject);

			MimeMultipart alternative = new MimeMultipart("alternative");
			alternative.addBodyPart(plainPart(textBody));
			if (htmlBody != null) {
				alternative.addBodyPart(htmlPart(htmlBody));
			}

			byte[] logo = readLogo();
			if (logo == null) {
				// Without the image there is nothing for "related" to relate, and an
				// empty related part is worse than a flat alternative.
				message.setContent(alternative);
			} else {
				message.setContent(relatedWithLogo(alternative, logo));
			}
			mailSender.send(message);
		} catch (Exception ex) {
			// Full stack trace on purpose — a silent-getMessage() failure mode
			// hides the SMTP root cause.
			log.warn("No se pudo enviar el correo de ficha a {} (asunto '{}'): ", to, subject, ex);
		}
	}

	private static BodyPart plainPart(String text) throws Exception {
		MimeBodyPart part = new MimeBodyPart();
		part.setText(text, UTF_8);
		return part;
	}

	private static BodyPart htmlPart(String html) throws Exception {
		MimeBodyPart part = new MimeBodyPart();
		part.setContent(html, "text/html; charset=" + UTF_8);
		return part;
	}

	/** Wraps {@code alternative} and the logo into the RFC-correct related tree. */
	private static MimeMultipart relatedWithLogo(MimeMultipart alternative, byte[] logo) throws Exception {
		MimeMultipart related = new MimeMultipart("related");
		MimeBodyPart alternativePart = new MimeBodyPart();
		alternativePart.setContent(alternative);
		related.addBodyPart(alternativePart);

		MimeBodyPart logoPart = new MimeBodyPart();
		logoPart.setDataHandler(new DataHandler(new ByteArrayDataSource(logo, "image/png")));
		// Explicit, not derived from the DataSource name: the HTML says
		// "cid:utez-logo" and that string has to match exactly.
		logoPart.setHeader("Content-ID", "<" + LOGO_CID + ">");
		logoPart.setDisposition(BodyPart.INLINE);
		related.addBodyPart(logoPart);
		return related;
	}

	/** @return the logo bytes, or {@code null} if the asset is not on the classpath. */
	private static byte[] readLogo() {
		ClassPathResource resource = new ClassPathResource(LOGO_CLASSPATH);
		if (!resource.exists()) {
			log.warn("No se encontró el logo '{}' para el correo; se enviará sin él.", LOGO_CLASSPATH);
			return null;
		}
		try (InputStream in = resource.getInputStream()) {
			return in.readAllBytes();
		} catch (IOException ex) {
			log.warn("No se pudo leer el logo '{}' para el correo: ", LOGO_CLASSPATH, ex);
			return null;
		}
	}

	private static Map<String, String> values(String name, String folio, String program, BigDecimal amount,
			String reference, Instant paidAt, String receipt) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("nombre", blankToDash(name));
		values.put("folio", blankToDash(folio));
		values.put("carrera", blankToDash(program));
		values.put("monto", formatAmount(amount));
		values.put("referencia", blankToDash(reference));
		values.put("recibo", blankToDash(receipt));
		values.put("fechaPago", formatInstant(paidAt));
		values.put(LOGO_KEY, "<img src=\"cid:" + LOGO_CID
				+ "\" width=\"160\" alt=\"UTEZ — SISA\" style=\"display:block; width:160px; max-width:160px; height:auto; border:0;\" />");
		return values;
	}

	/**
	 * Fills {@code {{placeholders}}}. Returns {@code null} when the template is
	 * absent so the caller can fall back — see the class note on why.
	 *
	 * @param html {@code true} for the HTML variant, where substituted values are
	 *             escaped. A name is free text typed at registration and lands
	 *             inside a layout table, so an unescaped {@code <} would let a
	 *             registrant reshape the receipt.
	 */
	private static String render(String classpath, Map<String, String> values, Map<String, String[]> raw,
			boolean html) {
		String template = TemplateCache.load(classpath);
		if (template == null) {
			return null;
		}
		// Rows and links are built here from escaped values, so they go in as markup.
		for (Map.Entry<String, String[]> entry : raw.entrySet()) {
			template = template.replace("{{" + entry.getKey() + "}}", entry.getValue()[html ? 0 : 1]);
		}
		for (Map.Entry<String, String> entry : values.entrySet()) {
			String placeholder = "{{" + entry.getKey() + "}}";
			if (LOGO_KEY.equals(entry.getKey())) {
				// The logo markup is ours, not user input: escaping it would break it,
				// and an <img> means nothing in the text/plain variant.
				template = template.replace(placeholder, html ? entry.getValue() : "");
			} else {
				// Whitespace is collapsed in BOTH variants: in text/plain a newline
				// in a free-text name would let it forge extra receipt lines, and
				// collapsing it in both keeps the two bodies consistent.
				String value = collapseWhitespace(entry.getValue());
				template = template.replace(placeholder, html ? escapeHtml(value) : value);
			}
		}
		return template;
	}

	/**
	 * Plain-text fallback used only when the text template is missing from the
	 * classpath. It exists so a packaging mistake costs formatting, not the
	 * receipt itself.
	 */
	private static String minimalTextBody(String name, String folio, String program, BigDecimal amount,
			String reference, Instant paidAt, String receipt) {
		return """
				¡Pago de ficha confirmado!

				Tu registro de Aspirante quedó completado.

				Folio: %s
				Monto pagado: $%s MXN
				Referencia de pago: %s
				Fecha de pago: %s
				Recibo: %s
				Carrera: %s

				Hola %s, conserva tu folio para los siguientes pasos del proceso de admisión.
				Descarga tu comprobante en PDF desde el portal.

				Universidad Tecnológica de la Zona de UTEZ
				SISA — Sistema Integral de Servicios Académicos"""
				.formatted(collapseWhitespace(blankToDash(folio)), formatAmount(amount),
						collapseWhitespace(blankToDash(reference)), formatInstant(paidAt),
						collapseWhitespace(blankToDash(receipt)), collapseWhitespace(blankToDash(program)),
						collapseWhitespace(blankToDash(name)));
	}

	private static String formatAmount(BigDecimal amount) {
		return amount == null ? "0.00" : amount.toPlainString();
	}

	/**
	 * The payment instant rendered in the server's zone. Falls back to {@code "-"}
	 * rather than to today: a receipt with no date is honest, a receipt with the
	 * wrong date is a support ticket.
	 */
	private static String formatInstant(Instant paidAt) {
		return paidAt == null ? "-" : LocalDate.ofInstant(paidAt, ZoneId.systemDefault()).format(DATE_FORMATTER);
	}

	private static String blankToDash(String value) {
		return value == null || value.isBlank() ? "-" : value.trim();
	}

	private static String collapseWhitespace(String value) {
		return value == null ? "" : value.replaceAll("\\s+", " ").trim();
	}

	/** Escapes the five characters that can break out of HTML text or an attribute. */
	private static String escapeHtml(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
				.replace("'", "&#39;");
	}

	/**
	 * Templates are read once and kept, including the failure: caching only hits
	 * would retry the classpath scan and re-log on every single recipient.
	 * The sentinel is what makes a missing template a one-time warning.
	 */
	private static final class TemplateCache {

		/** Marks "looked, not there" so the miss is not retried per recipient. */
		private static final String MISSING = " missing";

		private static final Map<String, String> CACHE = new LinkedHashMap<>();

		private TemplateCache() {
		}

		static synchronized String load(String classpath) {
			String cached = CACHE.get(classpath);
			if (cached != null) {
				return MISSING.equals(cached) ? null : cached;
			}
			String loaded = read(classpath);
			CACHE.put(classpath, loaded == null ? MISSING : loaded);
			return loaded;
		}

		private static String read(String classpath) {
			ClassPathResource resource = new ClassPathResource(classpath);
			if (!resource.exists()) {
				log.warn("No se encontró la plantilla de correo '{}'; se usará el cuerpo mínimo.", classpath);
				return null;
			}
			try (InputStream in = resource.getInputStream()) {
				return new String(in.readAllBytes(), UTF_8);
			} catch (IOException ex) {
				log.warn("No se pudo leer la plantilla de correo '{}': ", classpath, ex);
				return null;
			}
		}
	}
}
