package com.waterquality.constant;

import java.util.HashMap;
import java.util.Map;

public final class ErrorCode {
    private ErrorCode() {}

    public static final String SUCCESS = "0000";
    public static final String PARAM_ERROR = "1001";
    public static final String UNAUTHORIZED = "1002";
    public static final String FORBIDDEN = "1003";
    public static final String DATA_NOT_FOUND = "1004";
    public static final String METHOD_NOT_SUPPORTED = "1005";
    public static final String REQUEST_TOO_LARGE = "1006";
    public static final String RATE_LIMITED = "1007";

    public static final String INFERENCE_FAILED = "2001";
    public static final String DB_CONNECTION_ERROR = "2002";
    public static final String DB_QUERY_TIMEOUT = "2003";
    public static final String TRANSACTION_FAILED = "2004";

    public static final String CAMERA_OFFLINE = "3001";
    public static final String SENSOR_TIMEOUT = "3002";
    public static final String STORAGE_FULL = "3003";
    public static final String NETWORK_DISCONNECTED = "3004";

    public static final String SMS_API_FAILED = "4001";
    public static final String MAP_API_TIMEOUT = "4002";
    public static final String THIRD_PARTY_ERROR = "4003";

    public static final String SYSTEM_RESOURCE_EXHAUSTED = "9001";
    public static final String INTERNAL_SERVICE_TIMEOUT = "9002";
    public static final String CONFIG_LOAD_FAILED = "9003";

    private static final Map<String, String> MESSAGES = new HashMap<>();

    static {
        MESSAGES.put(SUCCESS, "操作成功");
        MESSAGES.put(PARAM_ERROR, "请求参数缺失或格式错误");
        MESSAGES.put(UNAUTHORIZED, "JWT令牌无效或已过期");
        MESSAGES.put(FORBIDDEN, "用户权限不足");
        MESSAGES.put(DATA_NOT_FOUND, "请求的资源不存在");
        MESSAGES.put(METHOD_NOT_SUPPORTED, "请求方法不支持");
        MESSAGES.put(REQUEST_TOO_LARGE, "请求体过大");
        MESSAGES.put(RATE_LIMITED, "请求频率超限");

        MESSAGES.put(INFERENCE_FAILED, "模型推理引擎失败");
        MESSAGES.put(DB_CONNECTION_ERROR, "数据库连接异常");
        MESSAGES.put(DB_QUERY_TIMEOUT, "数据库查询超时");
        MESSAGES.put(TRANSACTION_FAILED, "事务提交失败");

        MESSAGES.put(CAMERA_OFFLINE, "摄像头离线");
        MESSAGES.put(SENSOR_TIMEOUT, "传感器读取超时");
        MESSAGES.put(STORAGE_FULL, "边缘端存储空间不足");
        MESSAGES.put(NETWORK_DISCONNECTED, "网络连接断开");

        MESSAGES.put(SMS_API_FAILED, "短信平台调用失败");
        MESSAGES.put(MAP_API_TIMEOUT, "地图API调用超时");
        MESSAGES.put(THIRD_PARTY_ERROR, "第三方服务返回异常");

        MESSAGES.put(SYSTEM_RESOURCE_EXHAUSTED, "请求频率超过限制");
        MESSAGES.put(INTERNAL_SERVICE_TIMEOUT, "内部服务调用超时");
        MESSAGES.put(CONFIG_LOAD_FAILED, "配置加载失败");
    }

    public static String getMessage(String code) {
        return MESSAGES.getOrDefault(code, "未知错误");
    }
}
