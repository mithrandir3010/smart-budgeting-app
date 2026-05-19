package com.mali.smartbudget.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "pdf_archive")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PdfArchive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bank_name", length = 50)
    private String bankName;

    @Column(name = "upload_date", nullable = false)
    private LocalDate uploadDate;

    @Column(name = "pdf_content", columnDefinition = "bytea", nullable = false)
    private byte[] pdfContent;

    @Column(name = "header_text", columnDefinition = "TEXT")
    private String headerText;
}
