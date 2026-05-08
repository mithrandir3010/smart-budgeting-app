package com.mali.smartbudget.repository;

import com.mali.smartbudget.model.PdfArchive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PdfArchiveRepository extends JpaRepository<PdfArchive, Long> {}
