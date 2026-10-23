package br.com.saasfinder.catalog.infra;

import br.com.saasfinder.catalog.domain.Tag;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    Optional<Tag> findBySlug(String slug);

    List<Tag> findAllByOrderByNamePtAsc();
}
