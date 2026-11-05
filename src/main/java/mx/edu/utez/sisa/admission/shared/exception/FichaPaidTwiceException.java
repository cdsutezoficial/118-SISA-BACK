package mx.edu.utez.sisa.admission.shared.exception;

/**
 * The ficha was already paid when another of its orders captured (E1): that second
 * capture was booked as saldo a favor. A {@link CandidateAlreadyPaidException},
 * so whoever treated "already paid" as "paid either way" keeps doing so.
 */
public class FichaPaidTwiceException extends CandidateAlreadyPaidException {

	public FichaPaidTwiceException(String message) {
		super(message);
	}
}
