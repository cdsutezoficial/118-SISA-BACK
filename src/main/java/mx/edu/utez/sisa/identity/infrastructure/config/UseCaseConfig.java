package mx.edu.utez.sisa.identity.infrastructure.config;

import mx.edu.utez.sisa.identity.domain.port.in.AssignRoleUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.AssignPermissionsToRoleUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.AuthenticateUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.ChangePermissionStatusUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.ChangePasswordUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.ChangeRoleStatusUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.CreatePermissionUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.CreatePersonUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.CreateRoleUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.CreateUserUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.GetCurrentProfileUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.GetPermissionUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.GetRoleUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.GetUserUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.ListPermissionsUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.ListPersonsUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.ListRolesUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.ListUsersUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.RefreshAccessTokenUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.RequestPasswordResetUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.ResetPasswordUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.RevokeRoleUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.UnlockUserUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.UpdatePermissionUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.UpdateRoleUseCase;
import mx.edu.utez.sisa.identity.domain.port.out.AccessTokenIssuer;
import mx.edu.utez.sisa.identity.domain.port.out.NotificationPort;
import mx.edu.utez.sisa.identity.domain.port.out.PasswordHasher;
import mx.edu.utez.sisa.identity.domain.port.out.PasswordResetTokenGenerator;
import mx.edu.utez.sisa.identity.domain.port.out.PasswordResetTokenRepository;
import mx.edu.utez.sisa.identity.domain.port.out.PermissionRepository;
import mx.edu.utez.sisa.identity.domain.port.out.PersonRepository;
import mx.edu.utez.sisa.identity.domain.port.out.RefreshTokenGenerator;
import mx.edu.utez.sisa.identity.domain.port.out.RefreshTokenRepository;
import mx.edu.utez.sisa.identity.domain.port.out.RolePermissionCacheInvalidator;
import mx.edu.utez.sisa.identity.domain.port.out.RolePermissionRepository;
import mx.edu.utez.sisa.identity.domain.port.out.RoleRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
import mx.edu.utez.sisa.identity.domain.service.AssignRoleUseCaseImpl;
import mx.edu.utez.sisa.identity.domain.service.AssignPermissionsToRoleUseCaseImpl;
import mx.edu.utez.sisa.identity.domain.service.AuthenticateUseCaseImpl;
import mx.edu.utez.sisa.identity.domain.service.ChangePermissionStatusUseCaseImpl;
import mx.edu.utez.sisa.identity.domain.service.ChangePasswordUseCaseImpl;
import mx.edu.utez.sisa.identity.domain.service.ChangeRoleStatusUseCaseImpl;
import mx.edu.utez.sisa.identity.domain.service.CreatePermissionUseCaseImpl;
import mx.edu.utez.sisa.identity.domain.service.CreatePersonUseCaseImpl;
import mx.edu.utez.sisa.identity.domain.service.CreateRoleUseCaseImpl;
import mx.edu.utez.sisa.identity.domain.service.CreateUserUseCaseImpl;
import mx.edu.utez.sisa.identity.domain.service.GetCurrentProfileUseCaseImpl;
import mx.edu.utez.sisa.identity.domain.service.GetPermissionUseCaseImpl;
import mx.edu.utez.sisa.identity.domain.service.GetRoleUseCaseImpl;
import mx.edu.utez.sisa.identity.domain.service.GetUserUseCaseImpl;
import mx.edu.utez.sisa.identity.domain.service.ListPermissionsUseCaseImpl;
import mx.edu.utez.sisa.identity.domain.service.ListPersonsUseCaseImpl;
import mx.edu.utez.sisa.identity.domain.service.ListRolesUseCaseImpl;
import mx.edu.utez.sisa.identity.domain.service.ListUsersUseCaseImpl;
import mx.edu.utez.sisa.identity.domain.service.RefreshAccessTokenUseCaseImpl;
import mx.edu.utez.sisa.identity.domain.service.RequestPasswordResetUseCaseImpl;
import mx.edu.utez.sisa.identity.domain.service.ResetPasswordUseCaseImpl;
import mx.edu.utez.sisa.identity.domain.service.RevokeRoleUseCaseImpl;
import mx.edu.utez.sisa.identity.domain.service.UnlockUserUseCaseImpl;
import mx.edu.utez.sisa.identity.domain.service.UpdatePermissionUseCaseImpl;
import mx.edu.utez.sisa.identity.domain.service.UpdateRoleUseCaseImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Composition root wiring the use case interactors as Spring beans. The
 * {@code XxxUseCaseImpl} classes are plain, framework-agnostic classes (no
 * stereotype annotations, per PR2/PR3 convention) so this is the only place
 * that constructs them with their out-port dependencies. Extended by plan
 * {@code docs/plans/2026-07-28-persons-and-user-management.md} with 5 more
 * beans: {@code createPersonUseCase}, {@code listPersonsUseCase},
 * {@code getUserUseCase}, {@code revokeRoleUseCase}, {@code unlockUserUseCase}.
 */
@Configuration
public class UseCaseConfig {

	@Bean
	public CreateUserUseCase createUserUseCase(UserRepository userRepository, PersonRepository personRepository,
			PasswordHasher passwordHasher) {
		return new CreateUserUseCaseImpl(userRepository, personRepository, passwordHasher);
	}

	@Bean
	public AssignRoleUseCase assignRoleUseCase(UserRepository userRepository, UserRoleRepository userRoleRepository,
			RoleRepository roleRepository) {
		return new AssignRoleUseCaseImpl(userRepository, userRoleRepository, roleRepository);
	}

	@Bean
	public ChangePasswordUseCase changePasswordUseCase(UserRepository userRepository, PasswordHasher passwordHasher) {
		return new ChangePasswordUseCaseImpl(userRepository, passwordHasher);
	}

	@Bean
	public AuthenticateUseCase authenticateUseCase(UserRepository userRepository, UserRoleRepository userRoleRepository,
			PasswordHasher passwordHasher, AccessTokenIssuer accessTokenIssuer,
			RefreshTokenGenerator refreshTokenGenerator, RefreshTokenRepository refreshTokenRepository,
			RoleRepository roleRepository, @Value("${sisa.security.jwt.refresh-token-ttl}") Duration refreshTokenTtl) {
		return new AuthenticateUseCaseImpl(userRepository, userRoleRepository, passwordHasher, accessTokenIssuer,
				refreshTokenGenerator, refreshTokenRepository, roleRepository, refreshTokenTtl);
	}

	@Bean
	public RefreshAccessTokenUseCase refreshAccessTokenUseCase(RefreshTokenRepository refreshTokenRepository,
			UserRepository userRepository, UserRoleRepository userRoleRepository, RoleRepository roleRepository,
			AccessTokenIssuer accessTokenIssuer) {
		return new RefreshAccessTokenUseCaseImpl(refreshTokenRepository, userRepository, userRoleRepository,
				roleRepository, accessTokenIssuer);
	}

	@Bean
	public RequestPasswordResetUseCase requestPasswordResetUseCase(UserRepository userRepository,
			PasswordResetTokenRepository passwordResetTokenRepository,
			PasswordResetTokenGenerator passwordResetTokenGenerator, NotificationPort notificationPort,
			@Value("${sisa.security.password-reset.token-ttl}") Duration passwordResetTokenTtl,
			@Value("${sisa.security.password-reset.frontend-base-url}") String frontendBaseUrl) {
		return new RequestPasswordResetUseCaseImpl(userRepository, passwordResetTokenRepository,
				passwordResetTokenGenerator, notificationPort, passwordResetTokenTtl, frontendBaseUrl);
	}

	@Bean
	public ResetPasswordUseCase resetPasswordUseCase(PasswordResetTokenRepository passwordResetTokenRepository,
			UserRepository userRepository, PasswordHasher passwordHasher) {
		return new ResetPasswordUseCaseImpl(passwordResetTokenRepository, userRepository, passwordHasher);
	}

	@Bean
	public ListUsersUseCase listUsersUseCase(UserRepository userRepository, UserRoleRepository userRoleRepository,
			RoleRepository roleRepository) {
		return new ListUsersUseCaseImpl(userRepository, userRoleRepository, roleRepository);
	}

	@Bean
	public CreatePersonUseCase createPersonUseCase(UserRepository userRepository, PersonRepository personRepository) {
		return new CreatePersonUseCaseImpl(userRepository, personRepository);
	}

	@Bean
	public ListPersonsUseCase listPersonsUseCase(UserRepository userRepository, PersonRepository personRepository) {
		return new ListPersonsUseCaseImpl(userRepository, personRepository);
	}

	@Bean
	public GetUserUseCase getUserUseCase(UserRepository userRepository, PersonRepository personRepository,
			UserRoleRepository userRoleRepository, RoleRepository roleRepository) {
		return new GetUserUseCaseImpl(userRepository, personRepository, userRoleRepository, roleRepository);
	}

	@Bean
	public GetCurrentProfileUseCase getCurrentProfileUseCase(UserRepository userRepository,
			PersonRepository personRepository) {
		return new GetCurrentProfileUseCaseImpl(userRepository, personRepository);
	}

	@Bean
	public RevokeRoleUseCase revokeRoleUseCase(UserRepository userRepository, UserRoleRepository userRoleRepository) {
		return new RevokeRoleUseCaseImpl(userRepository, userRoleRepository);
	}

	@Bean
	public UnlockUserUseCase unlockUserUseCase(UserRepository userRepository) {
		return new UnlockUserUseCaseImpl(userRepository);
	}

	@Bean
	public CreateRoleUseCase createRoleUseCase(RoleRepository roleRepository) {
		return new CreateRoleUseCaseImpl(roleRepository);
	}

	@Bean
	public UpdateRoleUseCase updateRoleUseCase(RoleRepository roleRepository) {
		return new UpdateRoleUseCaseImpl(roleRepository);
	}

	@Bean
	public ChangeRoleStatusUseCase changeRoleStatusUseCase(UserRepository userRepository, RoleRepository roleRepository) {
		return new ChangeRoleStatusUseCaseImpl(userRepository, roleRepository);
	}

	@Bean
	public ListRolesUseCase listRolesUseCase(RoleRepository roleRepository) {
		return new ListRolesUseCaseImpl(roleRepository);
	}

	@Bean
	public GetRoleUseCase getRoleUseCase(RoleRepository roleRepository,
			RolePermissionRepository rolePermissionRepository, PermissionRepository permissionRepository) {
		return new GetRoleUseCaseImpl(roleRepository, rolePermissionRepository, permissionRepository);
	}

	@Bean
	public AssignPermissionsToRoleUseCase assignPermissionsToRoleUseCase(UserRepository userRepository,
			RoleRepository roleRepository, PermissionRepository permissionRepository,
			RolePermissionRepository rolePermissionRepository, RolePermissionCacheInvalidator rolePermissionCacheInvalidator) {
		return new AssignPermissionsToRoleUseCaseImpl(userRepository, roleRepository, permissionRepository,
				rolePermissionRepository, rolePermissionCacheInvalidator);
	}

	@Bean
	public CreatePermissionUseCase createPermissionUseCase(PermissionRepository permissionRepository) {
		return new CreatePermissionUseCaseImpl(permissionRepository);
	}

	@Bean
	public UpdatePermissionUseCase updatePermissionUseCase(PermissionRepository permissionRepository) {
		return new UpdatePermissionUseCaseImpl(permissionRepository);
	}

	@Bean
	public ChangePermissionStatusUseCase changePermissionStatusUseCase(UserRepository userRepository,
			PermissionRepository permissionRepository, RolePermissionCacheInvalidator rolePermissionCacheInvalidator) {
		return new ChangePermissionStatusUseCaseImpl(userRepository, permissionRepository,
				rolePermissionCacheInvalidator);
	}

	@Bean
	public ListPermissionsUseCase listPermissionsUseCase(PermissionRepository permissionRepository) {
		return new ListPermissionsUseCaseImpl(permissionRepository);
	}

	@Bean
	public GetPermissionUseCase getPermissionUseCase(PermissionRepository permissionRepository) {
		return new GetPermissionUseCaseImpl(permissionRepository);
	}
}
