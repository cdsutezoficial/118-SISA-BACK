package mx.edu.utez.sisa.admission.domain.service;

import mx.edu.utez.sisa.admission.domain.port.in.GetFichaAmountUseCase;
import mx.edu.utez.sisa.admission.domain.port.out.ProgramAdmissionConfigQueryPort;
import mx.edu.utez.sisa.admission.shared.exception.ProgramAdmissionConfigNotFoundException;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Prices the ficha for a chosen {@code ProgramAdmissionConfig} so the
 * registration wizard can display the real amount before submitting (Fase 11).
 *
 * <p>Resolves the config to its program, then delegates to
 * {@link FichaAmountResolver} — the same component the registration command
 * uses — so the previewed amount cannot drift from the charged one. The quote
 * date is injected by the composition root, keeping this class framework-free.
 *
 * <p>Only existence is enforced here ({@code 404} when the config id is
 * unknown): an {@code OPEN} window is the registration command's rule to
 * enforce, and a window that is not sellable simply has no active enrollment
 * concept to quote.
 */
public class GetFichaAmountUseCaseImpl implements GetFichaAmountUseCase {

	private final ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort;

	private final FichaAmountResolver fichaAmountResolver;

	private final LocalDate quoteDate;

	public GetFichaAmountUseCaseImpl(ProgramAdmissionConfigQueryPort programAdmissionConfigQueryPort,
			FichaAmountResolver fichaAmountResolver, LocalDate quoteDate) {
		this.programAdmissionConfigQueryPort = programAdmissionConfigQueryPort;
		this.fichaAmountResolver = fichaAmountResolver;
		this.quoteDate = quoteDate;
	}

	@Override
	public FichaAmountQuote quote(UUID admissionConfigId) {
		ProgramAdmissionConfigQueryPort.AdmissionConfigInfo config = programAdmissionConfigQueryPort
				.findById(admissionConfigId)
				.orElseThrow(() -> new ProgramAdmissionConfigNotFoundException(
						"No existe la configuración de admisión: " + admissionConfigId));
		FichaAmountResolver.FichaAmount fichaAmount = fichaAmountResolver.resolve(config.programId(), quoteDate);
		return new FichaAmountQuote(fichaAmount.amount(), fichaAmount.conceptName(), config.programName());
	}
}
