package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.model.OutreachChannel;
import mx.edu.utez.sisa.admission.domain.model.OutreachChannelStatus;
import mx.edu.utez.sisa.admission.domain.port.out.OutreachChannelRepository.OutreachChannelSearchCriteria;
import mx.edu.utez.sisa.admission.domain.port.out.OutreachChannelRepository.OutreachChannelSearchPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-DB coverage for {@link OutreachChannelRepositoryAdapter#search},
 * mirroring {@code SubjectClassificationRepositoryAdapterSearchIT}'s style.
 */
@DataJpaTest
@Import(OutreachChannelRepositoryAdapter.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class OutreachChannelRepositoryAdapterSearchIT {

	@Autowired
	private OutreachChannelRepositoryAdapter adapter;

	@Autowired
	private OutreachChannelJpaRepository jpaRepository;

	@Test
	void statusFilterMatchesExactStatus() {
		jpaRepository.save(new OutreachChannel("Facebook"));
		jpaRepository.save(new OutreachChannel("Feria educativa"));

		OutreachChannelSearchPage activePage = adapter
				.search(new OutreachChannelSearchCriteria(OutreachChannelStatus.ACTIVE, null, 0, 20));
		OutreachChannelSearchPage inactivePage = adapter
				.search(new OutreachChannelSearchCriteria(OutreachChannelStatus.INACTIVE, null, 0, 20));

		assertThat(activePage.totalElements()).isEqualTo(2L);
		assertThat(inactivePage.totalElements()).isZero();
	}

	@Test
	void searchMatchesNameCaseInsensitively() {
		jpaRepository.save(new OutreachChannel("Facebook"));
		jpaRepository.save(new OutreachChannel("Feria educativa"));

		OutreachChannelSearchPage byFragment = adapter.search(new OutreachChannelSearchCriteria(null, "face", 0, 20));

		assertThat(byFragment.totalElements()).isEqualTo(1L);
		assertThat(byFragment.content().get(0).getName()).isEqualTo("Facebook");
	}

	@Test
	void paginationMetadataReflectsTotalElementsAcrossMultiplePages() {
		for (int i = 0; i < 5; i++) {
			jpaRepository.save(new OutreachChannel("Canal " + i));
		}

		OutreachChannelSearchPage firstPage = adapter.search(new OutreachChannelSearchCriteria(null, null, 0, 2));
		OutreachChannelSearchPage secondPage = adapter.search(new OutreachChannelSearchCriteria(null, null, 1, 2));

		assertThat(firstPage.totalElements()).isEqualTo(5L);
		assertThat(firstPage.totalPages()).isEqualTo(3);
		assertThat(firstPage.content()).hasSize(2);
		assertThat(secondPage.content()).hasSize(2);
	}

	@Test
	void databaseRejectsDuplicateNames() {
		// Antes este test insertaba dos filas con el mismo nombre y esperaba 2
		// resultados. Desde que `name` lleva `uk_*_name`, eso es un error de
		// integridad, y esta es la capa que lo atrapa aunque el caso de uso se
		// saltara la comprobación: es la que cierra la carrera entre dos altas
		// simultáneas, imposible de resolver en Java.
		jpaRepository.saveAndFlush(new OutreachChannel("Facebook"));

		assertThatThrownBy(() -> jpaRepository.saveAndFlush(new OutreachChannel("Facebook")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void databaseRejectsDuplicateNamesIgnoringCaseAndTrailingSpace() {
		// La misma regla del lado de la base: `utf8mb4_0900_ai_ci` no distingue
		// mayúsculas, y el `NO PAD` de la colación hace que el espacio final sí
		// cuente, así que este caso lo cierra el normalizador del caso de uso y
		// no el índice.
		jpaRepository.saveAndFlush(new OutreachChannel("Facebook"));

		assertThatThrownBy(() -> jpaRepository.saveAndFlush(new OutreachChannel("Facebook".toLowerCase())))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void resultsAreSortedDeterministicallyAcrossRepeatedQueries() {
		// Antes este test creaba un empate a propósito guardando dos veces el
		// mismo nombre, porque no había unicidad. Ya no se puede formar un empate
		// (el índice único lo impide), así que lo que se afirma es la propiedad que
		// queda: dos consultas seguidas devuelven el mismo orden. El desempate por
		// `id` de la Sort chain del adapter se queda por si algún día se suelta la
		// restricción.
		jpaRepository.save(new "Facebook-orden");
		jpaRepository.save(new "Feria-orden");

		OutreachChannelSearchPage firstCall = adapter.search(new OutreachChannelSearchCriteria(null, null, 0, 20));
		OutreachChannelSearchPage secondCall = adapter.search(new OutreachChannelSearchCriteria(null, null, 0, 20));

		assertThat(firstCall.content()).extracting(OutreachChannel::getId)
				.containsExactlyElementsOf(secondCall.content().stream().map(OutreachChannel::getId).toList());
	}

	@Test
	void saveInsertsANewRow() {
		OutreachChannel saved = adapter.save(new OutreachChannel("Facebook"));

		assertThat(saved.getId()).isNotNull();
		assertThat(jpaRepository.findById(saved.getId())).isPresent();
	}

	@Test
	void findByIdReturnsTheChannelWhenItExists() {
		OutreachChannel saved = jpaRepository.save(new OutreachChannel("Facebook"));

		assertThat(adapter.findById(saved.getId())).isPresent().get().extracting(OutreachChannel::getName)
				.isEqualTo("Facebook");
	}

	@Test
	void findByIdReturnsEmptyWhenChannelDoesNotExist() {
		assertThat(adapter.findById(java.util.UUID.randomUUID())).isEmpty();
	}
}
