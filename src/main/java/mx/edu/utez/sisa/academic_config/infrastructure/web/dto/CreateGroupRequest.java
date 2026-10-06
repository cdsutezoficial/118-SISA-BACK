package mx.edu.utez.sisa.academic_config.infrastructure.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import mx.edu.utez.sisa.shared.model.Shift;

import java.util.UUID;

/**
 * Request body for {@code POST /groups}. {@code programId} is deliberately
 * absent — it is always resolved server-side from {@code generationId}'s
 * owning {@code Generation.programId}, even if a client sends one it is
 * ignored. {@code status} is also absent — every new group defaults to
 * {@code OPEN} (enforced by the {@code Group} constructor).
 * {@code maxCapacity} is a primitive {@code int} rather than boxed with
 * {@code @NotNull} — same convention as {@code CreateGenerationRequest.number}.
 *
 * <p>
 * {@code code} is the group's human identifier: level followed by letter, e.g.
 * {@code "3A"} — the format the domain doc
 * ({@code 02-config-academica.md}) specifies, decided with the user on
 * 2026-10-05 after the form's placeholder said {@code "Ej. A"} and the Fase 8
 * plan said "la letra del grupo". Hence digits-then-letter, which is why the
 * placeholder was corrected to {@code "Ej. 3A"} rather than the other way
 * round. The upper bound of 10 characters is not a business rule — it is only
 * there to stop a paste from becoming a {@code varchar} overflow; a 3-digit
 * level plus one letter is the longest code that means anything here.
 *
 * <p>
 * The regex is checked against the RAW value, before
 * {@code GroupTextNormalizer} uppercases it. That ordering is deliberate and
 * costs nothing: {@code \p{L}} accepts lowercase, so {@code "3a"} passes the
 * pattern and is normalized to {@code "3A"} afterwards. Uppercasing before
 * validation would have made the pattern untestable and the error messages
 * misleading, since the user would be told about a "3A" they never typed.
 */
public record CreateGroupRequest(@NotNull UUID generationId, @NotNull UUID periodId, @NotNull UUID planLevelId,
		@NotNull @Size(max = 10, message = "La clave del grupo no puede exceder 10 caracteres.")
		@Pattern(regexp = "^\\p{N}+\\p{L}$", message = "La clave del grupo debe ser el nivel seguido de una sola letra, por ejemplo 3A.") String code,
		@Min(value = 1, message = "La capacidad máxima debe ser mayor o igual a 1.") int maxCapacity, @NotNull Shift shift) {
}