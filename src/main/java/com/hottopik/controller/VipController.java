package com.hottopik.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Các đường dẫn VIP cũ (giá viết cứng trong HTML, trang kết quả thanh toán giả) được chuyển sang module
 * gói học mới: giá lấy từ database và thanh toán qua VNPay Sandbox. Giữ URL cũ để link trong navbar
 * và các trang khác của nhóm vẫn hoạt động. Template vip/*.html cũ không bị xóa.
 */
@Controller
public class VipController {

    /** Đường dẫn VIP cũ: chuyển sang bảng giá mới. */
    @GetMapping({"/vip", "/vip/thanh-toan"})
    public String vip() {
        return "redirect:/subscription/plans";
    }

    /** Trang kết quả VIP cũ: chuyển sang lịch sử giao dịch. */
    @GetMapping("/vip/ket-qua")
    public String result() {
        return "redirect:/subscription/transactions";
    }

    /** Trang "Gói của tôi" cũ: chuyển sang trang gói hiện tại mới. */
    @GetMapping({"/goi-cua-toi", "/vip/my-package"})
    public String myPackage() {
        return "redirect:/subscription/my-plan";
    }
}
