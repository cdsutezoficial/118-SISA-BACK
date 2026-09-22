package mx.edu.utez.sisa.identity.infrastructure.web.dto;

/**
 * Body of {@code GET /auth/me/capabilities}: the caller's OWN permission keys,
 * delivered as a single base64url-encoded JSON array — never as plaintext keys
 * in the JSON body and never embedded in the access token. The envelope keeps
 * the payload compact and unreadable-at-a-glance on the wire; the server-side
 * fine-grained layer remains the real authority regardless of what the client
 * decodes.
 *
 * @param capabilities base64url-encoded JSON array of ACTIVE permission keys
 */
public record CapabilityResponse(String capabilities) {
}