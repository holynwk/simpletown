package com.simpletown.data;

import org.bukkit.Material;

public enum AgeTier {
    AGE1(1, "I Каменный Век", Material.COBBLESTONE, "Дает возможность крафтить Каменный станок и Склад I."),
    AGE2(2, "II Деревенский Век", Material.OAK_WOOD, "Дает возможность крафтить Оружейную."),
    AGE3(3, "III Железный Век", Material.IRON_INGOT, "Дает возможность крафтить Железный станок и Склад II."),
    AGE4(4, "IV Медный Век", Material.COPPER_INGOT, "Дает возможность крафтить Медный станок."),
    AGE5(5, "V Золотой Век", Material.GOLD_INGOT, "Дает возможность крафтить Золотой станок и Банк I."),
    AGE6(6, "VI Индустриальный Век", Material.EXPERIENCE_BOTTLE, "Дает возможность крафтить Лабораторию, Университет и Электростанцию."),
    AGE7(7, "VII Алмазный Век", Material.DIAMOND, "Дает возможность крафтить Алмазный станок, Банк II и Склад III."),
    AGE8(8, "VIII Промышленный Век", Material.BRICK, "Дает возможность крафтить Промышленный и Машинный заводы, Банк III."),
    AGE9(9, "IX Незеритовый Век", Material.NETHERITE_INGOT, "Дает возможность крафтить Незеритовый станок."),
    AGE10(10, "X Военный Век", Material.TNT, "Дает возможность крафтить Военный завод.");

    private final int level;
    private final String displayName;
    private final Material icon;
    private final String description;

    AgeTier(int level, String displayName, Material icon, String description) {
        this.level = level;
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
    }

    public int getLevel() {
        return level;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public String getDescription() {
        return description;
    }

    public static AgeTier byLevel(int level) {
        for (AgeTier tier : values()) {
            if (tier.level == level) {
                return tier;
            }
        }
        return AGE1;
    }
}