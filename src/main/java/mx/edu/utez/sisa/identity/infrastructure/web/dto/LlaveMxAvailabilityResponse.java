package mx.edu.utez.sisa.identity.infrastructure.web.dto;

/**
 * Whether the LlaveMX integration is usable in this environment. The front
 * reads it to decide whether to render the button, so an unconfigured
 * deployment never sends an applicant into a round that cannot finish.
 */
public record LlaveMxAvailabilityResponse(boolean configured) {
}
