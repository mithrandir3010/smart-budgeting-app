package com.mali.smartbudget.service;

import com.mali.smartbudget.model.Transaction;
import com.mali.smartbudget.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;

    @Transactional
    public void deleteAllByUserId(Long userId) {
        transactionRepository.deleteAllByUserId(userId);
        log.info("İşlemler silindi. userId={}", userId);
    }

    @Transactional
    public List<Transaction> saveAllTransactions(List<Transaction> transactions) {
        List<Transaction> saved = transactionRepository.saveAll(transactions);
        log.info("İşlemler kaydedildi. adet={}", saved.size());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Transaction> getTransactionsByUser(Long userId) {
        return transactionRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<Transaction> getSubscriptionsByUser(Long userId) {
        return transactionRepository.findByUserIdAndIsSubscriptionTrue(userId);
    }
}
