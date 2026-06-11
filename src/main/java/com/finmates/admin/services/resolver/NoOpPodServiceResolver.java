package com.finmates.admin.services.resolver;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * Phase 1a default: every lookup returns empty. The dashboard endpoints still
 * work — per-service connection counts come back null, cluster-wide rows show
 * IPs with a null serviceName.
 *
 * <p>Phase 1b adds the real k8s-API-backed implementation, which can either be
 * marked {@code @Primary} or annotated with
 * {@code @ConditionalOnMissingBean(PodServiceResolver.class)} to replace this
 * bean.
 */
@Component
public class NoOpPodServiceResolver implements PodServiceResolver {

    @Override
    public Optional<String> getServiceNameForPodIp(String podIp) {
        return Optional.empty();
    }

    @Override
    public Set<String> getPodIpsForService(String serviceName) {
        return Collections.emptySet();
    }
}
