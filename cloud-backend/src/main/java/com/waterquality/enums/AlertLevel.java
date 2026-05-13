package com.waterquality.enums;

public enum AlertLevel {
    NORMAL(0, "正常"),
    MILD(1, "轻度异常"),
    MODERATE(2, "中度异常"),
    SEVERE(3, "重度异常");

    private final int levelCode;
    private final String description;

    AlertLevel(int levelCode, String description) {
        this.levelCode = levelCode;
        this.description = description;
    }

    public int getLevelCode() { return levelCode; }
    public String getDescription() { return description; }

    public static AlertLevel fromCode(int code) {
        for (AlertLevel level : values()) {
            if (level.levelCode == code) return level;
        }
        return NORMAL;
    }

    public static String getDescriptionByCode(int code) {
        return fromCode(code).getDescription();
    }
}
