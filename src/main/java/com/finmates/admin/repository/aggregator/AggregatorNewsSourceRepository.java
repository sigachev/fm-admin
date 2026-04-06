package com.finmates.admin.repository.aggregator;

import com.finmates.admin.entity.aggregator.AggregatorNewsSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AggregatorNewsSourceRepository extends JpaRepository<AggregatorNewsSource, Long> {

    Optional<AggregatorNewsSource> findByName(String name);
}
