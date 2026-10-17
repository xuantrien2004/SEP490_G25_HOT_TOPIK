package com.hottopik.dto;

import java.io.Serializable;

public class AiBudgetDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String serviceName;
    private double monthlyLimitUsd;
    private double currentUsageUsd;
    private int alertThresholdPercent;
    private String status; // NORMAL, WARNING, CRITICAL

    public AiBudgetDTO() {}

    public AiBudgetDTO(Long id, String serviceName, double monthlyLimitUsd, double currentUsageUsd, int alertThresholdPercent, String status) {
        this.id = id;
        this.serviceName = serviceName;
        this.monthlyLimitUsd = monthlyLimitUsd;
        this.currentUsageUsd = currentUsageUsd;
        this.alertThresholdPercent = alertThresholdPercent;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public double getMonthlyLimitUsd() { return monthlyLimitUsd; }
    public void setMonthlyLimitUsd(double monthlyLimitUsd) { this.monthlyLimitUsd = monthlyLimitUsd; }

    public double getCurrentUsageUsd() { return currentUsageUsd; }
    public void setCurrentUsageUsd(double currentUsageUsd) { this.currentUsageUsd = currentUsageUsd; }

    public int getAlertThresholdPercent() { return alertThresholdPercent; }
    public void setAlertThresholdPercent(int alertThresholdPercent) { this.alertThresholdPercent = alertThresholdPercent; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getUsagePercent() {
        if (monthlyLimitUsd <= 0) return 0;
        return (currentUsageUsd / monthlyLimitUsd) * 100.0;
    }
}
