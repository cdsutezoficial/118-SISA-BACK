package mx.edu.utez.sisa.admission.domain.port.in;

import mx.edu.utez.sisa.admission.domain.model.CandidateStatus;
import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.RegisterCandidateCommand;
import mx.edu.utez.sisa.admission.domain.port.out.FichaPaymentPort.ProgramChangeCharge;
import mx.edu.utez.sisa.shared.model.ProgramModality;

import java.util.UUID;

/**
 * La ficha de admisión completa de un candidato, tal como la necesita el
 * formulario de edición de Servicios Escolares (Fase 7 · T2).
 *
 * <p>A diferencia de {@link GetCandidateFichaUseCase}, que proyecta nombres de
 * catálogo para mostrar, aquí viajan los IDs (estado, municipio, canal, tipo de
 * bachillerato, configuración de admisión): el formulario los vuelve a enviar
 * sin cambios en el {@code PUT}.
 */
public interface GetCandidateEditFormUseCase {

	CandidateEditForm get(UUID candidateId);

	/**
	 * @param ficha los datos capturados, con la misma forma que el registro.
	 * @param programChangeAllowed si el formulario puede ofrecer el cambio de
	 *            carrera (no con el candidato inscrito, la ficha vencida o un
	 *            pago en curso).
	 * @param programChangeBlockedReason por qué no se puede cambiar la carrera;
	 *            {@code null} si se puede.
	 * @param pendingProgramChange la diferencia que dejó un cambio de carrera
	 *            anterior y falta pagar; {@code null} si no hay.
	 */
	record CandidateEditForm(UUID candidateId, String folio, CandidateStatus status, boolean llaveMxVerified,
			String programName, ProgramModality modality, boolean programChangeAllowed,
			String programChangeBlockedReason, RegisterCandidateCommand ficha, ProgramChangeCharge pendingProgramChange) {
	}
}
