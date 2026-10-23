package br.com.saasfinder.reviews.infra;

import br.com.saasfinder.reviews.domain.ReviewVerification;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewVerificationRepository extends JpaRepository<ReviewVerification, UUID> {

    /**
     * A busca da confirmacao e pelo hash (plano DT-04) - o token em claro nao esta
     * no banco, entao nao ha por onde procurar por ele.
     */
    Optional<ReviewVerification> findByTokenHash(String tokenHash);
}
