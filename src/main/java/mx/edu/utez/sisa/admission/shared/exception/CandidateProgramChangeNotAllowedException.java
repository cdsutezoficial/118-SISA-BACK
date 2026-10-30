package mx.edu.utez.sisa.admission.shared.exception;

/**
 * La carrera del candidato no se puede cambiar en su estado actual: ya está
 * inscrito, su ficha venció, o hay un pago en curso. Responde 409.
 */
public class CandidateProgramChangeNotAllowedException extends RuntimeException {

	public CandidateProgramChangeNotAllowedException(String message) {
		super(message);
	}
}
