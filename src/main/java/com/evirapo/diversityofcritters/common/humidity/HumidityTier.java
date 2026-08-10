package com.evirapo.diversityofcritters.common.humidity;

public enum HumidityTier {
    ARID("Arid", 0xFFC0392B),
    DRY("Dry", 0xFFE67E22),
    LOW("Low", 0xFFF0C040),
    MODERATE("Moderate", 0xFF4CAF50),
    HUMID("Humid", 0xFF26A69A),
    WET("Wet", 0xFF1976D2),
    SATURATED("Saturated", 0xFF4527A0);

    private final String displayName;
    private final int color;

    HumidityTier(String displayName, int color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() { return displayName; }
    public int getColor() { return color; }

    public static HumidityTier byId(int id) {
        HumidityTier[] values = values();
        return values[Math.max(0, Math.min(values.length - 1, id))];
    }

    /** Downfall vanilla: 0.0 (desierto) a 1.0 (jungla). */
    public static HumidityTier fromDownfall(float downfall) {
        if (downfall < 0.05F) return ARID;
        if (downfall < 0.2F) return DRY;
        if (downfall < 0.4F) return LOW;
        if (downfall < 0.6F) return MODERATE;
        if (downfall < 0.8F) return HUMID;
        if (downfall < 0.95F) return WET;
        return SATURATED;
    }

    /** 0 = dentro del rango (Confort), 1 = Estrés, 2+ = Peligro */
    public int distanceOutside(HumidityTier min, HumidityTier max) {
        if (this.ordinal() < min.ordinal()) return min.ordinal() - this.ordinal();
        if (this.ordinal() > max.ordinal()) return this.ordinal() - max.ordinal();
        return 0;
    }
}
