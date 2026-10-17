package com.hottopik.dto;

import java.io.Serializable;

public class PackagePriceDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String packageCode;
    private String name;
    private int durationDays;
    private double currentPrice;
    private double proposedPrice;
    private String proposalStatus; // ACTIVE, PROPOSED, REJECTED
    private String description;

    public PackagePriceDTO() {}

    public PackagePriceDTO(Long id, String packageCode, String name, int durationDays,
                           double currentPrice, double proposedPrice, String proposalStatus,
                           String description) {
        this.id = id;
        this.packageCode = packageCode;
        this.name = name;
        this.durationDays = durationDays;
        this.currentPrice = currentPrice;
        this.proposedPrice = proposedPrice;
        this.proposalStatus = proposalStatus;
        this.description = description;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPackageCode() { return packageCode; }
    public void setPackageCode(String packageCode) { this.packageCode = packageCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getDurationDays() { return durationDays; }
    public void setDurationDays(int durationDays) { this.durationDays = durationDays; }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public double getProposedPrice() { return proposedPrice; }
    public void setProposedPrice(double proposedPrice) { this.proposedPrice = proposedPrice; }

    public String getProposalStatus() { return proposalStatus; }
    public void setProposalStatus(String proposalStatus) { this.proposalStatus = proposalStatus; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
