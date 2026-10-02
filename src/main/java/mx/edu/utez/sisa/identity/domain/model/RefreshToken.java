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
 * An opaque refresh token, persisted only as its SHA-256 hash (design.md —
 * Decision: Refresh endpoint). Validity/expiry/revocation/owning-user-status
 * checks are orchestrated by the refresh exchange flow, not by this class —
 * this is a data holder.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

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
	private Instant revokedAt;

	protected RefreshToken() {
		// JPA
	}

	public RefreshToken(UUID userId, String tokenHash, Instant expiresAt) {
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

	public Instant getRevokedAt() {
		return revokedAt;
	}

	/**
	 * Marks this token as no longer usable (plan
	 * {@code 2026-10-02-admin-reset-password.md}): an ADMIN reset of the
	 * holder's password must not leave a stolen session alive for the remaining
	 * {@code refresh-token-ttl}. Idempotent — the first revocation wins, so a
	 * later bulk revoke cannot overwrite the original timestamp.
	 */
	public void revoke(Instant revokedAt) {
		if (this.revokedAt == null) {
			this.revokedAt = revokedAt;
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof RefreshToken that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
