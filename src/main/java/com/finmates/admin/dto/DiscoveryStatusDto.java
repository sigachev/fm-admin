package com.finmates.admin.dto;

import java.time.Instant;
import java.util.Map;

public record DiscoveryStatusDto(
    boolean running,
    Instant startedAt,
    Instant completedAt,
    Map<String, Integer> results,
    String error
) {}
