package mx.edu.utez.sisa.identity.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F7: every staff route has a fine-grained permission, or is one of the exceptions
 * listed here with its reason. The registry fails silently — a route it does not know is
 * governed by the coarse role matcher alone — so a new controller method without its
 * entry would let anyone with the role through. This walks every {@code @RestController}
 * and fails on such a route instead.
 *
 * <p>Same exceptions as {@code roles-permisos.md} §3.3 and the F7 inventory.
 */
class PermissionRegistryCoverageTest {

	/** Path variables are replaced by this so the Ant patterns can match. */
	private static final String ANY_ID = "00000000-0000-0000-0000-000000000001";

	/**
	 * Routes with no permission on purpose. Public: the candidate portal and login need no
	 * session. Self-service: every signed-in user may read their own profile and change
	 * their own password. {@code /auth/llave-mx/link} is ADMIN-only in the coarse layer,
	 * and ADMIN skips the fine layer.
	 */
	private static final Set<String> EXCEPTIONS = Set.of(
			// público — portal del aspirante
			"POST /candidates", "POST /candidates/payment-access", "GET /candidates/*",
			"GET /candidates/*/payment-access", "GET /candidates/*/ficha.pdf",
			"POST /candidates/*/payments/confirm", "POST /candidates/*/payments/checkout",
			"POST /candidates/*/payments/release", "GET /program-admission-configs/*/ficha-amount",
			// público — catálogos de domicilio
			"GET /postal-codes/*", "GET /states", "GET /municipalities",
			// sesión propia
			"GET /auth/me", "GET /auth/me/capabilities", "POST /auth/change-password");

	/** Login, refresh, password reset and Llave MX: public, or ADMIN-only (link). */
	private static final List<String> EXCEPTION_PREFIXES = List.of("/auth/", "/oauth/", "/SISAVA/");

	@Test
	void everyStaffRouteHasAFineGrainedPermissionOrADocumentedException() throws Exception {
		Set<String> uncovered = new TreeSet<>();
		for (String route : routes()) {
			String method = route.substring(0, route.indexOf(' '));
			String path = route.substring(route.indexOf(' ') + 1);
			String wildcard = path.replaceAll("\\{[^/]+}", "*");
			if (EXCEPTIONS.contains(method + " " + wildcard)
					|| EXCEPTION_PREFIXES.stream().anyMatch(wildcard::startsWith)
					|| path.endsWith("/options") || path.endsWith("/available")) {
				continue;
			}
			String concrete = path.replaceAll("\\{[^/]+}", ANY_ID);
			if (PermissionRegistry.resolve(method, concrete).isEmpty()) {
				uncovered.add(method + " " + path);
			}
		}
		assertThat(uncovered).as("rutas de personal sin permiso fino (PermissionRegistry)").isEmpty();
	}

	@Test
	void theWalkFindsTheControllers() throws Exception {
		assertThat(routes()).contains("GET /finance/alerts", "POST /candidates/{id}/cash-payment",
				"PUT /payment-concepts/{conceptId}/rates");
	}

	private static List<String> routes() throws Exception {
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
		List<String> routes = new ArrayList<>();
		for (BeanDefinition definition : scanner.findCandidateComponents("mx.edu.utez.sisa")) {
			Class<?> type = Class.forName(definition.getBeanClassName());
			RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
			String[] prefixes = base == null || base.path().length == 0 ? new String[] { "" } : base.path();
			for (Method handler : type.getDeclaredMethods()) {
				RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(handler, RequestMapping.class);
				if (mapping == null) {
					continue;
				}
				String[] paths = mapping.path().length == 0 ? new String[] { "" } : mapping.path();
				for (RequestMethod verb : mapping.method()) {
					for (String prefix : prefixes) {
						for (String path : paths) {
							routes.add(verb.name() + " " + prefix + path);
						}
					}
				}
			}
		}
		return routes;
	}
}
