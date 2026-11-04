package com.hottopik.service.exam;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** Lựa chọn đã kiểm tra phải được giữ tại server để tải lại hay autosave không đổi đáp án. */
public final class CheckedAnswers {
    private static final ObjectMapper JSON = new ObjectMapper();
    private CheckedAnswers() {}
    public static Map<String,String> read(String value) {
        if(value == null || value.isBlank()) return new LinkedHashMap<>();
        try { return JSON.readValue(value, new TypeReference<LinkedHashMap<String,String>>() {}); }
        catch(Exception ex) { throw new IllegalStateException("Không đọc được lựa chọn đã lưu.",ex); }
    }
    public static String write(Map<?,?> value) {
        try { return JSON.writeValueAsString(value); }
        catch(Exception ex) { throw new IllegalStateException(ex); }
    }
}
