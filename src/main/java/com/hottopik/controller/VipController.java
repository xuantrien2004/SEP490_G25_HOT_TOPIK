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

    @GetMapping({"/vip", "/vip/thanh-toan"})
    public String vip() {
        return "redirect:/subscription/plans";
    }

    @GetMapping("/vip/ket-qua")
    public String result() {
        return "redirect:/subscription/transactions";
    }

    @GetMapping({"/goi-cua-toi", "/vip/my-package"})
    public String myPackage() {
        return "redirect:/subscription/my-plan";
    }
}
