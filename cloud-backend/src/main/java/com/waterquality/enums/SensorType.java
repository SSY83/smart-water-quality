package com.waterquality.enums;

public enum SensorType {
    PH("", new double[]{0, 14}),
    TURBIDITY("NTU", new double[]{0, 5, 30, 80}),
    COD("mg/L", new double[]{0, 15, 30, 50});

    private final String unit;
    private final double[] thresholds;

    SensorType(String unit, double[] thresholds) {
        this.unit = unit;
        this.thresholds = thresholds;
    }

    public String getUnit() { return unit; }
    public double[] getThresholds() { return thresholds; }
}
