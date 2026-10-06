package mx.edu.utez.sisa.identity.infrastructure.web.dto;

/**
 * What LlaveMX said about the person who just verified, in the shape the
 * registration wizard stores it (RF-AUTH-003: "el candidato no los captura
 * manualmente").
 *
 * <p>Every field is a plain string and every one may be empty — which of them
 * the provider actually returns is not contractual. The front treats an empty
 * value as "not provided" and keeps that input editable, so a provider that
 * withholds, say, the birth state leaves a normal field to type in rather than
 * an empty locked box the applicant cannot leave.
 *
 * <p>No token, no subject and no provider identifier travels here: this is the
 * same data already shown read-only on screen, nothing that could be replayed
 * into a session.
 */
public record LlaveMxPrefillResponse(String nombres, String apellidoPaterno, String apellidoMaterno, String curp,
		String fechaNacimiento, String sexo, String estadoNacimiento, String municipioNacimiento, String email,
		String celular) {
}
