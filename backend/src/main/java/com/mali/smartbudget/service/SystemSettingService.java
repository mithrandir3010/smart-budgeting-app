package com.mali.smartbudget.service;

import com.mali.smartbudget.model.SystemSetting;
import com.mali.smartbudget.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SystemSettingService {

    private final SystemSettingRepository repository;

    public String get(String key, String defaultValue) {
        return repository.findById(key)
                .map(SystemSetting::getValue)
                .orElse(defaultValue);
    }

    @Transactional
    public void set(String key, String value) {
        SystemSetting s = repository.findById(key)
                .orElse(new SystemSetting(key, value, LocalDateTime.now()));
        s.setValue(value);
        s.setUpdatedAt(LocalDateTime.now());
        repository.save(s);
    }

    public boolean isMaintenanceMode() {
        return "true".equals(get("maintenance_mode", "false"));
    }

    public String getAnnouncement() {
        return get("announcement", "");
    }

    public Set<String> getDisabledBanks() {
        String val = get("disabled_banks", "");
        if (val == null || val.isBlank()) return Set.of();
        return Arrays.stream(val.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }
}
