package mx.edu.utez.sisa.shared.bootstrap;

import mx.edu.utez.sisa.shared.model.Municipality;
import mx.edu.utez.sisa.shared.model.PostalCodeSettlement;
import mx.edu.utez.sisa.shared.model.State;
import mx.edu.utez.sisa.shared.persistence.MunicipalityJpaRepository;
import mx.edu.utez.sisa.shared.persistence.PostalCodeSettlementJpaRepository;
import mx.edu.utez.sisa.shared.persistence.StateJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Bootstraps the closed SEPOMEX postal-code catalog on startup.
 */
@Component
@Order(2)
public class PostalCodeSettlementSeedRunner implements ApplicationRunner {

	private static final int BATCH_SIZE = 1000;

	private static final Logger log = LoggerFactory.getLogger(PostalCodeSettlementSeedRunner.class);

	private static final String POSTAL_CODES_CSV_LOCATION = "seed/sepomex_postal_codes.csv";

	private final PostalCodeSettlementJpaRepository postalCodeSettlementRepository;

	private final StateJpaRepository stateRepository;

	private final MunicipalityJpaRepository municipalityRepository;

	public PostalCodeSettlementSeedRunner(PostalCodeSettlementJpaRepository postalCodeSettlementRepository,
			StateJpaRepository stateRepository, MunicipalityJpaRepository municipalityRepository) {
		this.postalCodeSettlementRepository = postalCodeSettlementRepository;
		this.stateRepository = stateRepository;
		this.municipalityRepository = municipalityRepository;
	}

	@Override
	public void run(ApplicationArguments args) throws IOException {
		if (postalCodeSettlementRepository.count() > 0) {
			log.info("El catálogo de códigos postales ya está cargado; se omite el sembrado de SEPOMEX");
			return;
		}

		int persistedCount = 0;
		int skippedCount = 0;
		int malformedCount = 0;
		Set<String> skippedKeyExamples = new LinkedHashSet<>();
		List<PostalCodeSettlement> batch = new ArrayList<>(BATCH_SIZE);

		log.info("Iniciando sembrado de códigos postales SEPOMEX desde {} por lotes de {}", POSTAL_CODES_CSV_LOCATION,
				BATCH_SIZE);

		try (InputStream inputStream = new ClassPathResource(POSTAL_CODES_CSV_LOCATION).getInputStream();
				BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
			reader.readLine();
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}

				String[] row = parseCsvLine(line);
				if (row.length < 7) {
					malformedCount++;
					continue;
				}

				String postalCode = row[0];
				String settlementName = row[1];
				String localityName = row[3].isBlank() ? null : row[3];
				String stateInegiCode = row[4];
				String municipalityInegiCode = row[5];
				String sepomexSettlementId = row[6];

				State state = stateRepository.findByInegiCode(stateInegiCode).orElse(null);
				if (state == null) {
					skippedCount++;
					rememberSkippedExample(skippedKeyExamples, "state:" + stateInegiCode);
					continue;
				}

				Municipality municipality = municipalityRepository.findByStateIdAndInegiCode(state.getId(), municipalityInegiCode)
						.orElse(null);
				if (municipality == null) {
					skippedCount++;
					rememberSkippedExample(skippedKeyExamples,
							"municipality:" + stateInegiCode + "|" + municipalityInegiCode);
					continue;
				}

				batch.add(new PostalCodeSettlement(postalCode, settlementName, localityName, state.getId(),
						municipality.getId(), sepomexSettlementId));
				if (batch.size() == BATCH_SIZE) {
					persistedCount += persistBatch(batch);
					log.info("Avance del sembrado SEPOMEX: {} asentamientos guardados", persistedCount);
				}
			}
		}

		if (!batch.isEmpty()) {
			persistedCount += persistBatch(batch);
		}

		log.info("Se sembraron {} asentamientos de códigos postales desde {}", persistedCount,
				POSTAL_CODES_CSV_LOCATION);
		if (skippedCount > 0) {
			log.warn("Se omitieron {} filas de códigos postales porque los catálogos INEGI cargados en tiempo de ejecución no contienen sus claves de estado/municipio. Ejemplos: {}",
					skippedCount, skippedKeyExamples);
		}
		if (malformedCount > 0) {
			log.warn("Se omitieron {} filas de códigos postales con formato inválido", malformedCount);
		}
	}

	private int persistBatch(List<PostalCodeSettlement> batch) {
		int savedCount = postalCodeSettlementRepository.saveAllAndFlush(batch).size();
		batch.clear();
		return savedCount;
	}

	private void rememberSkippedExample(Set<String> skippedKeyExamples, String skippedKey) {
		if (skippedKeyExamples.size() < 5) {
			skippedKeyExamples.add(skippedKey);
		}
	}

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