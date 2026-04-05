package com.finmates.admin.service;

import com.finmates.admin.dto.AdminNewsDto;
import com.finmates.admin.dto.CreateNewsRequest;
import com.finmates.admin.entity.main.AdminNewsArticle;
import com.finmates.admin.repository.main.AdminNewsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminNewsService {

    private final AdminNewsRepository newsRepository;

    public Page<AdminNewsDto> listNews(Pageable pageable) {
        return newsRepository.findAllByOrderByPublishedAtDesc(pageable).map(this::toDto);
    }

    @Transactional("mainTransactionManager")
    public AdminNewsDto create(CreateNewsRequest req, Long createdBy) {
        AdminNewsArticle article = new AdminNewsArticle();
        article.setTitle(req.getTitle());
        article.setSummary(req.getSummary());
        article.setUrl(req.getUrl());
        article.setSource(req.getSource() != null ? req.getSource() : "FinMates");
        article.setBreaking(req.isBreaking());
        article.setSymbols(symbolsToString(req.getSymbols()));
        article.setCreatedBy(createdBy);
        return toDto(newsRepository.save(article));
    }

    @Transactional("mainTransactionManager")
    public AdminNewsDto update(Long id, CreateNewsRequest req) {
        AdminNewsArticle article = newsRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found: " + id));
        if (req.getTitle() != null) article.setTitle(req.getTitle());
        if (req.getSummary() != null) article.setSummary(req.getSummary());
        if (req.getUrl() != null) article.setUrl(req.getUrl());
        if (req.getSource() != null) article.setSource(req.getSource());
        article.setBreaking(req.isBreaking());
        if (req.getSymbols() != null) article.setSymbols(symbolsToString(req.getSymbols()));
        return toDto(newsRepository.save(article));
    }

    @Transactional("mainTransactionManager")
    public void delete(Long id) {
        if (!newsRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found: " + id);
        }
        newsRepository.deleteById(id);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
