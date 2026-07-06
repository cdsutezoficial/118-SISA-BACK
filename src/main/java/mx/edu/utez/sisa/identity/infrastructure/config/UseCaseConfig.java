package mx.edu.utez.sisa.identity.infrastructure.config;

import mx.edu.utez.sisa.identity.domain.port.in.AssignRoleUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.AuthenticateUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.ChangePasswordUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.CreateUserUseCase;
import mx.edu.utez.sisa.identity.domain.port.in.RefreshAccessTokenUseCase;
import mx.edu.utez.sisa.identity.domain.port.out.AccessTokenIssuer;
import mx.edu.utez.sisa.identity.domain.port.out.PasswordHasher;
import mx.edu.utez.sisa.identity.domain.port.out.PersonRepository;
import mx.edu.utez.sisa.identity.domain.port.out.RefreshTokenGenerator;
import mx.edu.utez.sisa.identity.domain.port.out.RefreshTokenRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRoleRepository;
import mx.edu.utez.sisa.identity.domain.service.AssignRoleUseCaseImpl;
import mx.edu.utez.sisa.identity.domain.service.AuthenticateUseCaseImpl;
import mx.edu.utez.sisa.identity.domain.service.ChangePasswordUseCaseImpl;
import mx.edu.utez.sisa.identity.domain.service.CreateUserUseCaseImpl;
import mx.edu.utez.sisa.identity.domain.service.RefreshAccessTokenUseCaseImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Composition root wiring the 5 use case interactors as Spring beans. The
 * {@code XxxUseCaseImpl} classes are plain, framework-agnostic classes (no
 * stereotype annotations, per PR2/PR3 convention) so this is the only place
 * that constructs them with their out-port dependencies.
 */
@Configuration
public class UseCaseConfig {

	@Bean
	public CreateUserUseCase createUserUseCase(UserRepository userRepository, PersonRepository personRepository,
			PasswordHasher passwordHasher) {
		return new CreateUserUseCaseImpl(userRepository, personRepository, passwordHasher);
	}

	@Bean
	public AssignRoleUseCase assignRoleUseCase(UserRepository userRepository, UserRoleRepository userRoleRepository) {
		return new AssignRoleUseCaseImpl(userRepository, userRoleRepository);
	}

	@Bean
	public ChangePasswordUseCase changePasswordUseCase(UserRepository userRepository, PasswordHasher passwordHasher) {
		return new ChangePasswordUseCaseImpl(userRepository, passwordHasher);
	}

	@Bean
	public AuthenticateUseCase authenticateUseCase(UserRepository userRepository, UserRoleRepository userRoleRepository,
			PasswordHasher passwordHasher, AccessTokenIssuer accessTokenIssuer,
			RefreshTokenGenerator refreshTokenGenerator, RefreshTokenRepository refreshTokenRepository,
			@Value("${sisa.security.jwt.refresh-token-ttl}") Duration refreshTokenTtl) {
		return new AuthenticateUseCaseImpl(userRepository, userRoleRepository, passwordHasher, accessTokenIssuer,
				refreshTokenGenerator, refreshTokenRepository, refreshTokenTtl);
	}

	@Bean
	public RefreshAccessTokenUseCase refreshAccessTokenUseCase(RefreshTokenRepository refreshTokenRepository,
			UserRepository userRepository, UserRoleRepository userRoleRepository, AccessTokenIssuer accessTokenIssuer) {
		return new RefreshAccessTokenUseCaseImpl(refreshTokenRepository, userRepository, userRoleRepository,
				accessTokenIssuer);
	}
}
