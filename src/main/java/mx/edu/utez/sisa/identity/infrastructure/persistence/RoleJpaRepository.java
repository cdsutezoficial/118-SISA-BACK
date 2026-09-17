package mx.edu.utez.sisa.identity.infrastructure.persistence;

import mx.edu.utez.sisa.identity.domain.model.Role;
import mx.edu.utez.sisa.identity.domain.model.RoleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleJpaRepository extends JpaRepository<Role, UUID> {

	Optional<Role> findByKey(String key);

	List<Role> findByIdIn(List<UUID> ids);

	boolean existsByKey(String key);

	boolean existsByKeyAndIdNot(String key, UUID id);

	@Query(value = """
			SELECT r FROM Role r
			WHERE (:status IS NULL OR r.status = :status)
			  AND (:search IS NULL
			       OR LOWER(r.name) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(r.key) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(r.description) LIKE LOWER(CONCAT('%', :search, '%')))
			""", countQuery = """
			SELECT COUNT(r) FROM Role r
			WHERE (:status IS NULL OR r.status = :status)
			  AND (:search IS NULL
			       OR LOWER(r.name) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(r.key) LIKE LOWER(CONCAT('%', :search, '%'))
			       OR LOWER(r.description) LIKE LOWER(CONCAT('%', :search, '%')))
			""")
	Page<Role> search(@Param("status") RoleStatus status, @Param("search") String search, Pageable pageable);
}