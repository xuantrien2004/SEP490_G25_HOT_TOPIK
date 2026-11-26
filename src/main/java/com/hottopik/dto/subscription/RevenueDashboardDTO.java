package com.hottopik.dto.subscription;

import com.hottopik.util.PaymentAmountUtil;

import java.util.List;

/** Báo cáo doanh thu MÔ PHỎNG (chỉ từ giao dịch VNPay Sandbox SUCCESS đã xác minh). */
public record RevenueDashboardDTO(
        String rangeLabel,
        String fromText,
        String toText,
        long totalRevenueAllTime,
        long revenueInRange,
        long revenueToday,
        long revenueThisMonth,
        long successCountInRange,
        long plansSoldInRange,
        long activePremiumUsers,
        long averageOrderValue,
        List<PlanRevenue> byPlan,
        List<String> chartLabels,
        List<Long> chartRevenue,
        List<Long> chartCount,
        List<PaymentTransactionDTO> recent
) {

    public record PlanRevenue(String planName, long count, long revenue) {
        public String getRevenueText() {
            return PaymentAmountUtil.format(revenue);
        }
    }

    public String money(long value) {
        return PaymentAmountUtil.format(value);
    }
}
