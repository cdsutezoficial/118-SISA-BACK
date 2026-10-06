package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when {@code CreateGroupUseCase}/{@code UpdateGroupUseCase} receives a
 * {@code code} whose numeric prefix is not the number of the chosen plan level
 * — "Nivel 3" with {@code 5A}. Maps to HTTP 400 in the web layer's
 * {@code GlobalExceptionHandler}, with the stable code
 * {@code GROUP_CODE_LEVEL_MISMATCH}.
 *
 * <p>Why this is a business rule and not just a field pattern: the DTO's
 * {@code ^\p{N}+\p{L}$} only says "digits + one letter", it cannot know which
 * level the user picked, and without this check nothing stops two groups of
 * different levels from sharing the same letter-space inconsistently — the
 * bulk path ({@code GroupCodeSequence}) always builds codes from the level's
 * own number, so accepting {@code 5A} under level 3 would make an
 * individually-created group unreachable by the preview's gap detection.
 *
 * <p>Distinct from {@link DuplicateGroupCodeException} (409): that one means
 * the code is already taken within the generation, this one means the code
 * never described that level in the first place.
 */
public class GroupCodeLevelMismatchException extends RuntimeException {

	public GroupCodeLevelMismatchException(String message) {
		super(message);
	}
}
