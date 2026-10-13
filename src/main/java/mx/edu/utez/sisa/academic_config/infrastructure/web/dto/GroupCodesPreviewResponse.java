package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import java.util.List;

/**
 * Response body for {@code GET /groups/next-codes}.
 *
 * <p>
 * The {@code quantity} bound (1..26) is enforced on the controller's
 * {@code @RequestParam} rather than here, since this is a response record with
 * nothing to validate.
 *
 * @param levelPrefix the numeric prefix the codes share, so the UI can render
 *                    "3A, 3B, 3C" without re-parsing each code
 * @param codes       the free codes in allocation order
 */
public record GroupCodesPreviewResponse(String levelPrefix, List<String> codes) {
}