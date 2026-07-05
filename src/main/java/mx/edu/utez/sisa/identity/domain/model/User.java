package mx.edu.utez.sisa.identity.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import mx.edu.utez.sisa.identity.shared.exception.MustChangePasswordException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Identity aggregate root. Owns the account-level invariants described in
 * {@code design.md} — Decision: mustChangePassword gate (service-layer domain
 * guard) and Decision: Auth ownership (domain validates, Spring provides
 * primitives). Credential matching itself is delegated to the
 * {@code PasswordHasher} out-port; this aggregate only tracks the state
 * machine around failed attempts, locking, and the mandatory first-access
 * password change.
 */
@Entity
@Table(name = "app_user")
public class User {

	private static final int MAX_FAILED_LOGIN_ATTEMPTS = 3;

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private UUID personId;

	@Column(nullable = false, unique = true)
	private String username;

	@Column(nullable = false)
	private String passwordHash;

	@Column(nullable = false)
	private boolean mustChangePassword;

	@Column(nullable = false)
	private int failedLoginAttempts;

	@Column(nullable = false)
	private boolean llaveMxLinked;

	@Column
	private String llaveMxSubject;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private UserStatus status;

	@Column
	private Instant lastLoginAt;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected User() {
		// JPA
	}

	public User(UUID personId, String username, String passwordHash) {
		this.personId = personId;
		this.username = username;
		this.passwordHash = passwordHash;
		this.mustChangePassword = true;
		this.failedLoginAttempts = 0;
		this.llaveMxLinked = false;
		this.status = UserStatus.ACTIVE;
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	/**
	 * Records a failed login attempt. Locks the account automatically once
	 * {@value #MAX_FAILED_LOGIN_ATTEMPTS} consecutive failures accumulate
	 * (spec: "Third consecutive failure locks the account").
	 */
	public void registerFailedLogin() {
		this.failedLoginAttempts++;
		if (this.failedLoginAttempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
			this.status = UserStatus.LOCKED;
		}
		this.updatedAt = Instant.now();
	}

	/**
	 * Guards every operation except {@code ChangePasswordUseCase} while a
	 * first-access password change is pending (design.md — Decision:
	 * mustChangePassword gate). Callers load the caller {@code User} and
	 * invoke this before proceeding.
	 */
	public void assertCanOperate() {
		if (this.mustChangePassword) {
			throw new MustChangePasswordException(
					"User " + this.id + " must change their password before performing this operation");
		}
	}

	/**
	 * Sets a new password hash and clears the first-access gate (spec:
	 * "Successful change lifts the block").
	 */
	public void changePassword(String newPasswordHash) {
		this.passwordHash = newPasswordHash;
		this.mustChangePassword = false;
		this.updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getPersonId() {
		return personId;
	}

	public String getUsername() {
		return username;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public boolean isMustChangePassword() {
		return mustChangePassword;
	}

	public int getFailedLoginAttempts() {
		return failedLoginAttempts;
	}

	public boolean isLlaveMxLinked() {
		return llaveMxLinked;
	}

	public void setLlaveMxLinked(boolean llaveMxLinked) {
		this.llaveMxLinked = llaveMxLinked;
	}

	public String getLlaveMxSubject() {
		return llaveMxSubject;
	}

	public void setLlaveMxSubject(String llaveMxSubject) {
		this.llaveMxSubject = llaveMxSubject;
	}

	public UserStatus getStatus() {
		return status;
	}

	public Instant getLastLoginAt() {
		return lastLoginAt;
	}

	public void setLastLoginAt(Instant lastLoginAt) {
		this.lastLoginAt = lastLoginAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof User user)) {
			return false;
		}
		return id != null && id.equals(user.id);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}
}
