package mx.edu.utez.sisa.admission.infrastructure.notification;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CandidateFichaMailService}.
 *
 * <p>Two things are under test, and they pull in opposite directions:
 * <ul>
 * <li>the mail is <b>best-effort</b> — an SMTP failure is logged, never thrown,
 *     so a broken mail server cannot fail the applicant's payment confirmation
 *     HTTP response;</li>
 * <li>the mail is <b>a real receipt</b> — the RFC-correct MIME tree with a plain
 *     text alternative, the logo inlined by CID, and no unescaped or
 *     line-forgeable free text from the applicant.</li>
 * </ul>
 *
 * <p>All of them use {@code fail-fast=true} so delivery runs synchronously; with
 * the default async dispatch the {@code send} races the {@code verify} and the
 * assertions would be flaky.
 *
 * <p>The captured message is {@code saveChanges()}-ed before inspection because
 * the real {@code JavaMailSenderImpl.send} does exactly that, and a mocked
 * {@code send} does not — without it the multipart tree is never assembled.
 *
 * <p>There is no payment-instructions email test anymore: the ficha is paid
 * online only, so the "Enviar instrucciones a mi correo" button and
 * {@code POST /candidates/{id}/send-instructions} were removed.
 */
@ExtendWith(MockitoExtension.class)
class CandidateFichaMailServiceTest {

	private static final String TO = "aspirante@example.mx";

	private static final String FOLIO = "ADM-2026-000001";

	private static final Instant PAID_AT = Instant.parse("2026-09-26T18:59:32Z");

	private static final String RECEIPT = "REC-2026-000001";

	@Mock
	private JavaMailSender mailSender;

	private CandidateFichaMailService service() {
		when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
		return new CandidateFichaMailService(mailSender, true);
	}

	private void send(CandidateFichaMailService service, String name) {
		service.sendPaymentConfirmation(TO, name, FOLIO, "Ing. en Tecnologías de la Información",
				new BigDecimal("1578.00"), "REF-2026-000001", PAID_AT, RECEIPT);
	}

	private MimeMessage captured() {
		ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
		verify(mailSender).send(captor.capture());
		MimeMessage message = captor.getValue();
		try {
			message.saveChanges();
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
		return message;
	}

	// --- MIME tree inspection -------------------------------------------------

	/** One leaf of the MIME tree, flattened. */
	private record Node(String contentType, String contentId, Object content) {
	}

	/**
	 * Depth-first walk of every leaf part. Takes {@link Part} rather than
	 * {@code MimeMessage} because JavaMail hands back plain {@code BodyPart}s for
	 * the nested levels, and both implement it.
	 */
	private List<Node> leaves(Part part) throws Exception {
		List<Node> out = new ArrayList<>();
		Object content = part.getContent();
		if (content instanceof MimeMultipart multipart) {
			for (int i = 0; i < multipart.getCount(); i++) {
				out.addAll(leaves(multipart.getBodyPart(i)));
			}
		} else {
			String[] contentId = part.getHeader("Content-ID");
			out.add(new Node(part.getContentType(),
					contentId == null || contentId.length == 0 ? null : contentId[0], content));
		}
		return out;
	}

	private List<Node> leaves(MimeMessage message) {
		try {
			return leaves((Part) message);
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** Every textual body in the message. */
	private List<String> textParts(MimeMessage message) {
		return leaves(message).stream()
				.filter(node -> node.content() instanceof String)
				.map(node -> (String) node.content())
				.toList();
	}

	/** Content types of every leaf, e.g. "text/plain; charset=UTF-8". */
	private List<String> contentTypes(MimeMessage message) {
		return leaves(message).stream().map(Node::contentType).toList();
	}

	/** {@code MimeMessage.getContentType} throws, which is noise inside a test. */
	private String contentType(MimeMessage message) {
		try {
			return message.getContentType();
		} catch (MessagingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private Address[] recipients(MimeMessage message) {
		try {
			return message.getAllRecipients();
		} catch (MessagingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private String subject(MimeMessage message) {
		try {
			return message.getSubject();
		} catch (MessagingException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** The MIME structure as a readable tree, for assertion failure messages. */
	private String shape(MimeMessage message) {
		StringBuilder out = new StringBuilder(contentType(message).split(";")[0]);
		try {
			appendShape(message, out, 1);
		} catch (Exception ex) {
			out.append(" (shape unavailable: ").append(ex).append(')');
		}
		return out.toString();
	}

	private void appendShape(Part part, StringBuilder out, int depth) throws Exception {
		Object content = part.getContent();
		if (content instanceof MimeMultipart multipart) {
			out.append('\n').append("  ".repeat(depth)).append(part.getContentType().split(";")[0]);
			for (int i = 0; i < multipart.getCount(); i++) {
				appendShape(multipart.getBodyPart(i), out, depth + 1);
			}
		} else {
			out.append('\n').append("  ".repeat(depth)).append(part.getContentType());
		}
	}

	// --- best-effort contract -------------------------------------------------

	@Test
	void sendPaymentConfirmationDeliversWhenSmtpSucceeds() {
		CandidateFichaMailService service = service();

		send(service, "Ana Torres");

		verify(mailSender).send(any(MimeMessage.class));
	}

	@Test
	void sendPaymentConfirmationSwallowsFailureInBestEffortMode() {
		CandidateFichaMailService service = service();
		doThrow(new MailSendException("down")).when(mailSender).send(any(MimeMessage.class));

		assertThatCode(() -> send(service, "Ana")).doesNotThrowAnyException();

		verify(mailSender).send(any(MimeMessage.class));
	}

	@Test
	void addressesTheApplicantAndNamesTheFolioInTheSubject() {
		send(service(), "Ana Torres");

		MimeMessage message = captured();
		assertThat(recipients(message)).hasSize(1);
		assertThat(recipients(message)[0].toString()).isEqualTo(TO);
		// The folio lets an applicant find the mail later; asserting the exact
		// string also proves the accents survived RFC 2047 encoding.
		assertThat(subject(message)).isEqualTo("Confirmación de pago de tu ficha — " + FOLIO);
	}

	// --- MIME structure -------------------------------------------------------

	/**
	 * The reason the tree is assembled by hand. {@code MimeMessageHelper} would
	 * have kept only the {@code text/html} here and dropped the alternative
	 * silently, so both bodies are asserted separately.
	 */
	@Test
	void keepsBothThePlainTextAndTheHtmlBody() {
		send(service(), "Ana Torres");

		MimeMessage message = captured();
		List<String> types = contentTypes(message);
		assertThat(types).anyMatch(t -> t.startsWith("text/plain"));
		assertThat(types).anyMatch(t -> t.startsWith("text/html"));

		assertThat(textParts(message))
				.as("plain-text receipt, structure was: %s", shape(message))
				.anySatisfy(part -> assertThat(part)
						.contains(FOLIO)
						.contains(RECEIPT)
						.doesNotContain("<html"));
	}

	@Test
	void nestsTheAlternativeInsideRelatedWithTheLogo() {
		send(service(), "Ana Torres");

		MimeMessage message = captured();
		// related( alternative( plain, html ), logo ) — the RFC-correct shape for one
		// body plus an inline resource.
		assertThat(contentType(message)).as("root, was: %s", shape(message))
				.startsWith("multipart/related");

		assertThat(shape(message)).contains("multipart/alternative")
				.contains("multipart/related")
				.contains("text/plain")
				.contains("text/html")
				.contains("image/png");
	}

	@Test
	void plainTextComesBeforeHtml() throws Exception {
		send(service(), "Ana Torres");

		MimeMessage message = captured();
		MimeMultipart alternative = (MimeMultipart) message.getContent();
		// Descend past related -> alternative.
		MimeMultipart inner = (MimeMultipart) alternative.getBodyPart(0).getContent();
		String first = inner.getBodyPart(0).getContentType();
		assertThat(first).as("clients that render both must be able to pick richest, was: %s", shape(message))
				.startsWith("text/plain");
	}

	@Test
	void inlinesTheLogoUnderTheContentIdTheHtmlReferences() {
		send(service(), "Ana Torres");

		MimeMessage message = captured();
		List<Node> nodes = leaves(message);
		// The HTML says "cid:utez-logo"; the part has to answer to exactly that.
		assertThat(nodes).anySatisfy(node -> assertThat(node.contentId()).isEqualTo("<utez-logo>"));
		assertThat(nodes).anySatisfy(node -> assertThat(node.contentType()).startsWith("image/png"));

		assertThat(textParts(message))
				.anySatisfy(part -> assertThat(part).contains("<html").contains("cid:utez-logo"));
	}

	// --- content of the receipt -----------------------------------------------

	@Test
	void stampsTheGatewayConfirmedPaymentDateNotTheSendDate() {
		send(service(), "Ana Torres");

		assertThat(textParts(captured()))
				.as("paidAt came from the gateway result, not LocalDate.now()")
				.anySatisfy(part -> assertThat(part).contains("26/09/2026"));
	}

	@Test
	void includesEveryValueAnApplicantNeedsToReconcileThePayment() {
		send(service(), "Ana Torres");

		assertThat(textParts(captured())).anySatisfy(part -> assertThat(part)
				.contains(FOLIO)
				.contains(RECEIPT)
				.contains("REF-2026-000001")
				.contains("1578.00")
				.contains("Ing. en Tecnologías de la Información"));
	}

	@Test
	void leavesNoUnresolvedPlaceholder() {
		send(service(), "Ana Torres");

		assertThat(textParts(captured()))
				.as("structure was: %s", shape(captured()))
				.allSatisfy(part -> assertThat(part).doesNotContain("{{"));
	}

	// --- free text from the applicant ----------------------------------------

	@Test
	void escapesFreeTextInTheHtmlVariant() {
		send(service(), "Ana <script>alert(1)</script> & \"Cruz\"");

		List<String> html = textParts(captured()).stream().filter(part -> part.contains("<html")).toList();
		assertThat(html).isNotEmpty();
		assertThat(html).allSatisfy(part -> assertThat(part)
				.doesNotContain("<script>")
				.contains("&lt;script&gt;")
				.contains("&amp;"));
	}

	/**
	 * Collapsing whitespace is the property that matters: a newline in a name must
	 * not be able to start a line that reads like a genuine receipt field. The
	 * injected words still appear inline ("Ana Reference: ..."), which is
	 * harmless, so the assertion is about line starts, not about the substring
	 * disappearing.
	 */
	@Test
	void keepsFreeTextOnOneLineSoItCannotForgeReceiptFields() {
		send(service(), "Ana\nReference: REF-FALSA");

		assertThat(textParts(captured()))
				.as("structure was: %s", shape(captured()))
				.allSatisfy(part -> assertThat(part).doesNotContainPattern("\\R\\s*Reference: REF-FALSA"));
	}

	// --- packaging guards -----------------------------------------------------

	@Test
	void serializesToAWellFormedMimeMessage() throws Exception {
		send(service(), "Ana Torres");

		ByteArrayOutputStream raw = new ByteArrayOutputStream();
		captured().writeTo(raw);
		assertThat(raw.toString("UTF-8")).contains(FOLIO);
	}

	/**
	 * Guards the assumption the whole CID path rests on: the asset has to be where
	 * the service looks, or every mail goes out with a silently missing logo.
	 */
	@Test
	void logoAssetIsOnTheClasspathWhereTheServiceLooksForIt() {
		assertThat(new ClassPathResource("assets/utez-logo.png").exists()).isTrue();
	}

	/** Both templates must be present or the receipt silently degrades. */
	@Test
	void bothTemplatesAreOnTheClasspath() {
		assertThat(new ClassPathResource("templates/ficha-pago-confirmacion.html").exists()).isTrue();
		assertThat(new ClassPathResource("templates/ficha-pago-confirmacion.txt").exists()).isTrue();
	}
}
