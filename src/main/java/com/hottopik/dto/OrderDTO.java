package com.hottopik.dto;

import java.io.Serializable;

public class OrderDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String orderCode;
    private String customerName;
    private String customerEmail;
    private String packageName;
    private double amount;
    private String paymentMethod;
    private String status; // PAID, PENDING, REFUND_REQUESTED, REFUNDED
    private String createdAt;

    public OrderDTO() {}

    public OrderDTO(Long id, String orderCode, String customerName, String customerEmail,
                    String packageName, double amount, String paymentMethod,
                    String status, String createdAt) {
        this.id = id;
        this.orderCode = orderCode;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.packageName = packageName;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
