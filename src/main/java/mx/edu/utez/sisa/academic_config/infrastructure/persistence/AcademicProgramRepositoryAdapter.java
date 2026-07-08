package mx.edu.utez.sisa.academic_config.infrastructure.persistence;

import mx.edu.utez.sisa.academic_config.domain.model.AcademicProgram;
import mx.edu.utez.sisa.academic_config.domain.port.out.AcademicProgramRepository;
import mx.edu.utez.sisa.shared.model.ProgramModality;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed {@link AcademicProgramRepository} adapter delegating to
 * {@link AcademicProgramJpaRepository}. Results are sorted by {@code name}
 * ascending (design.md — File Changes: "JPA repo+adapter (sort by name
 * ASC)"), mirroring {@code AcademicDivisionRepositoryAdapter}.
 */
@Component
public class AcademicProgramRepositoryAdapter implements AcademicProgramRepository {

	private final AcademicProgramJpaRepository jpaRepository;

	public AcademicProgramRepositoryAdapter(AcademicProgramJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public AcademicProgram save(AcademicProgram program) {
		return jpaRepository.save(program);
	}

	@Override
	public Optional<AcademicProgram> findById(UUID id) {
		return jpaRepository.findById(id);
	}

	@Override
	public Optional<AcademicProgram> findByCode(String code) {
		return jpaRepository.findByCode(code);
	}

	@Override
	public Optional<AcademicProgram> findByOfferNameAndModality(String offerName, ProgramModality modality) {
		return jpaRepository.findByOfferNameAndModality(offerName, modality);
	}

	@Override
	public ProgramSearchPage search(ProgramSearchCriteria criteria) {
		PageRequest pageRequest = PageRequest.of(criteria.page(), criteria.size(),
				Sort.by(Sort.Direction.ASC, "name"));
		Page<AcademicProgram> page = jpaRepository.search(criteria.divisionId(), criteria.status(), criteria.search(),
				pageRequest);
		return new ProgramSearchPage(page.getContent(), page.getTotalElements(), page.getTotalPages());
	}
}
