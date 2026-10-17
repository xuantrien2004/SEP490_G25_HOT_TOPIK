package com.hottopik.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class VipController {

    @GetMapping("/vip")
    public String vip(Model model) {
        model.addAttribute("pageTitle", "Gói học VIP · HOT TOPIK");
        model.addAttribute("activePage", "vip");
        return "vip/index";
    }

    @GetMapping("/vip/thanh-toan")
    public String checkout(@RequestParam(value = "package", defaultValue = "M3") String pkg, Model model) {
        model.addAttribute("pageTitle", "Thanh toán gói VIP · HOT TOPIK");
        model.addAttribute("activePage", "vip");
        model.addAttribute("selectedPackage", pkg);
        return "vip/checkout";
    }

    @GetMapping("/vip/ket-qua")
    public String result(@RequestParam(value = "order_id", required = false) String orderId, Model model) {
        model.addAttribute("pageTitle", "Kết quả thanh toán · HOT TOPIK");
        model.addAttribute("activePage", "vip");
        model.addAttribute("orderId", orderId != null ? orderId : "ORD-8921");
        return "vip/result";
    }

    @GetMapping({"/goi-cua-toi", "/vip/my-package"})
    public String myPackage(Model model) {
        model.addAttribute("pageTitle", "Gói của tôi & Đơn hàng · HOT TOPIK");
        model.addAttribute("activePage", "vip");
        return "vip/my-package";
    }
}
