package mx.edu.utez.sisa.identity.infrastructure.security;

import mx.edu.utez.sisa.identity.domain.port.out.TemporaryPasswordGenerator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link TemporaryPasswordGenerator} adapter enforcing the agreed password
 * policy: <strong>exactly 8 characters</strong> drawn from letters, digits and
 * the special set {@code ! # $ % / * - .}, with at least one uppercase letter,
 * one lowercase letter, one digit and one special character.
 *
 * <p>The guarantee is structural, not probabilistic: one character is drawn
 * from each of the four classes to fill the mandatory slots, the remaining slots
 * are drawn from the full alphabet, and only then is the list shuffled with a
 * {@link SecureRandom}. A pure random draw from the union would produce a
 * compliant password only ~68% of the time, which is not acceptable for a
 * credential the user is told to rely on.
 *
 * <p>Base64 is deliberately not used, unlike the sibling token generators: it
 * offers no control over character classes, and {@code +} / {@code =} / padding
 * are awkward to read aloud and easy to mistype when transcribed from a screen.
 *
 * <p>Deliberately absent from the special set: {@code + = < > " \ & _} and
 * whitespace. The value travels in a JSON response, is rendered on screen, gets
 * copied to the clipboard and is emailed as plain text, so every character here
 * has to survive all four hops unchanged and unambiguously.
 */
@Component
public class SecureRandomTemporaryPasswordGenerator implements TemporaryPasswordGenerator {

	static final int PASSWORD_LENGTH = 8;

	private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
	private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
	private static final String DIGITS = "0123456789";
	private static final String SPECIALS = "!#$%/*-.";

	private static final String ALPHABET = UPPERCASE + LOWERCASE + DIGITS + SPECIALS;

	private static final int MANDATORY_CLASSES = 4;

	private final SecureRandom secureRandom = new SecureRandom();

	@Override
	public String generate() {
		List<Character> characters = new ArrayList<>(PASSWORD_LENGTH);
		characters.add(pick(UPPERCASE));
		characters.add(pick(LOWERCASE));
		characters.add(pick(DIGITS));
		characters.add(pick(SPECIALS));
		while (characters.size() < PASSWORD_LENGTH) {
			characters.add(pick(ALPHABET));
		}
		Collections.shuffle(characters, secureRandom);
		return toString(characters);
	}

	private char pick(String source) {
		return source.charAt(secureRandom.nextInt(source.length()));
	}

	private static String toString(List<Character> characters) {
		StringBuilder builder = new StringBuilder(characters.size());
		for (char character : characters) {
			builder.append(character);
		}
		return builder.toString();
	}
}