package com.mali.smartbudget.service;

import com.mali.smartbudget.exception.DuplicateStatementException;
import com.mali.smartbudget.model.Statement;
import com.mali.smartbudget.model.StatementStatus;
import com.mali.smartbudget.model.Transaction;
import com.mali.smartbudget.model.User;
import com.mali.smartbudget.repository.StatementRepository;
import com.mali.smartbudget.repository.UserRepository;
import com.mali.smartbudget.util.ChecksumUtil;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
 *  3. ExtractionService.extractAndMap() — bu @Transactional'a JOIN olur
 *       ↳ Harcamaları sil + yenilerini kaydet
 *  4. Dönem (periodStart/periodEnd) hesapla
 *  5. Dönem çakışma kontrolü → 409 DuplicateStatementException
 *       ↳ Çakışma varsa tüm transaction ROLLBACK (deleteAll geri alınır)
 *  6. Statement metadata'yı kaydet
 * </pre>
 *
 * <h3>Neden tek @Transactional?</h3>
 * ExtractionService.extractAndMap() varsayılan REQUIRED propagation ile
 * bu sınıfın başlattığı transaction'a JOIN olur. Adım 5'te conflict
 * exception fırlatılırsa, adım 3'teki deleteAll + saveAll da rollback'e
 * dahil olur — kullanıcının mevcut verisi bozulmaz.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatementService {

    private final StatementRepository statementRepository;
    private final UserRepository      userRepository;
    private final ExtractionService   extractionService;

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
    public String processUpload(MultipartFile file, Long userId, String fileName) throws IOException {

        // ── Adım 1: SHA-256 ───────────────────────────────────────────────────
        byte[] bytes    = file.getBytes();
        String checksum = ChecksumUtil.sha256(bytes);
        log.info("[Upload 1/5] SHA-256 hesaplandı: {}... | dosya={}", checksum.substring(0, 12), fileName);

        // ── Adım 2: Hash mükerrerlik kontrolü ────────────────────────────────
        if (statementRepository.existsByUserIdAndSha256Checksum(userId, checksum)) {
            log.warn("[Upload 2/5] HASH MÜKERRERİ — userId={}, checksum={}...", userId, checksum.substring(0, 8));
            throw new DuplicateStatementException(
                    "Bu dosya daha önce yüklendi. Aynı PDF içeriği sistemde zaten kayıtlı.",
                    DuplicateStatementException.Type.HASH, null, null
            );
        }
        log.info("[Upload 2/5] Hash kontrolü geçildi.");

        // ── Adım 3: PDF ayıklama + kayıt (bu @Transactional'a JOIN olur) ─────
        log.info("[Upload 3/5] Extraction başlıyor...");
        List<Transaction> saved = extractionService.extractAndMap(file, userId);
        log.info("[Upload 3/5] {} işlem ayıklandı ve kaydedildi.", saved.size());

        // ── Adım 4: Dönem hesaplama ───────────────────────────────────────────
        Optional<LocalDate> periodStart = saved.stream()
                .map(Transaction::getDate)
                .min(Comparator.naturalOrder());
        Optional<LocalDate> periodEnd = saved.stream()
                .map(Transaction::getDate)
                .max(Comparator.naturalOrder());

        if (periodStart.isPresent()) {
            log.info("[Upload 4/5] Dönem: {} – {}", periodStart.get(), periodEnd.get());
        } else {
            log.warn("[Upload 4/5] İşlem tarihi bulunamadı — dönem boş.");
        }

        // ── Adım 5: Dönem çakışma kontrolü ───────────────────────────────────
        if (periodStart.isPresent() && periodEnd.isPresent()) {
            long conflictCount = statementRepository.countOverlappingPeriods(
                    userId, periodStart.get(), periodEnd.get());

            if (conflictCount > 0) {
                log.warn("[Upload 5/5] DÖNEM ÇAKIŞMASI — userId={}, dönem={} – {}, çakışan kayıt={}",
                        userId, periodStart.get(), periodEnd.get(), conflictCount);
                throw new DuplicateStatementException(
                        "Bu ekstre dönemi zaten kayıtlı! %s – %s aralığını kapsayan bir ekstre mevcut."
                                .formatted(periodStart.get(), periodEnd.get()),
                        DuplicateStatementException.Type.PERIOD,
                        periodStart.get(), periodEnd.get()
                );
            }
        }
        log.info("[Upload 5/5] Dönem kontrolü geçildi.");

        // ── Adım 6: Statement metadata kaydet ────────────────────────────────
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Kullanıcı bulunamadı: " + userId));

        Statement statement = Statement.builder()
                .user(user)
                .fileName(fileName)
                .sha256Checksum(checksum)
                .uploadDate(LocalDate.now())
                .periodStart(periodStart.orElse(null))
                .periodEnd(periodEnd.orElse(null))
                .status(StatementStatus.PROCESSED)
                .build();
        statementRepository.save(statement);
        log.info("[Upload 6/6] Statement kaydedildi. id={}", statement.getId());

        // ── Başarı mesajı ─────────────────────────────────────────────────────
        String msg = "%d işlem başarıyla işlendi".formatted(saved.size());
        if (periodStart.isPresent()) {
            msg += " (%s – %s dönemi)".formatted(periodStart.get(), periodEnd.get());
        }
        return msg + ".";
    }
}
