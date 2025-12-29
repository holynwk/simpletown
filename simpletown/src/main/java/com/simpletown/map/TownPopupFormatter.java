package com.simpletown.map;

import com.simpletown.data.AgeTier;
import com.simpletown.data.Town;
import com.simpletown.war.WarConflict;
import com.simpletown.war.WarManager;
import com.simpletown.war.WarStatus;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class TownPopupFormatter {
    private static final String MISSING = "отсутствует";
    private static final DateTimeFormatter CREATED_FORMAT = DateTimeFormatter.ofPattern("MMM dd yyyy", Locale.US)
            .withZone(ZoneId.systemDefault());

    private TownPopupFormatter() {
    }

    public static String buildDescription(Town town, WarManager warManager) {
        if (town == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-size:2em;font-weight:bold;\">")
                .append(escape(town.getName()))
                .append("</div>");
        sb.append("<div style=\"font-size:2em;\">")
                .append("(")
                .append(MISSING)
                .append(")</div>");
        sb.append("<hr/>");

        sb.append("<div><b>Мэр:</b> ")
                .append(valueOrMissing(town.getMayor()))
                .append(" &nbsp; <b>Создан:</b> ")
                .append(createdDate(town))
                .append("</div>");
        sb.append("<div><b>Страна:</b> ").append(MISSING).append("</div>");
        sb.append("<hr/>");

        sb.append("<div><b>Альянсы:</b> ").append(MISSING).append("</div>");
        sb.append("<div><b>Век:</b> ").append(ageName(town)).append("</div>");
        sb.append("<hr/>");

        sb.append("<div><b>Войны:</b> ").append(warStatus(town, warManager)).append("</div>");
        sb.append("<hr/>");

        sb.append("<div><b>Казна:</b> ").append(formatBank(town.getBank())).append("</div>");
        sb.append("<hr/>");

        sb.append("<div><b>Граждане (")
                .append(town.getCitizens().size())
                .append("):</b> ")
                .append(citizens(town.getCitizens()))
                .append("</div>");
        sb.append("<hr/>");

        sb.append("<div><b>Объявление:</b> ")
                .append(valueOrMissing(town.getBoardMessage()))
                .append("</div>");
        sb.append("<hr/>");

        sb.append("<div><code>/town set board [msg]</code></div>");
        return sb.toString();
    }

    private static String valueOrMissing(String input) {
        if (input == null || input.isBlank()) {
            return MISSING;
        }
        return escape(input);
    }

    private static String citizens(Set<String> citizens) {
        if (citizens == null || citizens.isEmpty()) {
            return MISSING;
        }
        return citizens.stream()
                .map(TownPopupFormatter::escape)
                .sorted(String::compareToIgnoreCase)
                .collect(Collectors.joining(", "));
    }

    private static String warStatus(Town town, WarManager warManager) {
        if (town == null || warManager == null) {
            return "мирно";
        }
        return warManager.getConflictForTown(town.getName())
                .map(conflict -> describeConflict(town.getName(), conflict))
                .orElse("мирно");
    }

    private static String describeConflict(String townName, WarConflict conflict) {
        String opponent = townName.equalsIgnoreCase(conflict.getAttacker())
                ? conflict.getDefender()
                : conflict.getAttacker();
        String statusText;
        WarStatus status = conflict.getStatus();
        if (status == WarStatus.PREPARATION) {
            statusText = "подготовка к войне";
        } else if (status == WarStatus.ACTIVE) {
            statusText = "война";
        } else if (status == WarStatus.AWAITING_RESULT) {
            statusText = "ожидание результата";
        } else {
            statusText = "завершена";
        }
        return escape(statusText + " с " + opponent);
    }

    private static String ageName(Town town) {
        if (town == null) {
            return MISSING;
        }
        AgeTier tier = AgeTier.byLevel(town.getAgeLevel());
        return escape(tier.getDisplayName());
    }

    private static String createdDate(Town town) {
        if (town == null || town.getCreatedAt() <= 0) {
            return MISSING;
        }
        return escape(CREATED_FORMAT.format(Instant.ofEpochMilli(town.getCreatedAt())));
    }

    private static String formatBank(double bank) {
        return String.format(Locale.US, "%.2f", Math.max(0, bank));
    }

    private static String escape(String input) {
        if (input == null) {
            return "";
        }
        String sanitized = input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
        return sanitized.replace("\n", "<br/>");
    }
}