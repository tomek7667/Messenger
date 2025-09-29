package pl.cyberman.messenger;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

public class MessengerMod implements ClientModInitializer {
    private static final MinecraftClient MC = MinecraftClient.getInstance();

    // === persistence ===
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path CONFIG_PATH() {
        return MC.runDirectory.toPath().resolve("config/messenger.json");
    }
    private static Path EXPORT_PATH() {
        return MC.runDirectory.toPath().resolve("config/messenger_export.json");
    }
    private static final Type SAVE_LIST_TYPE = new TypeToken<List<SaveTask>>() {}.getType();

    private static class SaveTask {
        String text;
        double intervalMinutes;
        boolean enabled;
        SaveTask(String text, double intervalMinutes, boolean enabled) {
            this.text = text;
            this.intervalMinutes = intervalMinutes;
            this.enabled = enabled;
        }
    }

    /** Each message with its own (possibly fractional) minutes interval. */
    public static class MessageTask {
        public String text;
        public double intervalMinutes; // > 0
        public boolean enabled = false;
        public long nextSendMs = 0L;

        public MessageTask(String text, double intervalMinutes) {
            this.text = text;
            this.intervalMinutes = intervalMinutes;
            scheduleNext();
        }

        public void scheduleNext() {
            long ms = (long)Math.round(intervalMinutes * 60_000.0);
            this.nextSendMs = now() + Math.max(50L, ms);
        }
    }

    /** Tasks edited by commands/UI. */
    public static final List<MessageTask> TASKS = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        // Load persisted tasks on startup
        loadTasks();

        // Always-on scheduler
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null || client.player == null) return;

            long tnow = now();
            for (MessageTask t : TASKS) {
                if (!t.enabled) continue;
                if (tnow >= t.nextSendMs) {
                    sendChatAsPlayerClient(t.text); // EXACT message or command
                    t.scheduleNext();
                }
            }
        });

        // Button in ESC menu
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof GameMenuScreen) {
                int x = screen.width / 2 + 104;
                int y = screen.height / 4 + 24;
                ButtonWidget btn = ButtonWidget.builder(Text.literal("Messenger"), b -> client.setScreen(new MessengerScreen(screen)))
                        .dimensions(x, y, 98, 20).build();
                Screens.getButtons(screen).add(btn);
            }
        });

        // Commands (/messenger ...)
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                ClientCommandManager.literal("messenger")

                    .then(ClientCommandManager.literal("list")
                        .executes(ctx -> {
                            if (TASKS.isEmpty()) { info("No tasks yet. Use /messenger add <minutes> <text...>"); return 1; }
                            info("Tasks (" + TASKS.size() + "):");
                            for (int i = 0; i < TASKS.size(); i++) {
                                MessageTask t = TASKS.get(i);
                                String line = "#" + (i+1) + " [" + (t.enabled ? "on" : "off") + "] every " + t.intervalMinutes + " min  " + t.text;
                                MC.inGameHud.getChatHud().addMessage(Text.literal(line));
                            }
                            return 1;
                        }))

                    .then(ClientCommandManager.literal("add")
                        .then(ClientCommandManager.argument("minutes", StringArgumentType.word())
                            .then(ClientCommandManager.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String minStr = StringArgumentType.getString(ctx, "minutes");
                                    String txt = StringArgumentType.getString(ctx, "text");
                                    double mins = parseMinutesClient(minStr);
                                    if (Double.isNaN(mins) || mins <= 0) { err("Invalid minutes: " + minStr); return 1; }
                                    if (txt == null || txt.isBlank()) { err("Message cannot be empty."); return 1; }
                                    TASKS.add(new MessageTask(txt, mins));
                                    saveTasks();
                                    ok("Added task #" + TASKS.size() + "  every " + mins + " min");
                                    return 1;
                                }))))

                    .then(ClientCommandManager.literal("enable")
                        .then(ClientCommandManager.argument("index", IntegerArgumentType.integer(1))
                            .executes(ctx -> {
                                int idx1 = IntegerArgumentType.getInteger(ctx, "index");
                                if (!checkIndexClient(idx1)) return 1;
                                MessageTask t = TASKS.get(idx1 - 1);
                                t.enabled = true;
                                sendChatAsPlayerClient(t.text);
                                t.scheduleNext();
                                saveTasks();
                                ok("Enabled task #" + idx1 + " (sent once now)");
                                return 1;
                            })))

                    .then(ClientCommandManager.literal("disable")
                        .then(ClientCommandManager.argument("index", IntegerArgumentType.integer(1))
                            .executes(ctx -> {
                                int idx1 = IntegerArgumentType.getInteger(ctx, "index");
                                if (!checkIndexClient(idx1)) return 1;
                                MessageTask t = TASKS.get(idx1 - 1);
                                t.enabled = false;
                                saveTasks();
                                ok("Disabled task #" + idx1);
                                return 1;
                            })))

                    .then(ClientCommandManager.literal("toggle")
                        .then(ClientCommandManager.argument("index", IntegerArgumentType.integer(1))
                            .executes(ctx -> {
                                int idx1 = IntegerArgumentType.getInteger(ctx, "index");
                                if (!checkIndexClient(idx1)) return 1;
                                MessageTask t = TASKS.get(idx1 - 1);
                                t.enabled = !t.enabled;
                                if (t.enabled) {
                                    sendChatAsPlayerClient(t.text);
                                    t.scheduleNext();
                                    ok("Task #" + idx1 + " enabled (sent once now)");
                                } else {
                                    ok("Task #" + idx1 + " disabled");
                                }
                                saveTasks();
                                return 1;
                            })))

                    .then(ClientCommandManager.literal("setinterval")
                        .then(ClientCommandManager.argument("index", IntegerArgumentType.integer(1))
                            .then(ClientCommandManager.argument("minutes", StringArgumentType.word())
                                .executes(ctx -> {
                                    int idx1 = IntegerArgumentType.getInteger(ctx, "index");
                                    if (!checkIndexClient(idx1)) return 1;
                                    String minStr = StringArgumentType.getString(ctx, "minutes");
                                    double mins = parseMinutesClient(minStr);
                                    if (Double.isNaN(mins) || mins <= 0) { err("Invalid minutes: " + minStr); return 1; }
                                    MessageTask t = TASKS.get(idx1 - 1);
                                    t.intervalMinutes = mins;
                                    t.scheduleNext();
                                    saveTasks();
                                    ok("Task #" + idx1 + " interval set to " + t.intervalMinutes + " min");
                                    return 1;
                                }))))

                    .then(ClientCommandManager.literal("enableall")
                        .executes(ctx -> {
                            if (TASKS.isEmpty()) { info("No tasks to enable."); return 1; }
                            for (MessageTask t : TASKS) {
                                t.enabled = true;
                                sendChatAsPlayerClient(t.text);
                                t.scheduleNext();
                            }
                            saveTasks();
                            ok("All tasks enabled (each sent once now).");
                            return 1;
                        }))

                    .then(ClientCommandManager.literal("disableall")
                        .executes(ctx -> {
                            if (TASKS.isEmpty()) { info("No tasks to disable."); return 1; }
                            for (MessageTask t : TASKS) t.enabled = false;
                            saveTasks();
                            ok("All tasks disabled.");
                            return 1;
                        }))
            );
        });
    }

    // === persistence helpers ===

    public static void saveTasks() {
        try {
            Path p = CONFIG_PATH();
            Files.createDirectories(p.getParent());
            List<SaveTask> out = new ArrayList<>();
            for (MessageTask t : TASKS) out.add(new SaveTask(t.text, t.intervalMinutes, t.enabled));
            String json = GSON.toJson(out, SAVE_LIST_TYPE);
            Files.writeString(p, json, StandardCharsets.UTF_8);
            ok("Saved " + out.size() + " task(s).");
        } catch (Exception e) {
            err("Save failed.");
        }
    }

    public static void loadTasks() {
        try {
            Path p = CONFIG_PATH();
            if (!Files.exists(p)) return;
            String json = Files.readString(p, StandardCharsets.UTF_8);
            List<SaveTask> in = GSON.fromJson(json, SAVE_LIST_TYPE);
            TASKS.clear();
            if (in != null) {
                for (SaveTask s : in) {
                    if (s == null) continue;
                    double mins = (s.intervalMinutes > 0) ? s.intervalMinutes : 1.0;
                    MessageTask t = new MessageTask(s.text == null ? "" : s.text, mins);
                    t.enabled = s.enabled;
                    t.scheduleNext(); // fresh countdown
                    TASKS.add(t);
                }
            }
            ok("Loaded " + TASKS.size() + " task(s).");
        } catch (Exception e) {
            err("Load failed.");
        }
    }

    public static void exportTasksTo(Path p) {
        try {
            Files.createDirectories(p.getParent());
            List<SaveTask> out = new ArrayList<>();
            for (MessageTask t : TASKS) out.add(new SaveTask(t.text, t.intervalMinutes, t.enabled));
            String json = GSON.toJson(out, SAVE_LIST_TYPE);
            Files.writeString(p, json, StandardCharsets.UTF_8);
            ok("Exported " + out.size() + " task(s).");
        } catch (Exception e) {
            err("Export failed.");
        }
    }

    public static void importTasksFrom(Path p) {
        try {
            if (!Files.exists(p)) { err("File not found."); return; }
            String json = Files.readString(p, StandardCharsets.UTF_8);
            List<SaveTask> in = GSON.fromJson(json, SAVE_LIST_TYPE);
            TASKS.clear();
            if (in != null) {
                for (SaveTask s : in) {
                    if (s == null) continue;
                    double mins = (s.intervalMinutes > 0) ? s.intervalMinutes : 1.0;
                    MessageTask t = new MessageTask(s.text == null ? "" : s.text, mins);
                    t.enabled = s.enabled;
                    t.scheduleNext();
                    TASKS.add(t);
                }
            }
            saveTasks(); // also write to main config
            ok("Imported " + TASKS.size() + " task(s).");
        } catch (Exception e) {
            err("Import failed.");
        }
    }

    // simple wrappers to default export path (optional use)
    public static void exportTasks() { exportTasksTo(EXPORT_PATH()); }
    public static void importTasks() { importTasksFrom(EXPORT_PATH()); }

    // === misc helpers ===

    public static boolean checkIndexClient(int idx1) {
        if (idx1 < 1 || idx1 > TASKS.size()) {
            err("No task at index " + idx1 + ". Use /messenger list.");
            return false;
        }
        return true;
    }

    /** Parse minutes. Returns NaN if invalid. */
    public static double parseMinutesClient(String s) {
        try {
            return Double.parseDouble(s.replace(',', '.'));
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    public static long now() {
        return System.currentTimeMillis();
    }

    /** Sends exact text, or executes a command if it starts with '/'. */
    public static void sendChatAsPlayerClient(String text) {
        if (MC == null || MC.player == null || MC.player.networkHandler == null) return;
        try {
            if (text != null && text.startsWith("/")) {
                String cmd = text.substring(1);
                MC.player.networkHandler.sendChatCommand(cmd);
            } else {
                MC.player.networkHandler.sendChatMessage(text);
            }
        } catch (Throwable t) {
            try { MC.player.networkHandler.sendChatMessage(text); } catch (Throwable ignored) {}
        }
    }

    // Local-only colored messages
    public static void ok(String s)  { if (MC != null && MC.inGameHud != null) MC.inGameHud.getChatHud().addMessage(Text.literal(s).formatted(Formatting.GREEN)); }
    public static void err(String s) { if (MC != null && MC.inGameHud != null) MC.inGameHud.getChatHud().addMessage(Text.literal(s).formatted(Formatting.RED)); }
    public static void info(String s){ if (MC != null && MC.inGameHud != null) MC.inGameHud.getChatHud().addMessage(Text.literal(s).formatted(Formatting.YELLOW)); }
    public static String version() {
        try {
            return FabricLoader.getInstance()
                    .getModContainer("messenger")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("dev");
        } catch (Throwable t) {
            return "dev";
        }
    }
}