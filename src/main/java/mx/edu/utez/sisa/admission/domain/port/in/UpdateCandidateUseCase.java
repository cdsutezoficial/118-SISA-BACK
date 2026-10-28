package mx.edu.utez.sisa.admission.domain.port.in;

import mx.edu.utez.sisa.admission.domain.port.in.RegisterCandidateUseCase.RegisterCandidateCommand;

import java.util.UUID;

/**
 * Edición de la ficha de un candidato por Servicios Escolares (Fase 7 · T2).
 *
 * <p>Sobrescribe todos los datos capturados en la ficha (incluidos los que
 * llegan de LlaveMX). No toca el folio, el estado, la verificación de LlaveMX
 * ni nada de pagos. El cambio de carrera todavía no se acepta aquí: requiere
 * recotizar la ficha (Fase 6) y llega en su propia rama.
 */
public interface UpdateCandidateUseCase {

	void update(UUID candidateId, RegisterCandidateCommand command);
}
