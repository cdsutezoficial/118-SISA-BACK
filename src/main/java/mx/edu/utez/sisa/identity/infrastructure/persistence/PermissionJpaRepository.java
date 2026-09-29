package mx.edu.utez.sisa.identity.infrastructure.persistence;

import mx.edu.utez.sisa.identity.domain.model.Permission;
import mx.edu.utez.sisa.identity.domain.model.PermissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PermissionJpaRepository extends JpaRepository<Permission, UUID> {

	Optional<Permission> findByKey(String key);

	List<Permission> findByIdIn(List<UUID> ids);

	boolean existsByKey(String key);

	boolean existsByKeyAndIdNot(String key, UUID id);

	@Query(value = """
			SELECT p FROM Permission p
			WHERE (:status IS NULL OR p.status = :status)
			  AND (:search IS NULL
			       OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(p.key) LIKE LOWER(CONCAT('%', :search, '%')))
			""", countQuery = """
			SELECT COUNT(p) FROM Permission p
			WHERE (:status IS NULL OR p.status = :status)
			  AND (:search IS NULL
			       OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(p.key) LIKE LOWER(CONCAT('%', :search, '%')))
			""")
	Page<Permission> search(@Param("status") PermissionStatus status, @Param("search") String search,
			Pageable pageable);
}