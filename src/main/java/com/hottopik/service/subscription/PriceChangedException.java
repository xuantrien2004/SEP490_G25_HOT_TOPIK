package com.hottopik.service.subscription;

/** Giá gói đã thay đổi giữa lúc học viên xem và lúc xác nhận mua: hiển thị lại giá mới để xác nhận. */
public class PriceChangedException extends RuntimeException {

    private final long currentPrice;

    public PriceChangedException(long currentPrice) {
        super("Giá gói học vừa được cập nhật. Vui lòng xem lại giá mới trước khi xác nhận thanh toán.");
        this.currentPrice = currentPrice;
    }

    /** Giá mới nhất trong database. */
    public long getCurrentPrice() {
        return currentPrice;
    }
}
