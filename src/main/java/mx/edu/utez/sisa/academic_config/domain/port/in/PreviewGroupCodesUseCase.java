package mx.edu.utez.sisa.academic_config.domain.port.in;

import java.util.List;
import java.util.UUID;

/**
 * Read-only preview of the codes a bulk creation <em>would</em> use —
 * {@code GET /groups/next-codes}.
 *
 * <p>
 * The Fase 8 plan asks for "detectar la última letra existente y
 * previsualizar las siguientes" before executing. Computing that on the client
 * would mean shipping every existing code of the generation down and
 * reimplementing {@code GroupCodeSequence}'s gap-filling rule in TypeScript —
 * two implementations of the same rule that drift. Asking the server instead
 * costs one GET and keeps a single source of truth.
 *
 * <p>
 * Preview and execute share the allocator, so what the user is shown is what
 * they get — unless another transaction takes the letters in between, which the
 * create's own duplicate check then turns into a 409.
 */
public interface PreviewGroupCodesUseCase {

	/**
	 * @param generationId required — existing generation to look at
	 * @param planLevelId  required — level of that generation's plan, fixes the
	 *                     numeric prefix
	 * @param quantity     required — how many codes to preview, 1..26 (A–Z)
	 */
	record PreviewGroupCodesQuery(UUID generationId, UUID planLevelId, int quantity) {
	}

	/**
	 * @param levelPrefix the numeric prefix, e.g. {@code "3"}
	 * @param codes       the free codes in allocation order, e.g.
	 *                    {@code ["3C", "3D"]}
	 */
	record PreviewGroupCodesResult(String levelPrefix, List<String> codes) {
	}

	PreviewGroupCodesResult previewGroupCodes(PreviewGroupCodesQuery query);
}