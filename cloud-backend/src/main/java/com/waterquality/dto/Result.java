package com.waterquality.dto;

import com.waterquality.constant.ErrorCode;

public class Result<T> {
    private String code;
    private String message;
    private T data;
    private Long total;

    private Result() {}

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.code = ErrorCode.SUCCESS;
        result.message = ErrorCode.getMessage(ErrorCode.SUCCESS);
        result.data = data;
        return result;
    }

    public static <T> Result<T> success(T data, Long total) {
        Result<T> result = success(data);
        result.total = total;
        return result;
    }

    public static <T> Result<T> error(String code, String message) {
        Result<T> result = new Result<>();
        result.code = code;
        result.message = message;
        return result;
    }

    public static <T> Result<T> error(String code) {
        return error(code, ErrorCode.getMessage(code));
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public Long getTotal() { return total; }
    public void setTotal(Long total) { this.total = total; }
}
