package com.evirapo.diversityofcritters.client.screen;

/**
 * One climate line of a stat screen: {@code "Temp: Warm (Stress)"} in its zone's colour, or
 * {@code "Temp: Off"} while doCritterClimate is off. Shared by every critter's stat screen.
 */
public record ClimateLine(String text, int color) {

    public static ClimateLine of(String label, String tierName, int zone, boolean enabled, int offColor) {
        if (!enabled) return new ClimateLine(label + ": Off", offColor);

        String zoneName = zone == 0 ? "Comfort" : (zone == 1 ? "Stress" : "Danger");
        int color = zone == 0 ? 0x55FF55 : (zone == 1 ? 0xFFFF55 : 0xFF5555);
        return new ClimateLine(label + ": " + tierName + " (" + zoneName + ")", color);
    }
}
