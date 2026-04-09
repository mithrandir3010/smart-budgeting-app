package com.mali.smartbudget.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class PdfService {

    /**
     * Gelen PDF dosyasının tüm metin içeriğini ham String olarak döner.
     *
     * @param file Kullanıcının yüklediği PDF ekstre dosyası
     * @return PDF içindeki düz metin
     * @throws IOException Dosya okunamadığında ya da geçerli bir PDF değilse
     */
    public String extractText(MultipartFile file) throws IOException {
        // PDFBox 3.x API: Loader.loadPDF() — eski PDDocument.load() kullanımı değişti
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }
}
