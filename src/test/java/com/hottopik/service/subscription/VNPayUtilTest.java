package com.hottopik.service.subscription;

import com.hottopik.util.PaymentAmountUtil;
import com.hottopik.util.VNPaySignatureUtil;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Chữ ký VNPay (HMAC-SHA512) và quy đổi số tiền. */
class VNPayUtilTest {

    private static final String SECRET = "unit-test-secret";

    private static Map<String, String> params() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("vnp_TxnRef", "HT2611250001P1");
        p.put("vnp_Amount", "9900000");
        p.put("vnp_OrderInfo", "Thanh toan don hang HT2611250001");
        p.put("vnp_ResponseCode", "00");
        p.put("vnp_Empty", "");
        return p;
    }

    @Test
    void hashDataIsSortedEncodedAndSkipsEmptyValues() {
        assertEquals("vnp_Amount=9900000&vnp_OrderInfo=Thanh+toan+don+hang+HT2611250001&vnp_ResponseCode=00&vnp_TxnRef=HT2611250001P1",
                VNPaySignatureUtil.buildHashData(params()));
    }

    @Test
    void verifyAcceptsOwnSignatureIgnoringCase() {
        Map<String, String> p = params();
        p.put("vnp_SecureHash", VNPaySignatureUtil.sign(p, SECRET).toUpperCase());
        p.put("vnp_SecureHashType", "HmacSHA512");
        assertTrue(VNPaySignatureUtil.verify(p, SECRET));
    }

    @Test
    void verifyRejectsTamperedAmountWrongSecretOrMissingHash() {
        Map<String, String> p = params();
        p.put("vnp_SecureHash", VNPaySignatureUtil.sign(p, SECRET));
        assertFalse(VNPaySignatureUtil.verify(p, "other-secret"));
        p.put("vnp_Amount", "100");
        assertFalse(VNPaySignatureUtil.verify(p, SECRET));
        p.remove("vnp_SecureHash");
        assertFalse(VNPaySignatureUtil.verify(p, SECRET));
    }

    @Test
    void amountIsMultipliedBy100AndParsedBack() {
        assertEquals("9900000", PaymentAmountUtil.toVnpAmount(99_000));
        assertEquals(99_000L, PaymentAmountUtil.fromVnpAmount("9900000"));
        assertNull(PaymentAmountUtil.fromVnpAmount("9900050"), "không chia hết cho 100");
        assertNull(PaymentAmountUtil.fromVnpAmount("-100"));
        assertNull(PaymentAmountUtil.fromVnpAmount("abc"));
        assertThrows(IllegalArgumentException.class, () -> PaymentAmountUtil.toVnpAmount(0));
    }

    @Test
    void ipnSuccessRequiresBothResponseCodeAndTransactionStatus() {
        assertTrue(new PaymentGateway.GatewayResult(true, "00", null, null, null, "00", "00", "1", null, 1L, null).isSuccess());
        assertFalse(new PaymentGateway.GatewayResult(true, "00", null, null, null, "00", "02", "1", null, 1L, null).isSuccess());
        assertFalse(new PaymentGateway.GatewayResult(true, "00", null, null, null, "24", "00", "1", null, 1L, null).isSuccess());
    }
}
