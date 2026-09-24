package mx.edu.utez.sisa.shared.bootstrap;

import mx.edu.utez.sisa.shared.model.Municipality;
import mx.edu.utez.sisa.shared.model.State;
import mx.edu.utez.sisa.shared.persistence.MunicipalityJpaRepository;
import mx.edu.utez.sisa.shared.persistence.StateJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bootstraps the closed INEGI {@link State}/{@link Municipality} catalogs on
 * startup (plan: {@code docs/plans/2026-07-28-inegi-catalogs-and-highschooltype.md},
 * section 4). Same idempotent-{@code ApplicationRunner} shape as
 * {@code identity.TestAccountsSeedRunner}, but
 * checks table emptiness rather than a role invariant — unlike those two
 * seeds, there is no narrower business invariant to check: this catalog is
 * either fully seeded or not seeded at all, and is never written to again
 * after the first run.
 *
 * <p>Lives in {@code shared.bootstrap} rather than
 * {@code identity.infrastructure.bootstrap} — this seed is not
 * identity-specific, it bootstraps shared-kernel reference data that every
 * bounded context may eventually consume (starting with {@code Person} in
 * Fase B).
 */
@Component
public class StateAndMunicipalitySeedRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(StateAndMunicipalitySeedRunner.class);

	private static final String STATES_CSV_LOCATION = "seed/inegi_estados.csv";

	private static final String MUNICIPALITIES_CSV_LOCATION = "seed/inegi_municipios.csv";

	private final StateJpaRepository stateRepository;

	private final MunicipalityJpaRepository municipalityRepository;

	public StateAndMunicipalitySeedRunner(StateJpaRepository stateRepository,
			MunicipalityJpaRepository municipalityRepository) {
		this.stateRepository = stateRepository;
		this.municipalityRepository = municipalityRepository;
	}

	@Override
	public void run(ApplicationArguments args) throws IOException {
		if (stateRepository.count() > 0) {
			log.info("The state catalog is already seeded; skipping INEGI catalogs bootstrap seed");
			return;
		}

		Map<String, UUID> stateIdByInegiCode = seedStates();
		seedMunicipalities(stateIdByInegiCode);
	}

	/**
	 * Reads {@code inegi_estados.csv} (columns: {@code inegi_code,name}),
	 * inserts all 32 rows in one batch, and returns the
	 * {@code inegiCode -> State.id} map the municipality pass needs to
	 * resolve {@code stateId}.
	 */
	private Map<String, UUID> seedStates() throws IOException {
		List<State> states = new ArrayList<>();
		for (String[] row : readCsvDataRows(STATES_CSV_LOCATION)) {
			String inegiCode = row[0];
			String name = row[1];
			states.add(new State(name, inegiCode));
		}

		List<State> saved = stateRepository.saveAll(states);

		Map<String, UUID> stateIdByInegiCode = new HashMap<>();
		for (State state : saved) {
			stateIdByInegiCode.put(state.getInegiCode(), state.getId());
		}

		log.info("Seeded {} states from {}", saved.size(), STATES_CSV_LOCATION);
		return stateIdByInegiCode;
	}

	/**
	 * Reads {@code inegi_municipios.csv} (columns:
	 * {@code state_inegi_code,inegi_code,name}), resolves each row's
	 * {@code state_inegi_code} against the map built by {@link #seedStates()},
	 * and batches all 2,469 rows into a single {@code saveAll} call — not one
	 * row at a time, which would be 2,469 individual inserts.
	 */
	private void seedMunicipalities(Map<String, UUID> stateIdByInegiCode) throws IOException {
		List<Municipality> municipalities = new ArrayList<>();
		for (String[] row : readCsvDataRows(MUNICIPALITIES_CSV_LOCATION)) {
			String stateInegiCode = row[0];
			String inegiCode = row[1];
			String name = row[2];

			UUID stateId = stateIdByInegiCode.get(stateInegiCode);
			if (stateId == null) {
				throw new IllegalStateException(
						"Municipality row '" + name + "' references unknown state INEGI code: " + stateInegiCode);
			}

			municipalities.add(new Municipality(stateId, name, inegiCode));
		}

		List<Municipality> saved = municipalityRepository.saveAll(municipalities);
		log.info("Seeded {} municipalities from {}", saved.size(), MUNICIPALITIES_CSV_LOCATION);
	}

	/**
	 * Reads a classpath CSV resource, skipping the header row. Uses a
	 * minimal quote-aware line parser ({@link #parseCsvLine(String)}) rather
	 * than a naive {@code String.split(",")} — the municipios dump contains
	 * exactly one RFC-4180-quoted row with an embedded comma
	 * ("Heroica Villa Tezoatlán de Segura y Luna, Cuna de la Independencia de
	 * Oaxaca", Oaxaca state code 20, municipality code 549) which a plain
	 * split would silently mis-parse into 4 fields instead of 3.
	 */
	private List<String[]> readCsvDataRows(String classpathLocation) throws IOException {
		List<String[]> rows = new ArrayList<>();
		try (InputStream inputStream = new ClassPathResource(classpathLocation).getInputStream();
				BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
			reader.readLine(); // header
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				rows.add(parseCsvLine(line));
			}
		}
		return rows;
	}

	/**
	 * Minimal RFC-4180-aware CSV line parser: splits on unquoted commas,
	 * treats a double-quoted field's internal commas as literal, and unescapes
	 * doubled quotes ({@code ""} -> {@code "}). Sufficient for this dataset —
	 * no multi-line quoted fields exist in either seed CSV.
	 */
	private String[] parseCsvLine(String line) {
		List<String> fields = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean inQuotes = false;

		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (inQuotes) {
				if (c == '"') {
					boolean nextIsEscapedQuote = i + 1 < line.length() && line.charAt(i + 1) == '"';
					if (nextIsEscapedQuote) {
						current.append('"');
						i++;
					}
					else {
						inQuotes = false;
					}
				}
				else {
					current.append(c);
				}
			}
			else if (c == '"') {
				inQuotes = true;
			}
			else if (c == ',') {
				fields.add(current.toString());
				current.setLength(0);
			}
			else {
				current.append(c);
			}
		}
		fields.add(current.toString());

		return fields.toArray(new String[0]);
	}
}
