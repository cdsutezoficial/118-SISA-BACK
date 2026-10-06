package mx.edu.utez.sisa.academic_config.shared.exception;

/**
 * Thrown when a bulk group creation asks for more codes than the A–Z range has
 * left free for that level — e.g. asking for 3 groups when only "3A" and "3B"
 * are unused. Maps to HTTP 409: the request is well-formed and the referenced
 * generation exists, but the current state of the generation cannot satisfy it,
 * which is what 409 is for. 400 would be wrong — nothing about the payload is
 * malformed, and retrying the same payload later could well succeed.
 *
 * <p>
 * Distinct from {@code DuplicateGroupCodeException}, which is the same status
 * but a different cause (the caller named a code that exists). They get
 * different messages in {@code GlobalExceptionHandler} so the user can tell
 * "you already have a group called 3A" from "there is no room left in this
 * level".
 *
 * <p>
 * Also thrown by {@code PreviewGroupCodesUseCaseImpl} on preview, so the UI
 * shows the shortage before the user commits to a submit.
 */
public class NotEnoughGroupCodesException extends RuntimeException {

	public NotEnoughGroupCodesException(String message) {
		super(message);
	}
}