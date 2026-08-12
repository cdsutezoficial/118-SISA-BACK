package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.Group;
import mx.edu.utez.sisa.academic_config.domain.model.GroupStatus;
import mx.edu.utez.sisa.academic_config.domain.port.out.GroupRepository.GroupSearchCriteria;
import mx.edu.utez.sisa.academic_config.domain.port.out.GroupRepository.GroupSearchPage;
import mx.edu.utez.sisa.shared.model.Shift;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB (H2) coverage for {@link GroupRepositoryAdapter#search}, mirroring
 * {@code GenerationRepositoryAdapterSearchIT}'s style.
 */
@DataJpaTest
@Import(GroupRepositoryAdapter.class)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class GroupRepositoryAdapterSearchIT {

	@Autowired
	private GroupRepositoryAdapter adapter;

	@Autowired
	private GroupJpaRepository jpaRepository;

	@Test
	void statusFilterMatchesExactStatus() {
		UUID programId = UUID.randomUUID();
		jpaRepository.save(newGroup(programId, UUID.randomUUID(), "1A"));
		Group closed = jpaRepository.save(newGroup(programId, UUID.randomUUID(), "1B"));
		closed.close();
		jpaRepository.save(closed);

		GroupSearchPage openPage = adapter.search(new GroupSearchCriteria(GroupStatus.OPEN, null, null, null, 0, 20));
		GroupSearchPage closedPage = adapter.search(new GroupSearchCriteria(GroupStatus.CLOSED, null, null, null, 0, 20));

		assertThat(openPage.totalElements()).isEqualTo(1L);
		assertThat(closedPage.totalElements()).isEqualTo(1L);
	}

	@Test
	void searchMatchesCodeCaseInsensitively() {
		UUID programId = UUID.randomUUID();
		jpaRepository.save(newGroup(programId, UUID.randomUUID(), "3A"));
		jpaRepository.save(newGroup(programId, UUID.randomUUID(), "3B"));

		GroupSearchPage byFragment = adapter.search(new GroupSearchCriteria(null, "3a", null, null, 0, 20));

		assertThat(byFragment.totalElements()).isEqualTo(1L);
		assertThat(byFragment.content().get(0).getCode()).isEqualTo("3A");
	}

	@Test
	void programIdFilterMatchesOnlyGroupsOfThatProgram() {
		UUID programA = UUID.randomUUID();
		UUID programB = UUID.randomUUID();
		jpaRepository.save(newGroup(programA, UUID.randomUUID(), "1A"));
		jpaRepository.save(newGroup(programA, UUID.randomUUID(), "1B"));
		jpaRepository.save(newGroup(programB, UUID.randomUUID(), "1A"));

		GroupSearchPage programAPage = adapter.search(new GroupSearchCriteria(null, null, programA, null, 0, 20));
		GroupSearchPage programBPage = adapter.search(new GroupSearchCriteria(null, null, programB, null, 0, 20));

		assertThat(programAPage.totalElements()).isEqualTo(2L);
		assertThat(programBPage.totalElements()).isEqualTo(1L);
	}

	@Test
	void generationIdFilterMatchesOnlyGroupsOfThatGeneration() {
		UUID programId = UUID.randomUUID();
		UUID generationA = UUID.randomUUID();
		UUID generationB = UUID.randomUUID();
		jpaRepository.save(newGroup(programId, generationA, "1A"));
		jpaRepository.save(newGroup(programId, generationA, "1B"));
		jpaRepository.save(newGroup(programId, generationB, "1A"));

		GroupSearchPage generationAPage = adapter.search(new GroupSearchCriteria(null, null, null, generationA, 0, 20));
		GroupSearchPage generationBPage = adapter.search(new GroupSearchCriteria(null, null, null, generationB, 0, 20));

		assertThat(generationAPage.totalElements()).isEqualTo(2L);
		assertThat(generationBPage.totalElements()).isEqualTo(1L);
	}

	@Test
	void paginationMetadataReflectsTotalElementsAcrossMultiplePages() {
		UUID programId = UUID.randomUUID();
		for (int i = 1; i <= 5; i++) {
			jpaRepository.save(newGroup(programId, UUID.randomUUID(), "G" + i));
		}

		GroupSearchPage firstPage = adapter.search(new GroupSearchCriteria(null, null, null, null, 0, 2));
		GroupSearchPage secondPage = adapter.search(new GroupSearchCriteria(null, null, null, null, 1, 2));

		assertThat(firstPage.totalElements()).isEqualTo(5L);
		assertThat(firstPage.totalPages()).isEqualTo(3);
		assertThat(firstPage.content()).hasSize(2);
		assertThat(secondPage.content()).hasSize(2);
	}

	@Test
	void resultsAreSortedByCodeAscending() {
		UUID programId = UUID.randomUUID();
		jpaRepository.save(newGroup(programId, UUID.randomUUID(), "3B"));
		jpaRepository.save(newGroup(programId, UUID.randomUUID(), "3A"));

		GroupSearchPage page = adapter.search(new GroupSearchCriteria(null, null, null, null, 0, 20));

		assertThat(page.content()).extracting(Group::getCode).containsExactly("3A", "3B");
	}

	@Test
	void saveInsertsANewRow() {
		Group saved = adapter.save(newGroup(UUID.randomUUID(), UUID.randomUUID(), "1A"));

		assertThat(saved.getId()).isNotNull();
		assertThat(jpaRepository.findById(saved.getId())).isPresent();
	}

	@Test
	void findByIdReturnsTheGroupWhenItExists() {
		Group saved = jpaRepository.save(newGroup(UUID.randomUUID(), UUID.randomUUID(), "1A"));

		assertThat(adapter.findById(saved.getId())).isPresent().get().extracting(Group::getCode).isEqualTo("1A");
	}

	@Test
	void findByIdReturnsEmptyWhenGroupDoesNotExist() {
		assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
	}

	private static Group newGroup(UUID programId, UUID generationId, String code) {
		return new Group(generationId, UUID.randomUUID(), UUID.randomUUID(), programId, code, 35, Shift.MORNING);
	}
}
