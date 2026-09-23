package mx.edu.utez.sisa.identity.domain.service;

import mx.edu.utez.sisa.identity.domain.model.PasswordResetToken;
import mx.edu.utez.sisa.identity.domain.model.User;
import mx.edu.utez.sisa.identity.domain.port.in.RequestPasswordResetUseCase.RequestPasswordResetCommand;
import mx.edu.utez.sisa.identity.domain.port.out.NotificationPort;
import mx.edu.utez.sisa.identity.domain.port.out.PasswordResetTokenGenerator;
import mx.edu.utez.sisa.identity.domain.port.out.PasswordResetTokenRepository;
import mx.edu.utez.sisa.identity.domain.port.out.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestPasswordResetUseCaseImplTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private PasswordResetTokenRepository passwordResetTokenRepository;
	@Mock
	private PasswordResetTokenGenerator passwordResetTokenGenerator;
	@Mock
	private NotificationPort notificationPort;

	private RequestPasswordResetUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new RequestPasswordResetUseCaseImpl(userRepository, passwordResetTokenRepository,
				passwordResetTokenGenerator, notificationPort, Duration.ofMinutes(30),
				"http://localhost:5173");
	}

	@Test
	void request_unknownUsernameSucceedsSilentlyWithoutSendingEmail() {
		when(userRepository.findByUsername("ghost@utez.edu.mx")).thenReturn(Optional.empty());

		assertThatCode(() -> useCase.request(new RequestPasswordResetCommand("ghost@utez.edu.mx")))
				.doesNotThrowAnyException();

		verify(passwordResetTokenRepository, never()).save(org.mockito.ArgumentMatchers.any());
		verify(notificationPort, never()).sendPasswordReset(anyString(), anyString());
	}

	@Test
	void request_knownUserPersistsHashedTokenAndSendsEmailWithLink() {
		User user = new User(UUID.randomUUID(), "jane.doe@utez.edu.mx", "hashed");
		when(userRepository.findByUsername("jane.doe@utez.edu.mx")).thenReturn(Optional.of(user));
		when(passwordResetTokenGenerator.generate()).thenReturn("plain-secret-token");
		when(passwordResetTokenRepository.findByUserId(user.getId())).thenReturn(List.of());

		useCase.request(new RequestPasswordResetCommand("jane.doe@utez.edu.mx"));

		ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
		verify(passwordResetTokenRepository).save(tokenCaptor.capture());
		PasswordResetToken saved = tokenCaptor.getValue();
		assertThat(saved.getUserId()).isEqualTo(user.getId());
		assertThat(saved.getTokenHash()).isEqualTo(TokenHashing.sha256("plain-secret-token"));
		assertThat(saved.isUsed()).isFalse();

		ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
		verify(notificationPort).sendPasswordReset(org.mockito.ArgumentMatchers.eq("jane.doe@utez.edu.mx"),
				linkCaptor.capture());
		assertThat(linkCaptor.getValue())
				.isEqualTo("http://localhost:5173/reset-confirm?token=plain-secret-token");
	}

	@Test
	void request_marksPreviousActiveTokensAsUsedBeforeSavingNewOne() {
		User user = new User(UUID.randomUUID(), "jane.doe@utez.edu.mx", "hashed");
		PasswordResetToken previous = new PasswordResetToken(user.getId(),
				TokenHashing.sha256("old-token"), java.time.Instant.now().plusSeconds(1800));
		when(userRepository.findByUsername("jane.doe@utez.edu.mx")).thenReturn(Optional.of(user));
		when(passwordResetTokenGenerator.generate()).thenReturn("new-token");
		when(passwordResetTokenRepository.findByUserId(user.getId())).thenReturn(List.of(previous));

		useCase.request(new RequestPasswordResetCommand("jane.doe@utez.edu.mx"));

		assertThat(previous.isUsed()).isTrue();
		verify(passwordResetTokenRepository, times(2)).save(org.mockito.ArgumentMatchers.any());
		ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
		verify(notificationPort).sendPasswordReset(org.mockito.ArgumentMatchers.eq("jane.doe@utez.edu.mx"),
				linkCaptor.capture());
		assertThat(linkCaptor.getValue()).contains("new-token");
	}
}