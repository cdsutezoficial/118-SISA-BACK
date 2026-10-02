package mx.edu.utez.sisa.identity.infrastructure.security;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SecureRandomTemporaryPasswordGeneratorTest {

	private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
	private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
	private static final Pattern DIGIT = Pattern.compile("[0-9]");
	private static final Pattern SPECIAL = Pattern.compile("[!#$%/*.\\-]");

	private final SecureRandomTemporaryPasswordGenerator generator = new SecureRandomTemporaryPasswordGenerator();

	@RepeatedTest(200)
	void generate_returnsExactlyEightCharactersCoveringAllFourRequiredClasses() {
		String password = generator.generate();

		assertThat(password).hasSize(SecureRandomTemporaryPasswordGenerator.PASSWORD_LENGTH);
		assertThat(password).matches("[A-Za-z0-9!#$%/*.\\-]{8}");
		assertThat(UPPERCASE.matcher(password).find()).isTrue();
		assertThat(LOWERCASE.matcher(password).find()).isTrue();
		assertThat(DIGIT.matcher(password).find()).isTrue();
		assertThat(SPECIAL.matcher(password).find()).isTrue();
	}

	@RepeatedTest(200)
	void generate_isShuffledRatherThanPinningTheMandatoryCharactersToFixedSlots() {
		Set<Integer> specialIndexes = new HashSet<>();
		for (int i = 0; i < 200; i++) {
			String password = generator.generate();
			for (int index = 0; index < password.length(); index++) {
				if (SPECIAL.matcher(String.valueOf(password.charAt(index))).matches()) {
					specialIndexes.add(index);
				}
			}
		}

		// A generator that only picked "one special per class" without shuffling
		// would confine specials to a single slot; the shuffle must spread them.
		assertThat(specialIndexes).hasSizeGreaterThan(1);
	}

	@RepeatedTest(200)
	void generate_doesNotRepeatAcrossCalls() {
		Set<String> passwords = new HashSet<>();
		for (int i = 0; i < 200; i++) {
			passwords.add(generator.generate());
		}

		assertThat(passwords).hasSize(200);
	}

	@Test
	void generate_neverEmitsWhitespaceOrUrlDelimitersThatWouldBreakTransport() {
		for (int i = 0; i < 200; i++) {
			assertThat(generator.generate()).doesNotContain(" ", "\"", "\\", "+", "=", "<", ">", "&", "?");
		}
	}
}