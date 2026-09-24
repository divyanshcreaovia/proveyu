package com.proveyu.settings.application;

import com.proveyu.auth.domain.User;
import com.proveyu.auth.infrastructure.UserRepository;
import com.proveyu.settings.application.dto.SettingsDto;
import com.proveyu.settings.domain.CandidateSettings;
import com.proveyu.settings.domain.CandidateSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CandidateSettingsService {

    private final CandidateSettingsRepository settingsRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public SettingsDto getSettings(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        CandidateSettings settings = settingsRepository.findByUser(user).orElseGet(() -> {
            CandidateSettings defaultSettings = new CandidateSettings();
            defaultSettings.setUser(user);
            return defaultSettings;
        });
        
        return mapToDto(settings);
    }

    @Transactional
    public SettingsDto updateSettings(UUID userId, SettingsDto dto) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        CandidateSettings settings = settingsRepository.findByUser(user).orElseGet(() -> {
            CandidateSettings newSettings = new CandidateSettings();
            newSettings.setUser(user);
            return newSettings;
        });

        settings.setEmailNotifications(dto.isEmailNotifications());
        settings.setSmsNotifications(dto.isSmsNotifications());
        settings.setPushNotifications(dto.isPushNotifications());
        settings.setTestReminders(dto.isTestReminders());
        settings.setInterviewInvites(dto.isInterviewInvites());
        settings.setApplicationUpdates(dto.isApplicationUpdates());
        settings.setJobOffers(dto.isJobOffers());
        settings.setMarketingUpdates(dto.isMarketingUpdates());
        settings.setNewsletter(dto.isNewsletter());

        settingsRepository.save(settings);
        return mapToDto(settings);
    }

    private SettingsDto mapToDto(CandidateSettings entity) {
        SettingsDto dto = new SettingsDto();
        dto.setEmailNotifications(entity.isEmailNotifications());
        dto.setSmsNotifications(entity.isSmsNotifications());
        dto.setPushNotifications(entity.isPushNotifications());
        dto.setTestReminders(entity.isTestReminders());
        dto.setInterviewInvites(entity.isInterviewInvites());
        dto.setApplicationUpdates(entity.isApplicationUpdates());
        dto.setJobOffers(entity.isJobOffers());
        dto.setMarketingUpdates(entity.isMarketingUpdates());
        dto.setNewsletter(entity.isNewsletter());
        return dto;
    }
}
