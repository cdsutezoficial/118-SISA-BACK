package mx.edu.utez.sisa.admission.domain.port.in;

import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.RegisterCandidateCommand;

import java.util.UUID;

/**
 * Edición de la ficha de un candidato por Servicios Escolares (Fase 7 · T2).
 *
 * <p>Sobrescribe todos los datos capturados en la ficha (incluidos los que
 * llegan de LlaveMX). No toca el folio, el estado ni la verificación de
 * LlaveMX. Si cambia la carrera, el cambio y su efecto en el cobro los hace
 * {@link ChangeCandidateProgramUseCase} en la misma transacción.
 */
public interface UpdateCandidateUseCase {

	/** @param actingUserId quién edita; queda en el historial si cambia la carrera */
	void update(UUID candidateId, RegisterCandidateCommand command, UUID actingUserId);
}
