package mx.edu.utez.sisa.audit.infrastructure.persistence;

import mx.edu.utez.sisa.audit.domain.model.AuditEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface AuditEntryJpaRepository extends JpaRepository<AuditEntry, UUID> {

	@Query("""
			select e from AuditEntry e
			where (:from is null or e.occurredAt >= :from)
			  and (:to is null or e.occurredAt < :to)
			  and (:actorId is null or e.actorId = :actorId)
			  and (:action is null or e.action = :action)
			  and (:entityType is null or e.entityType = :entityType)
			  and (:entityId is null or e.entityId = :entityId)
			order by e.occurredAt desc, e.field asc
			""")
	Page<AuditEntry> search(@Param("from") Instant from, @Param("to") Instant to, @Param("actorId") UUID actorId,
			@Param("action") String action, @Param("entityType") String entityType, @Param("entityId") UUID entityId,
			Pageable pageable);
}
