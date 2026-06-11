package com.lavarising.plugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public class LavaRisingCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ROOT_COMMANDS = Arrays.asList(
            "start",
            "stop",
            "status",
            "settings",
            "border",
            "speed");
    private static final List<String> SETTING_OPTIONS = Arrays.asList(
            "arenaDiameter",
            "lavaRisingSpeed",
            "suddenDeathDelay",
            "suddenDeathSpeed",
            "suddenDeathRadius",
            "soundsEnabled",
            "milestoneMessages",
            "giveDirt",
            "pvpAtSurface",
            "borderDamage",
            "noMoreBottomDwellers",
            "buildingBlockGiveRate");
    private static final List<String> BOOLEAN_VALUES = Arrays.asList("true", "false", "on", "off");

    private final LavaRisingManager manager;

    public LavaRisingCommand(LavaRisingManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("lavarising.use")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("lavastart")) {
            handleStart(sender);
            return true;
        }
        if (name.equals("lavastop")) {
            handleStop(sender);
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /lavarising <start|stop|status|settings>");
            sender.sendMessage(ChatColor.GRAY + "Settings: /lavarising settings <option> <value>");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start" -> handleStart(sender);
            case "stop" -> handleStop(sender);
            case "status" -> handleStatus(sender);
            case "border", "diameter" -> handleBorder(sender, args);
            case "speed" -> handleSpeed(sender, args);
            case "settings" -> handleSettings(sender, args);
            default -> sender.sendMessage(ChatColor.RED
                    + "Unknown subcommand. Use: start, stop, status, or settings.");
        }
        return true;
    }

    private void handleStart(CommandSender sender) {
        if (manager.isRunning()) {
            sender.sendMessage(ChatColor.RED + "Game is already running! Y=" + manager.getCurrentY());
            return;
        }
        if (manager.isCountdown()) {
            sender.sendMessage(ChatColor.RED + "A countdown is already in progress. Use /lavastop to cancel it.");
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "Starting lava rise...");
        manager.startLava();
    }

    private void handleStop(CommandSender sender) {
        if (!manager.isRunning() && !manager.isCountdown()) {
            sender.sendMessage(ChatColor.GRAY + "Nothing is running right now.");
            return;
        }

        manager.stopLava();
        sender.sendMessage(ChatColor.YELLOW + "Lava event stopped.");
    }

    private void handleBorder(CommandSender sender, String[] args) {
        if (manager.isInGame()) {
            sender.sendMessage(ChatColor.RED + "Stop the current game before changing the border diameter.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /lavarising border <diameter>");
            return;
        }

        try {
            manager.setArenaDiameter(Integer.parseInt(args[1]));
            sender.sendMessage(ChatColor.YELLOW + "arenaDiameter set to "
                    + ChatColor.WHITE + formatSettingValue("arenaDiameter") + ChatColor.YELLOW + ".");
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Border diameter must be a whole number.");
        }
    }

    private void handleSpeed(CommandSender sender, String[] args) {
        if (manager.isInGame()) {
            sender.sendMessage(ChatColor.RED + "Stop the current game before changing the lava rising speed.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /lavarising speed <seconds-per-block>");
            return;
        }

        try {
            manager.setLavaRisingSpeedSeconds(parseFiniteDouble(args[1]));
            sender.sendMessage(ChatColor.YELLOW + "All lavaRisingSpeed phases set to "
                    + ChatColor.WHITE + formatSettingValue("lavaRisingSpeed") + ChatColor.YELLOW + ".");
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Lava rising speed must be a number of seconds.");
        }
    }

    private void handleStatus(CommandSender sender) {
        if (!manager.isInGame()) {
            sender.sendMessage(ChatColor.GRAY + "No active game. Use /lavastart to begin.");
            sender.sendMessage(ChatColor.GRAY + "Settings: border "
                    + ChatColor.WHITE + manager.getArenaDiameter() + " blocks"
                    + ChatColor.GRAY + ", lava speed "
                    + ChatColor.WHITE + manager.getSpeedLabel()
                    + ChatColor.GRAY + ", sudden death "
                    + ChatColor.WHITE + manager.getSuddenDeathDiameter() + "x"
                    + manager.getSuddenDeathDiameter());
            return;
        }

        if (manager.isCountdown()) {
            sender.sendMessage(ChatColor.YELLOW + "Countdown is in progress.");
            return;
        }

        if (manager.getState() == LavaRisingManager.GameState.SUDDEN_DEATH) {
            sender.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD
                    + "SUDDEN DEATH: border is shrinking.");
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "Lava rising at Y=" + manager.getCurrentY()
                + " | Speed: " + manager.getSpeedLabel());
    }

    private void handleSettings(CommandSender sender, String[] args) {
        if (args.length == 1) {
            sendAllSettings(sender);
            return;
        }

        String canonical = canonicalSettingName(args[1]);
        if (canonical == null) {
            sender.sendMessage(ChatColor.RED + "Unknown setting: " + args[1]);
            sender.sendMessage(ChatColor.GRAY + "Options: " + String.join(", ", SETTING_OPTIONS));
            return;
        }

        if (canonical.equals("lavaRisingSpeed")) {
            handleLavaRisingSpeedSetting(sender, args);
            return;
        }

        if (args.length == 2) {
            sender.sendMessage(ChatColor.YELLOW + canonical + ": "
                    + ChatColor.WHITE + formatSettingValue(canonical));
            return;
        }

        if (args.length > 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /lavarising settings <option> <value>");
            return;
        }

        if (manager.isInGame()) {
            sender.sendMessage(ChatColor.RED + "Stop the current game before changing settings.");
            return;
        }

        if (setSettingValue(sender, canonical, args[2])) {
            sender.sendMessage(ChatColor.YELLOW + canonical + " set to "
                    + ChatColor.WHITE + formatSettingValue(canonical) + ChatColor.YELLOW + ".");
        }
    }

    private void sendAllSettings(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "LavaRising settings:");
        for (String option : SETTING_OPTIONS) {
            sender.sendMessage(ChatColor.GRAY + option + ": " + ChatColor.WHITE + formatSettingValue(option));
        }
        sender.sendMessage(ChatColor.GRAY + "Usage: "
                + ChatColor.WHITE + "/lavarising settings <option> <value>");
        sender.sendMessage(ChatColor.GRAY + "Phase speed usage: "
                + ChatColor.WHITE + "/lavarising settings lavaRisingSpeed <phase> <seconds>");
    }

    private void handleLavaRisingSpeedSetting(CommandSender sender, String[] args) {
        if (args.length == 2) {
            sendLavaPhaseSpeeds(sender);
            return;
        }

        if (args.length == 3) {
            if (args[2].equalsIgnoreCase("all")) {
                sendLavaPhaseSpeeds(sender);
                return;
            }

            LavaRisingManager.LavaPhase phase = LavaRisingManager.LavaPhase.fromInput(args[2]);
            if (phase != null) {
                sender.sendMessage(ChatColor.YELLOW + "lavaRisingSpeed " + phase.getId()
                        + " (" + phase.getConfigKey() + "): "
                        + ChatColor.WHITE + formatPhaseSpeed(phase));
                return;
            }

            if (manager.isInGame()) {
                sender.sendMessage(ChatColor.RED + "Stop the current game before changing settings.");
                return;
            }

            try {
                manager.setLavaRisingSpeedSeconds(parseFiniteDouble(args[2]));
                sender.sendMessage(ChatColor.YELLOW + "All lavaRisingSpeed phases set to "
                        + ChatColor.WHITE + formatSettingValue("lavaRisingSpeed") + ChatColor.YELLOW + ".");
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "lavaRisingSpeed must be a number of seconds.");
            }
            return;
        }

        if (args.length == 4) {
            if (args[2].equalsIgnoreCase("all")) {
                if (manager.isInGame()) {
                    sender.sendMessage(ChatColor.RED + "Stop the current game before changing settings.");
                    return;
                }

                try {
                    manager.setLavaRisingSpeedSeconds(parseFiniteDouble(args[3]));
                    sender.sendMessage(ChatColor.YELLOW + "All lavaRisingSpeed phases set to "
                            + ChatColor.WHITE + formatSettingValue("lavaRisingSpeed") + ChatColor.YELLOW + ".");
                } catch (NumberFormatException ex) {
                    sender.sendMessage(ChatColor.RED + "lavaRisingSpeed must be a number of seconds.");
                }
                return;
            }

            LavaRisingManager.LavaPhase phase = LavaRisingManager.LavaPhase.fromInput(args[2]);
            if (phase == null) {
                sender.sendMessage(ChatColor.RED + "Unknown lava phase: " + args[2]);
                sendPhaseUsage(sender);
                return;
            }
            if (manager.isInGame()) {
                sender.sendMessage(ChatColor.RED + "Stop the current game before changing settings.");
                return;
            }

            try {
                manager.setLavaRisingSpeedSeconds(phase, parseFiniteDouble(args[3]));
                sender.sendMessage(ChatColor.YELLOW + "lavaRisingSpeed " + phase.getId()
                        + " (" + phase.getConfigKey() + ") set to "
                        + ChatColor.WHITE + formatPhaseSpeed(phase) + ChatColor.YELLOW + ".");
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "lavaRisingSpeed must be a number of seconds.");
            }
            return;
        }

        sender.sendMessage(ChatColor.RED + "Usage: /lavarising settings lavaRisingSpeed <phase> <seconds>");
    }

    private void sendLavaPhaseSpeeds(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "lavaRisingSpeed phases:");
        for (LavaRisingManager.LavaPhase phase : manager.getLavaPhases()) {
            sender.sendMessage(ChatColor.GRAY + Integer.toString(phase.getId()) + ". "
                    + phase.getConfigKey() + " (" + phase.getDisplayName() + "): "
                    + ChatColor.WHITE + formatPhaseSpeed(phase));
        }
        sendPhaseUsage(sender);
    }

    private void sendPhaseUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GRAY + "Usage: " + ChatColor.WHITE
                + "/lavarising settings lavaRisingSpeed <1-6|phaseName|all> <seconds>");
    }

    private boolean setSettingValue(CommandSender sender, String setting, String rawValue) {
        try {
            switch (setting) {
                case "arenaDiameter" -> manager.setArenaDiameter(Integer.parseInt(rawValue));
                case "suddenDeathDelay" -> manager.setSuddenDeathDelaySeconds(Integer.parseInt(rawValue));
                case "suddenDeathSpeed" -> manager.setSuddenDeathSpeedSeconds(Integer.parseInt(rawValue));
                case "suddenDeathRadius" -> manager.setSuddenDeathRadius(Integer.parseInt(rawValue));
                case "soundsEnabled" -> manager.setSoundsEnabled(parseBoolean(sender, rawValue));
                case "milestoneMessages" -> manager.setMilestoneMessagesEnabled(parseBoolean(sender, rawValue));
                case "giveDirt" -> manager.setGiveDirtEnabled(parseBoolean(sender, rawValue));
                case "pvpAtSurface" -> manager.setPvpAtSurfaceEnabled(parseBoolean(sender, rawValue));
                case "borderDamage" -> manager.setBorderDamageEnabled(parseBoolean(sender, rawValue));
                case "noMoreBottomDwellers" -> manager.setNoMoreBottomDwellersSeconds(Integer.parseInt(rawValue));
                case "buildingBlockGiveRate" -> manager.setBuildingBlockGiveRate(Integer.parseInt(rawValue));
                default -> {
                    sender.sendMessage(ChatColor.RED + "Unknown setting: " + setting);
                    return false;
                }
            }
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + numericError(setting));
            return false;
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(ChatColor.RED + ex.getMessage());
            return false;
        }
        return true;
    }

    private String formatSettingValue(String setting) {
        return switch (setting) {
            case "arenaDiameter" -> manager.getArenaDiameter() + " blocks";
            case "lavaRisingSpeed" -> formatAllPhaseSpeeds();
            case "suddenDeathDelay" -> manager.getSuddenDeathDelaySeconds() + " seconds before shrink starts";
            case "suddenDeathSpeed" -> manager.getSuddenDeathSpeedSeconds() + " seconds to shrink";
            case "suddenDeathRadius" -> manager.getSuddenDeathRadius() + " block radius ("
                    + manager.getSuddenDeathDiameter() + "x" + manager.getSuddenDeathDiameter() + " final zone)";
            case "soundsEnabled" -> Boolean.toString(manager.isSoundsEnabled());
            case "milestoneMessages" -> Boolean.toString(manager.isMilestoneMessagesEnabled());
            case "giveDirt" -> Boolean.toString(manager.isGiveDirtEnabled());
            case "pvpAtSurface" -> Boolean.toString(manager.isPvpAtSurfaceEnabled());
            case "borderDamage" -> Boolean.toString(manager.isBorderDamageEnabled());
            case "noMoreBottomDwellers" -> manager.getNoMoreBottomDwellersSeconds() == 0
                    ? "disabled"
                    : manager.getNoMoreBottomDwellersSeconds() + " seconds";
            case "buildingBlockGiveRate" -> manager.getBuildingBlockGiveRate() + " blocks per lava rise";
            default -> "unknown";
        };
    }

    private String formatAllPhaseSpeeds() {
        return Arrays.stream(manager.getLavaPhases())
                .map(phase -> phase.getId() + "=" + formatSeconds(manager.getLavaRisingSpeedSeconds(phase)) + "s")
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
    }

    private String formatPhaseSpeed(LavaRisingManager.LavaPhase phase) {
        return formatSeconds(manager.getLavaRisingSpeedSeconds(phase)) + " seconds per block";
    }

    private String canonicalSettingName(String option) {
        return switch (normalize(option)) {
            case "arenadiameter", "borderdiameter", "diameter", "border" -> "arenaDiameter";
            case "lavarisingspeed", "basespeed", "speed" -> "lavaRisingSpeed";
            case "suddendeathdelay", "suddendeathwait", "suddendeathwarning" -> "suddenDeathDelay";
            case "suddendeathspeed", "suddendeathshrink", "bordershrinkspeed" -> "suddenDeathSpeed";
            case "suddendeathradius", "finalradius", "radius" -> "suddenDeathRadius";
            case "soundsenabled", "sounds", "sound" -> "soundsEnabled";
            case "milestonemessages", "messages", "notifications" -> "milestoneMessages";
            case "givedirt", "dirt" -> "giveDirt";
            case "pvpathsurface", "pvp" -> "pvpAtSurface";
            case "borderdamage", "suffocation" -> "borderDamage";
            case "nomorebottomdwellers", "bottomdwellers", "nobottomdwellers" -> "noMoreBottomDwellers";
            case "buildingblockgiverate", "blockgiverate", "blockrate", "giverate" -> "buildingBlockGiveRate";
            default -> null;
        };
    }

    private String normalize(String value) {
        return value.replace("-", "")
                .replace("_", "")
                .replace(".", "")
                .toLowerCase(Locale.ROOT);
    }

    private boolean isBooleanSetting(String setting) {
        return switch (setting) {
            case "soundsEnabled", "milestoneMessages", "giveDirt", "pvpAtSurface", "borderDamage" -> true;
            default -> false;
        };
    }

    private Boolean parseBooleanValue(String rawValue) {
        return switch (rawValue.toLowerCase(Locale.ROOT)) {
            case "true", "on", "yes", "1" -> true;
            case "false", "off", "no", "0" -> false;
            default -> null;
        };
    }

    private boolean parseBoolean(CommandSender sender, String rawValue) {
        Boolean value = parseBooleanValue(rawValue);
        if (value == null) {
            throw new IllegalArgumentException("Boolean settings must be true/false, on/off, yes/no, or 1/0.");
        }
        return value;
    }

    private double parseFiniteDouble(String rawValue) {
        double value = Double.parseDouble(rawValue);
        if (!Double.isFinite(value)) {
            throw new NumberFormatException("Value must be finite.");
        }
        return value;
    }

    private String numericError(String setting) {
        return switch (setting) {
            case "arenaDiameter", "suddenDeathDelay", "suddenDeathSpeed", "suddenDeathRadius",
                    "noMoreBottomDwellers", "buildingBlockGiveRate" ->
                    setting + " must be a whole number.";
            case "lavaRisingSpeed" -> "lavaRisingSpeed must be a number of seconds.";
            default -> setting + " has an invalid value.";
        };
    }

    private String formatSeconds(double seconds) {
        if (Math.abs(seconds - Math.rint(seconds)) < 0.0001D) {
            return Integer.toString((int) Math.rint(seconds));
        }
        return String.format(Locale.ROOT, "%.2f", seconds);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("lavarising") && args.length == 1) {
            return filter(ROOT_COMMANDS, args[0]);
        }
        if (command.getName().equalsIgnoreCase("lavarising") && args.length == 2) {
            if (args[0].equalsIgnoreCase("settings")) {
                return filter(SETTING_OPTIONS, args[1]);
            }
            if (args[0].equalsIgnoreCase("border") || args[0].equalsIgnoreCase("diameter")) {
                return filter(Arrays.asList("50", "100", "200"), args[1]);
            }
            if (args[0].equalsIgnoreCase("speed")) {
                return filter(Arrays.asList("2", "1", "0.5"), args[1]);
            }
        }
        if (command.getName().equalsIgnoreCase("lavarising")
                && args.length == 3
                && args[0].equalsIgnoreCase("settings")) {
            String setting = canonicalSettingName(args[1]);
            if (setting == null) {
                return Collections.emptyList();
            }
            if (setting.equals("lavaRisingSpeed")) {
                return filter(phaseSuggestions(), args[2]);
            }
            if (isBooleanSetting(setting)) {
                return filter(BOOLEAN_VALUES, args[2]);
            }
            return filter(numberSuggestions(setting), args[2]);
        }
        if (command.getName().equalsIgnoreCase("lavarising")
                && args.length == 4
                && args[0].equalsIgnoreCase("settings")
                && canonicalSettingName(args[1]) != null
                && canonicalSettingName(args[1]).equals("lavaRisingSpeed")) {
            return filter(numberSuggestions("lavaRisingSpeed"), args[3]);
        }
        return Collections.emptyList();
    }

    private List<String> numberSuggestions(String setting) {
        return switch (setting) {
            case "arenaDiameter" -> Arrays.asList("50", "100", "200");
            case "lavaRisingSpeed" -> Arrays.asList("2", "1", "0.5");
            case "suddenDeathDelay" -> Arrays.asList("300", "120", "0");
            case "suddenDeathSpeed" -> Arrays.asList("300", "180", "60");
            case "suddenDeathRadius" -> Arrays.asList("1", "2", "5");
            case "noMoreBottomDwellers" -> Arrays.asList("60", "30", "0");
            case "buildingBlockGiveRate" -> Arrays.asList("16", "8", "0");
            default -> Collections.emptyList();
        };
    }

    private List<String> phaseSuggestions() {
        List<String> phases = Arrays.stream(manager.getLavaPhases())
                .flatMap(phase -> Arrays.stream(new String[] {
                        Integer.toString(phase.getId()),
                        phase.getConfigKey()
                }))
                .toList();
        List<String> suggestions = new java.util.ArrayList<>(phases);
        suggestions.add("all");
        return suggestions;
    }

    private List<String> filter(List<String> values, String token) {
        String lowerToken = token.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lowerToken))
                .toList();
    }
}
