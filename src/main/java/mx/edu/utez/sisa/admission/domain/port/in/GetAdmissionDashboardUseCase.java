package mx.edu.utez.sisa.admission.domain.port.in;

import mx.edu.utez.sisa.shared.model.ProgramModality;

import java.util.List;
import java.util.UUID;

/**
 * Conteos del proceso de admisión de un periodo, para el tablero de Admisión.
 *
 * <p>El periodo es el de destino de las configuraciones de admisión (el periodo al
 * que entran los admitidos). Sin periodo pedido se toma el del proceso más
 * reciente: el de una configuración abierta, o si no hay, el de la última que
 * abrió.
 *
 * <p>Un Director de división solo cuenta las carreras de su división, igual que en
 * la lista de candidatos; sin división asignada no ve nada.
 */
public interface GetAdmissionDashboardUseCase {

	AdmissionDashboard get(UUID callerId, UUID periodId);

	/**
	 * @param periodId   el periodo contado; {@code null} si todavía no hay ningún
	 *                   proceso de admisión
	 * @param periodName su nombre, para el encabezado
	 */
	record AdmissionDashboard(UUID periodId, String periodName, Funnel funnel, List<ProgramStats> programs) {
	}

	/**
	 * El embudo. Cada etapa cuenta a quien llegó a ella o la pasó:
	 * <ul>
	 * <li>{@code registered}: todas las fichas, también las vencidas;</li>
	 * <li>{@code paid}: pagadas, con examen, aceptadas, rechazadas o inscritas;</li>
	 * <li>{@code examTaken}: con examen, aceptadas, rechazadas o inscritas;</li>
	 * <li>{@code inductionEnabled}: habilitadas para inducción;</li>
	 * <li>{@code admitted}: aceptadas o inscritas.</li>
	 * </ul>
	 * {@code expired} son las fichas que vencieron sin pagarse.
	 */
	record Funnel(long registered, long paid, long examTaken, long inductionEnabled, long admitted, long expired) {
	}

	/**
	 * Una carrera. {@code inProcess} son las fichas que siguen en el proceso
	 * (registradas, pagadas, con examen o aceptadas sin inscribir);
	 * {@code completed}, las que ya terminaron (inscritas o rechazadas). Las vencidas
	 * no cuentan en ninguna de las dos.
	 */
	record ProgramStats(UUID programId, String programName, ProgramModality modality, long registered, long paid,
			long admitted, long inProcess, long completed, long expired) {
	}
}
