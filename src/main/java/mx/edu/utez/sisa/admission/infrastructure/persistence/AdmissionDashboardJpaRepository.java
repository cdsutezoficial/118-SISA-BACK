package mx.edu.utez.sisa.admission.infrastructure.persistence;

import mx.edu.utez.sisa.admission.domain.model.Candidate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** Consultas de conteo del tablero de Admisión. Devuelve filas sueltas; el adaptador las tipa. */
public interface AdmissionDashboardJpaRepository extends Repository<Candidate, UUID> {

	@Query("SELECT p.id, p.name FROM AcademicPeriod p WHERE p.id = :periodId")
	List<Object[]> findPeriod(@Param("periodId") UUID periodId);

	@Query("""
			SELECT p.id, p.name FROM ProgramAdmissionConfig cfg, AcademicPeriod p
			WHERE p.id = cfg.periodId
			ORDER BY CASE WHEN cfg.status = mx.edu.utez.sisa.academic_config.domain.model.ProgramAdmissionConfigStatus.OPEN
			              THEN 0 ELSE 1 END,
			         cfg.opensAt DESC""")
	List<Object[]> findLatestAdmissionPeriods(Pageable pageable);

	@Query("""
			SELECT prog.id, prog.name, prog.modality, c.status, c.isEnabledForInduction, COUNT(c)
			FROM Candidate c, ProgramAdmissionConfig cfg, AcademicProgram prog
			WHERE cfg.id = c.admissionConfigId
			  AND prog.id = cfg.programId
			  AND cfg.periodId = :periodId
			  AND (:divisionId IS NULL OR prog.divisionId = :divisionId)
			GROUP BY prog.id, prog.name, prog.modality, c.status, c.isEnabledForInduction""")
	List<Object[]> countByProgramAndStatus(@Param("periodId") UUID periodId, @Param("divisionId") UUID divisionId);
}
