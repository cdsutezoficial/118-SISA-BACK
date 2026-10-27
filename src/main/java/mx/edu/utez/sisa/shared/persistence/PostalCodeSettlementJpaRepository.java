package mx.edu.utez.sisa.shared.persistence;

import mx.edu.utez.sisa.shared.model.PostalCodeSettlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Plain Spring Data repository for the closed SEPOMEX postal-code catalog.
 */
public interface PostalCodeSettlementJpaRepository extends JpaRepository<PostalCodeSettlement, UUID> {

	List<PostalCodeSettlement> findByPostalCodeOrderBySettlementNameAsc(String postalCode);
}