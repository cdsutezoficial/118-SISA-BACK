package mx.edu.utez.sisa.identity.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Single-use token for the "forgot my password" flow (01-identidad.md —
 * PasswordResetToken). Only the SHA-256 hash is ever persisted; the plaintext
 * value travels to the user's inbox via email. {@code usedAt} being non-null
 * means the token was already consumed; {@code expiresAt} enforces the 30-minute
 * TTL. The "one active token per user, new tokens invalidate previous ones" rule
 * is orchestrated by {@code RequestPasswordResetUseCase}, not by this class —
 * this is a data holder.
 */
@Entity
@Table(name = "password_reset_token")
public class PasswordResetToken {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID userId;

	@Column(nullable = false, unique = true)
	private String tokenHash;

	@Column(nullable = false)
	private Instant expiresAt;

	@Column
	private Instant usedAt;

	protected PasswordResetToken() {
		// JPA
	}

	public PasswordResetToken(UUID userId, String tokenHash, Instant expiresAt) {
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getUsedAt() {
		return usedAt;
	}

	public boolean isUsed() {
		return usedAt != null;
	}

	public void markUsed() {
		this.usedAt = Instant.now();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof PasswordResetToken that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}