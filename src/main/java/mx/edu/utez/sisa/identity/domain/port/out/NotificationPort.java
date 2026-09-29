package mx.edu.utez.sisa.identity.domain.port.out;

/**
 * Out-port for sending emails (01-identidad.md — NotificationPort). Currently
 * backs the password-reset flow: {@link #sendPasswordReset} delivers the
 * single-use reset link to the user's institutional email.
 */
public interface NotificationPort {

	void sendPasswordReset(String to, String resetLink);
}