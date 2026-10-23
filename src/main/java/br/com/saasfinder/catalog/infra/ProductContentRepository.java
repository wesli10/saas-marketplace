package br.com.saasfinder.catalog.infra;

import br.com.saasfinder.catalog.domain.ProductContent;
import br.com.saasfinder.catalog.domain.ProductContentId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductContentRepository extends JpaRepository<ProductContent, ProductContentId> {}
