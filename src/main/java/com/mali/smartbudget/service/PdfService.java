package com.mali.smartbudget.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@Service
public class PdfService {

    public String extractText(MultipartFile file) throws IOException {
        log.info("PdfService: dosya alındı. Ad='{}', boyut={} byte, contentType='{}'",
                file.getOriginalFilename(), file.getSize(), file.getContentType());

        if (file.isEmpty()) {
            log.error("PdfService: dosya içeriği boş (0 byte).");
            throw new IllegalArgumentException("Yüklenen dosya boş.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
            log.info("PdfService: {} byte okundu.", bytes.length);
        } catch (IOException e) {
            log.error("PdfService: file.getBytes() başarısız: {}", e.getMessage(), e);
            throw e;
        }

        PDDocument document;
        try {
            document = Loader.loadPDF(bytes);
            log.info("PdfService: PDDocument yüklendi. Sayfa sayısı: {}", document.getNumberOfPages());
        } catch (IOException e) {
            log.error("PdfService: PDF yüklenemedi (geçerli bir PDF olmayabilir): {}", e.getMessage(), e);
            throw new IOException("Geçerli bir PDF dosyası değil: " + e.getMessage(), e);
        }

        try (document) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            log.info("PdfService: metin çıkarıldı. {} karakter.", text.length());
            return text;
        } catch (IOException e) {
            log.error("PdfService: metin çıkarma başarısız: {}", e.getMessage(), e);
            throw e;
        }
    }
}
