package com.evirapo.diversityofcritters.client.animations;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sifaka's one-shot clips must not be flagged as looping.
 *
 * <p>DeluxeLib's mixer does {@code time %= length} for looping definitions, and an enter or exit
 * clip keeps advancing while it fades out. Flagged as looping, it wraps back to its first frame in
 * the middle of the fade and the pose visibly jumps back. Blockbench exports many one-shot clips
 * flagged as looping, so every re-export can bring it back silently. Reads the source, because the
 * clips are client classes that a unit test cannot load.
 */
class SifakaOneShotClipsTest {

    private static final String DIR = "src/main/java/com/evirapo/diversityofcritters/client/animations/";
    private static final Pattern CLIP = Pattern.compile(
            "public static final AnimationDefinition (\\w+)\\s*=\\s*AnimationDefinition\\.Builder"
                    + "\\.withLength\\([\\d.]+F?\\)(\\.looping\\(\\))?");

    private static Map<String, Boolean> looping(String file) throws IOException {
        Map<String, Boolean> clips = new HashMap<>();
        Matcher m = CLIP.matcher(Files.readString(Path.of(DIR + file)));
        while (m.find()) {
            clips.put(m.group(1), m.group(2) != null);
        }
        return clips;
    }

    private static void assertNoneLoop(String file, List<String> oneShots) throws IOException {
        Map<String, Boolean> clips = looping(file);
        List<String> offenders = new ArrayList<>();
        for (String name : oneShots) {
            assertTrue(clips.containsKey(name), file + " has no clip " + name);
            if (clips.get(name)) offenders.add(name);
        }
        assertEquals(List.of(), offenders, "one-shot clips flagged as looping in " + file);
    }

    @Test
    void adultOneShotClipsDoNotLoop() throws IOException {
        assertNoneLoop("SifakaAnims.java", List.of(
                "SIT_STARTING", "SIT_ENDING", "LAY_STARTING", "LAY_ENDING",
                "DRINK_STARTING", "DRINK_ENDING", "EATING_STARTING", "EATING_ENDING",
                "GROOMING_STARTING", "GROOMING_ENDING", "preparing_sleep", "awakening",
                "stand_up", "SniffRight", "SniffRight2", "Attack"));
    }

    @Test
    void babyOneShotClipsDoNotLoop() throws IOException {
        assertNoneLoop("SifakaBabyAnims.java", List.of(
                "preparing_sleep", "awakening", "preparing_cry", "stopping_cry"));
    }
}
