package com.finmates.admin.service;

import com.finmates.admin.dto.AdminNewsDto;
import com.finmates.admin.dto.CreateNewsRequest;
import com.finmates.admin.entity.aggregator.AggregatorNewsArticle;
import com.finmates.admin.entity.main.AdminNewsArticle;
import com.finmates.admin.repository.aggregator.AggregatorNewsArticleRepository;
import com.finmates.admin.repository.aggregator.AggregatorNewsSourceRepository;
import com.finmates.admin.repository.main.AdminNewsRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminNewsService {

    private static final String FINMATES_SOURCE_NAME = "FinMates";

    private final AdminNewsRepository newsRepository;
    private final AggregatorNewsSourceRepository aggregatorNewsSourceRepo;
    private final AggregatorNewsArticleRepository aggregatorNewsArticleRepo;

    /** Cached source ID of the "FinMates" row in news_source. Resolved on startup. */
    private Long finmatesSourceId;

    @PostConstruct
    void init() {
        try {
            aggregatorNewsSourceRepo.findByName(FINMATES_SOURCE_NAME)
                .ifPresentOrElse(
                    s -> {
                        finmatesSourceId = s.getId();
                        log.info("FinMates aggregator source resolved: id={}", finmatesSourceId);
                    },
                    () -> log.warn("FinMates source not found in news_source table — admin news will not appear in public feed. Run V5 migration.")
                );
        } catch (Exception e) {
            log.error("Failed to resolve FinMates aggregator source: {}", e.getMessage());
        }
    }

    public Page<AdminNewsDto> listNews(Pageable pageable) {
        return newsRepository.findAllByOrderByPublishedAtDesc(pageable).map(this::toDto);
    }

    @Transactional("mainTransactionManager")
    public AdminNewsDto create(CreateNewsRequest req, Long createdBy) {
        AdminNewsArticle article = new AdminNewsArticle();
        article.setTitle(req.getTitle());
        article.setSummary(req.getSummary());
        article.setContent(req.getContent());
        article.setUrl(req.getUrl());
        article.setSource(req.getSource() != null ? req.getSource() : "FinMates");
        article.setBreaking(req.isBreaking());
        article.setSymbols(symbolsToString(req.getSymbols()));
        article.setCreatedBy(createdBy);
        AdminNewsArticle saved = newsRepository.save(article);
        mirrorToAggregator(saved);
        return toDto(saved);
    }

    @Transactional("mainTransactionManager")
    public AdminNewsDto update(Long id, CreateNewsRequest req) {
        AdminNewsArticle article = newsRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found: " + id));
        if (req.getTitle() != null) article.setTitle(req.getTitle());
        if (req.getSummary() != null) article.setSummary(req.getSummary());
        if (req.getContent() != null) article.setContent(req.getContent());
        if (req.getUrl() != null) article.setUrl(req.getUrl());
        if (req.getSource() != null) article.setSource(req.getSource());
        article.setBreaking(req.isBreaking());
        if (req.getSymbols() != null) article.setSymbols(symbolsToString(req.getSymbols()));
        AdminNewsArticle saved = newsRepository.save(article);
        upsertInAggregator(saved);
        return toDto(saved);
    }

    @Transactional("mainTransactionManager")
    public void delete(Long id) {
        if (!newsRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found: " + id);
        }
        newsRepository.deleteById(id);
        deleteFromAggregator(id);
    }

    // ── Aggregator mirroring (best-effort, does not affect main transaction) ──

    private void mirrorToAggregator(AdminNewsArticle article) {
        if (finmatesSourceId == null) return;
        try {
            AggregatorNewsArticle agg = toAggregatorArticle(article);
            aggregatorNewsArticleRepo.save(agg);
        } catch (Exception e) {
            log.warn("Failed to mirror article {} to aggregator DB: {}", article.getId(), e.getMessage());
        }
    }

    private void upsertInAggregator(AdminNewsArticle article) {
        if (finmatesSourceId == null) return;
        try {
            String externalId = String.valueOf(article.getId());
            AggregatorNewsArticle agg = aggregatorNewsArticleRepo
                .findBySourceIdAndExternalId(finmatesSourceId, externalId)
                .orElseGet(AggregatorNewsArticle::new);
            populateAggregatorArticle(agg, article);
            aggregatorNewsArticleRepo.save(agg);
        } catch (Exception e) {
            log.warn("Failed to upsert article {} in aggregator DB: {}", article.getId(), e.getMessage());
        }
    }

    private void deleteFromAggregator(Long adminArticleId) {
        if (finmatesSourceId == null) return;
        try {
            aggregatorNewsArticleRepo.deleteBySourceIdAndExternalId(
                finmatesSourceId, String.valueOf(adminArticleId));
        } catch (Exception e) {
            log.warn("Failed to delete article {} from aggregator DB: {}", adminArticleId, e.getMessage());
        }
    }

    private AggregatorNewsArticle toAggregatorArticle(AdminNewsArticle article) {
        AggregatorNewsArticle agg = new AggregatorNewsArticle();
        populateAggregatorArticle(agg, article);
        return agg;
    }

    private void populateAggregatorArticle(AggregatorNewsArticle agg, AdminNewsArticle article) {
        agg.setSourceId(finmatesSourceId);
        agg.setExternalId(String.valueOf(article.getId()));
        agg.setTitle(article.getTitle());
        agg.setSummary(article.getSummary());
        agg.setContent(article.getContent());
        agg.setUrl(article.getUrl());
        agg.setAssetSymbols(article.getSymbols());
        agg.setImportant(article.isBreaking());
        agg.setFetchedAt(Instant.now());
        Instant published = article.getPublishedAt() != null
            ? article.getPublishedAt().toInstant()
            : Instant.now();
        agg.setPublishedAt(published);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String symbolsToString(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) return null;
        return String.join(",", symbols);
    }

    private List<String> symbolsFromString(String symbols) {
        if (symbols == null || symbols.isBlank()) return Collections.emptyList();
        return Arrays.stream(symbols.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private AdminNewsDto toDto(AdminNewsArticle a) {
        AdminNewsDto dto = new AdminNewsDto();
        dto.setId(a.getId());
        dto.setTitle(a.getTitle());
        dto.setSummary(a.getSummary());
        dto.setContent(a.getContent());
        dto.setUrl(a.getUrl());
        dto.setSource(a.getSource());
        dto.setBreaking(a.isBreaking());
        dto.setSymbols(symbolsFromString(a.getSymbols()));
        dto.setPublishedAt(a.getPublishedAt());
        dto.setCreatedBy(a.getCreatedBy());
        dto.setCreatedAt(a.getCreatedAt());
        return dto;
    }
}
