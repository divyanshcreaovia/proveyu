package com.proveyu.settings.application.dto;

import lombok.Data;

@Data
public class SettingsDto {
    private boolean emailNotifications;
    private boolean smsNotifications;
    private boolean pushNotifications;
    private boolean testReminders;
    private boolean interviewInvites;
    private boolean applicationUpdates;
    private boolean jobOffers;
    private boolean marketingUpdates;
    private boolean newsletter;
}
