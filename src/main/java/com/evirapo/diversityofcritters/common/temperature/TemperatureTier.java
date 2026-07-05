package com.evirapo.diversityofcritters.common.temperature;

public enum TemperatureTier {
    FREEZING("Freezing", 0xFF4A90D9),
    COLD("Cold", 0xFF5BA3E0),
    COOL("Cool", 0xFF4CAF8A),
    MODERATE("Moderate", 0xFF4CAF50),
    WARM("Warm", 0xFFF5A623),
    HOT("Hot", 0xFFE8621A),
    SCORCHING("Scorching", 0xFFC0392B);

    private final String displayName;
    private final int color;

    TemperatureTier(String displayName, int color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() { return displayName; }
    public int getColor() { return color; }

    public static TemperatureTier byId(int id) {
        TemperatureTier[] values = values();
        return values[Math.max(0, Math.min(values.length - 1, id))];
    }

    public static TemperatureTier fromTemperature(float temperature) {
        if (temperature < 0.0F) return FREEZING;
        if (temperature < 0.3F) return COLD;
        if (temperature < 0.6F) return COOL;
        if (temperature < 0.9F) return MODERATE;
        if (temperature < 1.2F) return WARM;
        if (temperature < 1.9F) return HOT;
        return SCORCHING;
    }

    /** 0 = dentro del rango (Confort), 1 = Estrés, 2+ = Peligro */
    public int distanceOutside(TemperatureTier min, TemperatureTier max) {
        if (this.ordinal() < min.ordinal()) return min.ordinal() - this.ordinal();
        if (this.ordinal() > max.ordinal()) return this.ordinal() - max.ordinal();
        return 0;
    }
}
