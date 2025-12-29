package com.simpletown.data;

import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.Map;

public class TownFlags {
    private final EnumMap<TownFlag, Boolean> values = new EnumMap<>(TownFlag.class);

    public TownFlags() {
        for (TownFlag flag : TownFlag.values()) {
            values.put(flag, false);
        }
    }

    public TownFlags(TownFlags copy) {
        this();
        for (TownFlag flag : TownFlag.values()) {
            values.put(flag, copy.isEnabled(flag));
        }
    }

    public boolean isEnabled(TownFlag flag) {
        return values.getOrDefault(flag, false);
    }

    public void set(TownFlag flag, boolean enabled) {
        values.put(flag, enabled);
    }

    public void toggle(TownFlag flag) {
        values.put(flag, !isEnabled(flag));
    }

    public TownFlags copy() {
        return new TownFlags(this);
    }

    public Map<String, Object> serialize() {
        Map<String, Object> data = new java.util.HashMap<>();
        for (TownFlag flag : TownFlag.values()) {
            data.put(flag.getConfigKey(), isEnabled(flag));
        }
        return data;
    }

    public static TownFlags fromSection(ConfigurationSection section, TownFlags defaults) {
        TownFlags flags = new TownFlags(defaults);
        if (section == null) {
            return flags;
        }
        for (TownFlag flag : TownFlag.values()) {
            if (section.contains(flag.getConfigKey())) {
                flags.set(flag, section.getBoolean(flag.getConfigKey(), flags.isEnabled(flag)));
            }
        }
        return flags;
    }
}