package com.hottopik.service.subscription;

import com.hottopik.dto.subscription.PaymentTransactionDTO;
import com.hottopik.dto.subscription.RevenueDashboardDTO;
import com.hottopik.entity.subscription.PaymentStatus;
import com.hottopik.entity.subscription.PaymentTransaction;
import com.hottopik.repository.subscription.PaymentTransactionRepository;
import com.hottopik.repository.subscription.UserSubscriptionRepository;
import com.hottopik.util.PaymentDateTimeUtil;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Doanh thu MÔ PHỎNG: chỉ tính giao dịch VNPay Sandbox có trạng thái SUCCESS (đã xác minh qua IPN / QueryDR).
 * Mỗi đơn chỉ có tối đa một giao dịch SUCCESS nên IPN gửi lặp không cộng trùng doanh thu.
 */
@Service
public class RevenueService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PaymentTransactionRepository transactions;
    private final UserSubscriptionRepository subscriptions;
    private final PaymentService paymentService;

    public RevenueService(PaymentTransactionRepository transactions, UserSubscriptionRepository subscriptions,
                          PaymentService paymentService) {
        this.transactions = transactions;
        this.subscriptions = subscriptions;
        this.paymentService = paymentService;
    }

    /** Khoảng thời gian theo bộ lọc: today, 7d, 30d, month, year, custom (from/to là ngày, to tính hết ngày). */
    public record Range(String key, String label, LocalDateTime from, LocalDateTime to) {}

    /** Đổi bộ lọc (today, 7d, 30d, month, year, custom) thành khoảng thời gian [from, to). */
    public Range resolveRange(String key, LocalDate from, LocalDate to) {
        LocalDate today = PaymentDateTimeUtil.now().toLocalDate();
        String k = key == null ? "30d" : key;
        return switch (k) {
            case "today" -> new Range(k, "Hôm nay", today.atStartOfDay(), today.plusDays(1).atStartOfDay());
            case "7d" -> new Range(k, "7 ngày gần nhất", today.minusDays(6).atStartOfDay(), today.plusDays(1).atStartOfDay());
            case "month" -> new Range(k, "Tháng này", today.withDayOfMonth(1).atStartOfDay(), today.plusDays(1).atStartOfDay());
            case "year" -> new Range(k, "Năm nay", today.withDayOfYear(1).atStartOfDay(), today.plusDays(1).atStartOfDay());
            case "custom" -> {
                LocalDate f = from == null ? today.minusDays(29) : from;
                LocalDate t = to == null ? today : to;
                if (t.isBefore(f)) { LocalDate tmp = f; f = t; t = tmp; }
                if (f.isBefore(t.minusYears(3))) f = t.minusYears(3);
                yield new Range(k, "Từ " + f.format(DATE) + " đến " + t.format(DATE), f.atStartOfDay(), t.plusDays(1).atStartOfDay());
            }
            default -> new Range("30d", "30 ngày gần nhất", today.minusDays(29).atStartOfDay(), today.plusDays(1).atStartOfDay());
        };
    }

    /** Số liệu báo cáo: doanh thu mô phỏng, số giao dịch, theo gói, dữ liệu biểu đồ, giao dịch gần đây. */
    @Transactional(readOnly = true)
    public RevenueDashboardDTO getDashboard(Range range) {
        LocalDateTime now = PaymentDateTimeUtil.now();
        LocalDate today = now.toLocalDate();
        List<PaymentTransaction> success = transactions.findSuccessBetween(range.from(), range.to());

        long revenueInRange = success.stream().mapToLong(t -> amount(t)).sum();
        long count = success.size();
        Map<String, long[]> byPlan = new LinkedHashMap<>();
        for (PaymentTransaction t : success) {
            long[] acc = byPlan.computeIfAbsent(t.getOrder().getPlanNameSnapshot(), k -> new long[2]);
            acc[0]++;
            acc[1] += amount(t);
        }
        List<RevenueDashboardDTO.PlanRevenue> planRevenue = byPlan.entrySet().stream()
                .map(e -> new RevenueDashboardDTO.PlanRevenue(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingLong(RevenueDashboardDTO.PlanRevenue::revenue).reversed())
                .toList();

        // Biểu đồ: theo ngày nếu khoảng <= 62 ngày, ngược lại theo tháng.
        boolean daily = java.time.Duration.between(range.from(), range.to()).toDays() <= 62;
        TreeMap<LocalDate, long[]> buckets = new TreeMap<>();
        LocalDate cursor = range.from().toLocalDate();
        LocalDate end = range.to().toLocalDate();
        while (cursor.isBefore(end)) {
            buckets.put(daily ? cursor : cursor.withDayOfMonth(1), new long[2]);
            cursor = daily ? cursor.plusDays(1) : cursor.withDayOfMonth(1).plusMonths(1);
        }
        for (PaymentTransaction t : success) {
            LocalDate d = t.getPaidAt().toLocalDate();
            long[] b = buckets.computeIfAbsent(daily ? d : d.withDayOfMonth(1), k -> new long[2]);
            b[0] += amount(t);
            b[1]++;
        }
        List<String> labels = new ArrayList<>();
        List<Long> revenue = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        buckets.forEach((d, v) -> {
            labels.add(d.format(daily ? DAY : MONTH));
            revenue.add(v[0]);
            counts.add(v[1]);
        });

        List<PaymentTransactionDTO> recent = paymentService.toDTOs(transactions.findTop10ByStatusOrderByPaidAtDesc(PaymentStatus.SUCCESS));
        return new RevenueDashboardDTO(range.label(), range.from().format(DATE), range.to().minusDays(1).format(DATE),
                nz(transactions.sumAllSuccessAmount()), revenueInRange,
                nz(transactions.sumSuccessAmount(today.atStartOfDay(), today.plusDays(1).atStartOfDay())),
                nz(transactions.sumSuccessAmount(today.withDayOfMonth(1).atStartOfDay(), today.plusDays(1).atStartOfDay())),
                count, count, subscriptions.countValidUsers(now), count == 0 ? 0 : revenueInRange / count,
                planRevenue, labels, revenue, counts, recent);
    }

    /** Xuất Excel báo cáo doanh thu mô phỏng. */
    @Transactional(readOnly = true)
    public byte[] exportExcel(Range range) {
        RevenueDashboardDTO d = getDashboard(range);
        List<PaymentTransaction> success = transactions.findSuccessBetween(range.from(), range.to());
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle bold = wb.createCellStyle();
            Font f = wb.createFont();
            f.setBold(true);
            bold.setFont(f);
            Sheet s = wb.createSheet("Doanh thu Sandbox");
            int r = 0;
            cell(s, r++, bold, "BÁO CÁO DOANH THU THỬ NGHIỆM – VNPAY SANDBOX (KHÔNG PHẢI DOANH THU THẬT)");
            cell(s, r++, null, "Khoảng thời gian: " + d.rangeLabel() + " (" + d.fromText() + " – " + d.toText() + ")");
            cell(s, r++, null, "Xuất lúc: " + PaymentDateTimeUtil.display(PaymentDateTimeUtil.now()));
            r++;
            row(s, r++, bold, "Chỉ số", "Giá trị (VND)");
            row(s, r++, null, "Doanh thu mô phỏng trong kỳ", d.revenueInRange());
            row(s, r++, null, "Số giao dịch thành công", d.successCountInRange());
            row(s, r++, null, "Giá trị trung bình mỗi đơn", d.averageOrderValue());
            row(s, r++, null, "Tổng doanh thu mô phỏng (từ trước đến nay)", d.totalRevenueAllTime());
            row(s, r++, null, "Học viên Premium đang hoạt động", d.activePremiumUsers());
            r++;
            row(s, r++, bold, "Gói học", "Số gói bán thử nghiệm", "Doanh thu mô phỏng (VND)");
            for (RevenueDashboardDTO.PlanRevenue p : d.byPlan()) row(s, r++, null, p.planName(), p.count(), p.revenue());
            r++;
            row(s, r++, bold, "Mã đơn", "Mã giao dịch VNPay", "Gói học", "Số tiền (VND)", "Thời gian thanh toán", "Môi trường");
            for (PaymentTransaction t : success) {
                row(s, r++, null, t.getOrder().getOrderCode(), t.getProviderTransactionNo(), t.getOrder().getPlanNameSnapshot(),
                        amount(t), PaymentDateTimeUtil.display(t.getPaidAt()), t.getPaymentEnvironment());
            }
            for (int i = 0; i < 6; i++) s.setColumnWidth(i, 28 * 256);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Không xuất được báo cáo Excel.", e);
        }
    }

    private static void cell(Sheet s, int r, CellStyle style, String value) {
        Cell c = s.createRow(r).createCell(0);
        c.setCellValue(value);
        if (style != null) c.setCellStyle(style);
    }

    private static void row(Sheet s, int r, CellStyle style, Object... values) {
        Row row = s.createRow(r);
        for (int i = 0; i < values.length; i++) {
            Cell c = row.createCell(i);
            Object v = values[i];
            if (v instanceof Number n) c.setCellValue(n.doubleValue());
            else c.setCellValue(v == null ? "" : v.toString());
            if (style != null) c.setCellStyle(style);
        }
    }

    private static long amount(PaymentTransaction t) {
        return t.getPaidAmount() != null ? t.getPaidAmount() : t.getExpectedAmount();
    }

    private static long nz(Long v) {
        return v == null ? 0 : v;
    }
}
