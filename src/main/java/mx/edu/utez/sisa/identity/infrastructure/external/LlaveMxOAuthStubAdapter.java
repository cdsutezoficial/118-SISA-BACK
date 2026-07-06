package mx.edu.utez.sisa.identity.infrastructure.external;

import mx.edu.utez.sisa.identity.domain.port.out.LlaveMxOAuthPort;
import org.springframework.stereotype.Component;

/**
 * Stub {@link LlaveMxOAuthPort} adapter. LlaveMX OAuth login is out of scope
 * for this slice (design.md — Purpose); declared now so the domain layer's
 * shape does not change when the real integration lands.
 */
@Component
public class LlaveMxOAuthStubAdapter implements LlaveMxOAuthPort {

	@Override
	public LlaveMxProfile exchangeAuthorizationCode(String authorizationCode) {
		throw new UnsupportedOperationException("LlaveMX OAuth integration is not implemented in this slice");
	}
}
