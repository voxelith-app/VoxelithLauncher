package net.kdt.pojavlaunch.mods;

import net.kdt.pojavlaunch.Tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mods and game options that make the game run well on weak phones (Adreno 610 / 4 GB class).
 * Projects are Modrinth slugs; each group lists alternatives in order of preference.
 */
public final class PerformancePack {
    private PerformancePack() {}

    public static List<String[]> groupsFor(String loader) {
        List<String[]> groups = new ArrayList<>();
        switch (loader) {
            case "fabric":
            case "quilt":
                groups.add(new String[]{"sodium"});
                groups.add(new String[]{"lithium"});
                groups.add(new String[]{"ferrite-core"});
                groups.add(new String[]{"modernfix"});
                groups.add(new String[]{"immediatelyfast"});
                groups.add(new String[]{"entityculling"});
                groups.add(new String[]{"moreculling"});
                groups.add(new String[]{"dynamic-fps"});
                groups.add(new String[]{"badoptimizations"});
                break;
            case "neoforge":
                groups.add(new String[]{"sodium", "embeddium"});
                groups.add(new String[]{"lithium"});
                groups.add(new String[]{"ferrite-core"});
                groups.add(new String[]{"modernfix"});
                groups.add(new String[]{"immediatelyfast"});
                groups.add(new String[]{"entityculling"});
                groups.add(new String[]{"dynamic-fps"});
                groups.add(new String[]{"badoptimizations"});
                break;
            case "forge":
                groups.add(new String[]{"embeddium"});
                groups.add(new String[]{"ferrite-core"});
                groups.add(new String[]{"modernfix"});
                groups.add(new String[]{"immediatelyfast"});
                groups.add(new String[]{"entityculling"});
                groups.add(new String[]{"dynamic-fps"});
                groups.add(new String[]{"badoptimizations"});
                break;
        }
        return groups;
    }

    /** Light video settings. Keys that a version does not know are simply ignored by the game. */
    private static final String[][] LIGHT_OPTIONS = {
            {"renderDistance", "6"},
            {"simulationDistance", "5"},
            {"graphicsMode", "0"},
            {"fancyGraphics", "false"},
            {"ao", "false"},
            {"particles", "2"},
            {"entityShadows", "false"},
            {"biomeBlendRadius", "0"},
            {"mipmapLevels", "0"},
            {"renderClouds", "\"false\""},
            {"entityDistanceScaling", "0.75"},
            {"maxFps", "60"},
            {"enableVsync", "false"},
            // The menu blur from 1.20.5 costs a lot on weak GPUs
            {"menuBackgroundBlurriness", "0"},
    };

    /** Writes the light settings into the instance options.txt, keeping every other option. */
    public static void applyLightOptions(File gameDirectory) throws IOException {
        File optionsFile = new File(gameDirectory, "options.txt");
        Map<String, String> options = new LinkedHashMap<>();
        if(optionsFile.isFile()) {
            try(BufferedReader reader = new BufferedReader(new FileReader(optionsFile))) {
                String line;
                while((line = reader.readLine()) != null) {
                    int colon = line.indexOf(':');
                    if(colon < 0) continue;
                    options.put(line.substring(0, colon), line.substring(colon + 1));
                }
            }
        }
        for(String[] option : LIGHT_OPTIONS) options.put(option[0], option[1]);
        StringBuilder builder = new StringBuilder();
        for(Map.Entry<String, String> entry : options.entrySet()) {
            builder.append(entry.getKey()).append(':').append(entry.getValue()).append('\n');
        }
        Tools.write(optionsFile.getAbsolutePath(), builder.toString());
    }
}
