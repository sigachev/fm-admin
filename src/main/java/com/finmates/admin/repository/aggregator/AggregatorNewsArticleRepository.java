package com.finmates.admin.repository.aggregator;

import com.finmates.admin.entity.aggregator.AggregatorNewsArticle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AggregatorNewsArticleRepository extends JpaRepository<AggregatorNewsArticle, Long> {

    Optional<AggregatorNewsArticle> findBySourceIdAndExternalId(Long sourceId, String externalId);

    void deleteBySourceIdAndExternalId(Long sourceId, String externalId);
}
