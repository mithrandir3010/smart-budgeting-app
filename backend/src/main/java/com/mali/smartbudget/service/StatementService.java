package com.mali.smartbudget.service;

import com.mali.smartbudget.dto.ExtractionResult;
import com.mali.smartbudget.dto.TransactionDto;
import com.mali.smartbudget.dto.UploadResponseDto;
import com.mali.smartbudget.exception.DuplicateStatementException;
import com.mali.smartbudget.model.PdfArchive;
import com.mali.smartbudget.model.Statement;
import com.mali.smartbudget.model.StatementStatus;
import com.mali.smartbudget.model.Transaction;
import com.mali.smartbudget.model.User;
import com.mali.smartbudget.repository.BudgetLimitRepository;
import com.mali.smartbudget.repository.PdfArchiveRepository;
import com.mali.smartbudget.repository.StatementRepository;
import com.mali.smartbudget.repository.UserRepository;
import com.mali.smartbudget.util.ChecksumUtil;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StopWatch;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Ekstre yükleme iş akışını tek bir @Transactional sınır içinde yönetir.
 *
 * <h3>processUpload() Adımları</h3>
 * <pre>
 *  1. SHA-256 hash hesapla (ChecksumUtil)
 *  2. Hash mükerrerlik kontrolü → 409 DuplicateStatementException
 *  3. ExtractionService.extractDtos() — saf I/O, hiç DB yazma yok
 *       ↳ PDF okunur, LLM çağrılır, DTO listesi döner
 *  4. Dönem (periodStart/periodEnd) hesapla
 *  5. Dönem çakışma kontrolü → 409 DuplicateStatementException
 *  6. Statement metadata'yı kaydet (ID gerekli — transaction FK için)
 *  7. Transaction'ları statement referansıyla ekle (eski veriler silinmez)
 * </pre>
 *
 * <h3>Neden bu sıra?</h3>
 * Eski tasarımda delete+save adım 3'te yapılıyordu; adım 5'te conflict
 * fırlarsa rollback, kullanıcı ekranında "eski veri" görünüyordu.
 * Yeni tasarımda tüm doğrulamalar tamamlandıktan SONRA DB'ye yazılıyor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatementService {

    private final StatementRepository   statementRepository;
    private final UserRepository        userRepository;
    private final ExtractionService     extractionService;
    private final TransactionService    transactionService;
    private final BudgetLimitRepository budgetLimitRepository;
    private final PdfArchiveRepository  pdfArchiveRepository;
    private final AuditService          auditService;

    /**
     * PDF ekstreyi işler: mükerrerlik kontrolü → ayıklama → dönem kontrolü → kayıt.
     *
     * @param file     Kullanıcının yüklediği PDF
     * @param userId   Sahip kullanıcı ID'si
     * @param fileName Orijinal dosya adı (Statement kaydında saklanır)
     * @return Kullanıcıya gösterilecek başarı mesajı
     * @throws IOException                  PDF okunamazsa
     * @throws DuplicateStatementException  Hash veya dönem mükerreri varsa (→ 409)
     * @throws EntityNotFoundException      Kullanıcı bulunamazsa (→ 404)
     */
    @Transactional
    public UploadResponseDto processUpload(MultipartFile file, Long userId, String fileName) throws IOException {

        StopWatch uploadSw = new StopWatch("processUpload");
        uploadSw.start("total");

        // ── Adım 1: SHA-256 ───────────────────────────────────────────────────
        byte[] bytes    = file.getBytes();
        String checksum = ChecksumUtil.sha256(bytes);
        log.info("[Upload 1/6] SHA-256 hesaplandı: {}... | dosya={}", checksum.substring(0, 12), fileName);

        // ── Adım 2: Hash mükerrerlik kontrolü ────────────────────────────────
        if (statementRepository.existsByUserIdAndSha256Checksum(userId, checksum)) {
            log.warn("[Upload 2/6] HASH MÜKERRERİ — userId={}, checksum={}...", userId, checksum.substring(0, 8));
            throw new DuplicateStatementException(
                    "Bu dosya daha önce yüklendi. Aynı PDF içeriği sistemde zaten kayıtlı.",
                    DuplicateStatementException.Type.HASH, null, null
            );
        }
        log.info("[Upload 2/6] Hash kontrolü geçildi.");

        // ── Adım 3: PDF ayıklama — saf I/O, DB'ye hiçbir şey yazılmıyor ──────
        log.info("[Upload 3/6] Extraction başlıyor (saf I/O)...");
        ExtractionResult extraction = extractionService.extractAll(file);
        List<TransactionDto> dtos      = extraction.dtos();
        String bankName                = extraction.bankName();
        String headerText              = extraction.headerText();
        String maskedCardNo            = extraction.maskedCardNo();
        java.time.LocalDate cutDate    = extraction.statementCutDate();
        log.info("[Upload 3/6] {} DTO ayıklandı. banka={} | kart={} | kesim={}",
                dtos.size(), bankName, maskedCardNo, cutDate);

        // ── Adım 4: Dönem hesaplama ───────────────────────────────────────────
        Optional<LocalDate> periodStart = dtos.stream()
                .map(TransactionDto::date)
                .min(Comparator.naturalOrder());
        Optional<LocalDate> periodEnd = dtos.stream()
                .map(TransactionDto::date)
                .max(Comparator.naturalOrder());

        if (periodStart.isPresent()) {
            log.info("[Upload 4/6] Dönem: {} – {}", periodStart.get(), periodEnd.get());
        } else {
            log.warn("[Upload 4/6] İşlem tarihi bulunamadı — dönem boş.");
        }

        // ── Adım 5: Kart parmak izi mükerrerlik kontrolü ─────────────────────
        // maskedCardNo + statementCutDate + bankName üçü de mevcutsa tam eşleşme kontrolü.
        // Herhangi biri null ise bu kontrol atlanır — hash koruması yeterli kabul edilir.
        if (maskedCardNo != null && cutDate != null && bankName != null) {
            boolean exists = statementRepository.existsByCardFingerprint(
                    userId, bankName, maskedCardNo, cutDate);
            if (exists) {
                log.warn("[Upload 5/6] KART MÜKERRERİ — userId={}, banka={}, kart={}, kesim={}",
                        userId, bankName, maskedCardNo, cutDate);
                throw new DuplicateStatementException(
                        "Bu karta ait ekstre zaten yüklü! (%s · Kesim: %s)"
                                .formatted(maskedCardNo, cutDate),
                        DuplicateStatementException.Type.CARD_PERIOD,
                        cutDate, cutDate
                );
            }
        } else {
            log.info("[Upload 5/6] Kart parmak izi eksik (kart={} kesim={}) — mükerrer kontrolü atlandı.",
                    maskedCardNo, cutDate);
        }

        // ── Adım 6: Statement kaydet → transaction'ları statement referansıyla ekle ──
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Kullanıcı bulunamadı: " + userId));

        Statement statement = Statement.builder()
                .user(user)
                .fileName(fileName)
                .sha256Checksum(checksum)
                .uploadDate(LocalDate.now())
                .periodStart(periodStart.orElse(null))
                .periodEnd(periodEnd.orElse(null))
                .bankName(bankName)
                .maskedCardNo(maskedCardNo)
                .statementCutDate(cutDate)
                .status(StatementStatus.PROCESSED)
                .build();
        statementRepository.save(statement);
        log.info("[Upload 6/6] Statement kaydedildi. id={}", statement.getId());

        List<Transaction> transactions = dtos.stream()
                .map(dto -> Transaction.builder()
                        .user(user)
                        .statement(statement)
                        .date(dto.date())
                        .description(dto.description())
                        .amount(dto.amount())
                        .category(dto.category())
                        .categoryEnum(dto.categoryEnum())
                        .currency(dto.currency())
                        .isSubscription(dto.isSubscription())
                        .isInstallment(dto.isInstallment())
                        .currentInstallment(dto.currentInstallment())
                        .totalInstallments(dto.totalInstallments())
                        .build())
                .toList();
        List<Transaction> saved = transactionService.saveAllTransactions(transactions);
        log.info("[Upload 6/6] {} adet Transaction kaydedildi.", saved.size());

        // ── Anonim PDF arşivi — kullanıcıdan bağımsız, pipeline geliştirme için ─
        pdfArchiveRepository.save(PdfArchive.builder()
                .bankName(bankName)
                .uploadDate(LocalDate.now())
                .pdfContent(bytes)
                .headerText(headerText)
                .build());
        log.info("[Upload 6/6] PDF arşivlendi (anonim).");

        // ── Başarı mesajı ─────────────────────────────────────────────────────
        uploadSw.stop();
        auditService.statementUploaded(userId, fileName);
        log.info("[Upload] Tamamlandı. userId={}, dosya='{}', {} işlem | toplam süre={}ms",
                userId, fileName, saved.size(), uploadSw.getTotalTimeMillis());

        String msg = "%d işlem başarıyla işlendi".formatted(saved.size());
        if (periodStart.isPresent()) {
            msg += " (%s – %s dönemi)".formatted(periodStart.get(), periodEnd.get());
        }
        return new UploadResponseDto(msg + ".", bankName);
    }

    /**
     * Kullanıcıya ait tüm işlemleri ve ekstre kayıtlarını siler.
     *
     * <p>Önce Transaction'lar silinir (FK bağımlılığı),
     * ardından Statement'lar silinir.
     *
     * @param userId Verileri silinecek kullanıcı ID'si
     */
    @Transactional
    public void deleteAllData(Long userId) {
        transactionService.deleteAllByUserId(userId);
        statementRepository.deleteAllByUserId(userId);
        budgetLimitRepository.deleteAllByUserId(userId);
        auditService.statementDeleted(userId);
        log.info("Tüm veriler silindi (transaction + statement + budget limits). userId={}", userId);
    }

    @Transactional(readOnly = true)
    public List<Statement> getProcessedStatements(Long userId) {
        return statementRepository.findByUserIdAndStatus(userId, StatementStatus.PROCESSED)
                .stream()
                .sorted(Comparator.comparing(Statement::getUploadDate))
                .toList();
    }
}
