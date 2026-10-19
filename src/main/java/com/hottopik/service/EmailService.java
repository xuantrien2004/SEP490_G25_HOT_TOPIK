package com.hottopik.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@hottopik.vn}")
    private String fromEmail;

    @Autowired(required = false)
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Gửi Email OTP Đăng ký tài khoản (Chạy Async không làm đơ giao diện người dùng)
     */
    @Async
    public void sendRegistrationOtp(String toEmail, String displayName, String otpCode) {
        String subject = "[HOT TOPIK] Mã OTP kích hoạt tài khoản học tập: " + otpCode;
        String htmlContent = buildOtpHtml(
                displayName,
                "Chào mừng bạn đến với HOT TOPIK & Bé Ssak!",
                "Cảm ơn bạn đã đăng ký tài khoản học tiếng Hàn. Vui lòng sử dụng mã OTP 6 số dưới đây để kích hoạt tài khoản của bạn:",
                otpCode,
                "Mã OTP này có hiệu lực trong vòng <strong>5 phút</strong>. Vui lòng không chia sẻ mã cho bất kỳ ai để bảo vệ tài khoản."
        );

        sendHtmlEmail(toEmail, subject, htmlContent, otpCode, "ĐĂNG KÝ");
    }

    /**
     * Gửi Email OTP Quên mật khẩu
     */
    @Async
    public void sendForgotPasswordOtp(String toEmail, String displayName, String otpCode) {
        String subject = "[HOT TOPIK] Mã OTP đặt lại mật khẩu của bạn: " + otpCode;
        String htmlContent = buildOtpHtml(
                displayName != null ? displayName : "Bạn",
                "Yêu cầu đặt lại mật khẩu tài khoản",
                "Chúng tôi nhận được yêu cầu khôi phục mật khẩu cho tài khoản HOT TOPIK của bạn. Sử dụng mã OTP dưới đây để hoàn tất:",
                otpCode,
                "Mã OTP này có hiệu lực trong vòng <strong>5 phút</strong>. Nếu bạn không yêu cầu hành động này, vui lòng bỏ qua email hoặc đổi mật khẩu ngay."
        );

        sendHtmlEmail(toEmail, subject, htmlContent, otpCode, "QUÊN MẬT KHẨU");
    }

    private void sendHtmlEmail(String toEmail, String subject, String htmlContent, String otpCode, String purpose) {
        // Luôn in mã OTP to rõ ràng ra Terminal để dev/test tức thì
        log.info("========================================================================");
        log.info("📧 >>> [MÃ OTP {} CHO EMAIL: {}]: {}", purpose, toEmail, otpCode);
        log.info("========================================================================");

        if (mailSender == null) {
            log.warn("JavaMailSender chưa được cấu hình. Chỉ in OTP ra console để kiểm thử.");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

            helper.setFrom(fromEmail, "HOT TOPIK · Bé Ssak");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("✅ Gửi email thật thành công tới: {}", toEmail);

        } catch (Exception ex) {
            log.warn("⚠️ Không thể gửi email thật tới {} qua SMTP (Lỗi: {}). Bạn vẫn có thể dùng mã OTP [{}] đã in ở trên để xác thực!",
                    toEmail, ex.getMessage(), otpCode);
        }
    }

    /**
     * Tạo mẫu Email HTML đẹp mắt chuẩn thương hiệu Bé Ssak (Mầm cây)
     */
    private String buildOtpHtml(String recipientName, String title, String intro, String otpCode, String note) {
        return "<!DOCTYPE html>" +
                "<html>" +
                "<head><meta charset='UTF-8'></head>" +
                "<body style='margin:0; padding:0; background-color:#F8F6EF; font-family:\"Segoe UI\", Arial, sans-serif; color:#24363D;'>" +
                "  <div style='max-width:560px; margin:30px auto; background:#FFFFFF; border-radius:20px; overflow:hidden; box-shadow:0 6px 24px rgba(0,0,0,0.06); border:1px solid #E8E4DA;'>" +
                "    <div style='background:#2E7D67; padding:32px 28px; text-align:center; color:#FFFFFF;'>" +
                "      <h1 style='margin:0; font-size:26px; font-weight:800; letter-spacing:1px;'>🌿 HOT TOPIK</h1>" +
                "      <p style='margin:6px 0 0 0; font-size:14px; opacity:0.9;'>Luyện thi TOPIK thông minh cùng Bé Ssak</p>" +
                "    </div>" +
                "    <div style='padding:36px 32px;'>" +
                "      <h2 style='margin:0 0 16px 0; font-size:20px; color:#1F6B56;'>" + title + "</h2>" +
                "      <p style='font-size:15px; line-height:1.6; margin:0 0 20px 0;'>Xin chào <strong>" + recipientName + "</strong>,</p>" +
                "      <p style='font-size:15px; line-height:1.6; margin:0 0 24px 0;'>" + intro + "</p>" +
                "      <div style='text-align:center; margin:28px 0;'>" +
                "        <div style='display:inline-block; padding:16px 36px; background:#E3F2EA; border:2px dashed #2E7D67; border-radius:16px; font-size:36px; font-weight:800; letter-spacing:8px; color:#1F6B56; font-family:monospace;'>" +
                otpCode +
                "        </div>" +
                "      </div>" +
                "      <p style='font-size:13.5px; line-height:1.6; color:#5A6E72; background:#FBF0D3; border-left:4px solid #F4C45A; padding:12px 16px; border-radius:8px; margin:24px 0 0 0;'>" +
                note +
                "      </p>" +
                "    </div>" +
                "    <div style='background:#F8F6EF; padding:20px 32px; text-align:center; font-size:12.5px; color:#8C9B9E; border-top:1px solid #E8E4DA;'>" +
                "      <p style='margin:0;'>© 2026 HOT TOPIK (SEP490_G25) · Mầm cây Ssak không áp lực</p>" +
                "      <p style='margin:4px 0 0 0;'>Email này được gửi tự động. Vui lòng không trả lời email này.</p>" +
                "    </div>" +
                "  </div>" +
                "</body>" +
                "</html>";
    }
}
