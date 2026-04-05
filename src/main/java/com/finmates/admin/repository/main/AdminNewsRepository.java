package com.finmates.admin.repository.main;

import com.finmates.admin.entity.main.AdminNewsArticle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminNewsRepository extends JpaRepository<AdminNewsArticle, Long> {

    Page<AdminNewsArticle> findAllByOrderByPublishedAtDesc(Pageable pageable);
}
