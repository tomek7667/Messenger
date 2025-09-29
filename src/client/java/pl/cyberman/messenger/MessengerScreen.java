package pl.cyberman.messenger;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class MessengerScreen extends Screen {
    private final Screen parent;

    // Add form (top)
    private TextFieldWidget addMinutesField;
    private TextFieldWidget addMessageField;

    // Layout / paging
    private int panelX, panelY, panelW;
    private int listTopY;
    private int rowHeight = 26;
    private int firstIndex = 0; // index of first visible rule
    private int maxRows = 0;

    // Track row widgets to rebuild on page change
    private final List<ButtonWidget> rowButtons = new ArrayList<>();
    private final List<TextFieldWidget> rowMinuteFields = new ArrayList<>();
    private final List<TextFieldWidget> rowMessageFields = new ArrayList<>();

    public MessengerScreen(Screen parent) {
        super(Text.literal("Messenger"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        computeLayout();

        // --- Add form (top) ---
        addMinutesField = new TextFieldWidget(this.textRenderer, panelX, panelY, 70, 20, Text.literal("Minutes"));
        addMinutesField.setText("1.0");
        this.addDrawableChild(addMinutesField);

        int msgW = Math.max(180, panelW - 70 - 70 - 10);
        addMessageField = new TextFieldWidget(this.textRenderer, panelX + 75, panelY, msgW, 20, Text.literal("Message"));
        this.addDrawableChild(addMessageField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Add"), b -> {
            String minStr = addMinutesField.getText();
            String msg = addMessageField.getText();
            double mins = MessengerMod.parseMinutesClient(minStr); // NaN if invalid
            if (Double.isNaN(mins) || mins <= 0) { MessengerMod.err("Invalid minutes: " + minStr); return; }
            if (msg == null || msg.isBlank()) { MessengerMod.err("Message cannot be empty."); return; }
            MessengerMod.TASKS.add(new MessengerMod.MessageTask(msg, mins));
            MessengerMod.ok("Added task #" + MessengerMod.TASKS.size() + "  every " + mins + " min");
            addMessageField.setText("");
            jumpToLastPage();
            rebuildRowWidgets();
        }).dimensions(panelX + 75 + msgW + 5, panelY, 60, 20).build());

        listTopY = panelY + 34; // space for divider

        // Nav + Done buttons
        int bottomY = this.height - 28;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Prev"), b -> {
            if (firstIndex > 0) { firstIndex = Math.max(0, firstIndex - maxRows); rebuildRowWidgets(); }
        }).dimensions(panelX, bottomY, 60, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Next"), b -> {
            int total = MessengerMod.TASKS.size();
            if (firstIndex + maxRows < total) { firstIndex = Math.min(Math.max(0, total - 1), firstIndex + maxRows); rebuildRowWidgets(); }
        }).dimensions(panelX + 65, bottomY, 60, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
            .dimensions(this.width / 2 - 40, bottomY, 80, 20).build());

        rebuildRowWidgets();
    }

    private void computeLayout() {
        panelW = Math.min(560, this.width - 40); // responsive width with margin
        panelX = this.width / 2 - panelW / 2;
        panelY = 36;
        int availableListHeight = this.height - panelY - 34 /*add form+divider*/ - 40 /*nav+done*/;
        maxRows = Math.max(1, availableListHeight / rowHeight);
    }

    private void jumpToLastPage() {
        int total = MessengerMod.TASKS.size();
        if (total <= maxRows) firstIndex = 0;
        else firstIndex = ((int)Math.ceil(total / (double)maxRows) - 1) * maxRows;
    }

    private void clearRowWidgets() {
        for (ButtonWidget b : rowButtons) remove(b);
        for (TextFieldWidget f : rowMinuteFields) remove(f);
        for (TextFieldWidget f : rowMessageFields) remove(f);
        rowButtons.clear();
        rowMinuteFields.clear();
        rowMessageFields.clear();
    }

    private Text coloredOnOff(boolean on) {
        return Text.literal(on ? "ON" : "OFF")
            .styled(s -> s.withColor(on ? Formatting.GREEN : Formatting.RED));
    }

    private String formatRemaining(long ms) {
        if (ms <= 0) return "now";
        long totalSec = ms / 1000;
        long m = totalSec / 60;
        long s = totalSec % 60;
        if (m >= 60) {
            long h = m / 60;
            long m2 = m % 60;
            return String.format("%dh %02dm", h, m2);
        }
        return String.format("%d:%02d", m, s);
    }

    private void rebuildRowWidgets() {
        clearRowWidgets();

        int total = MessengerMod.TASKS.size();
        int end = Math.min(total, firstIndex + maxRows);

        for (int i = firstIndex; i < end; i++) {
            final int idx1 = i + 1;
            MessengerMod.MessageTask t = MessengerMod.TASKS.get(i);
            int rowY = listTopY + (i - firstIndex) * rowHeight;

            int x = panelX;

            // Minutes field
            TextFieldWidget minutesField = new TextFieldWidget(this.textRenderer, x + 32, rowY + 2, 54, 20, Text.literal("Minutes"));
            minutesField.setText(String.valueOf(t.intervalMinutes));
            this.addDrawableChild(minutesField);
            rowMinuteFields.add(minutesField);

            // Message field (flex width). Timer (reserve 80px) + buttons (42 + 40 + 28 + 8)
            int reservedTimer = 80;
            int buttonsW = 42 + 40 + 28 + 8; // ON/OFF + Edit + Del + pad
            int gap = 24; int msgX = x + 32 + 54 + gap;
            int msgW = Math.max(120, panelW - (msgX - panelX) - (reservedTimer + buttonsW));
            TextFieldWidget messageField = new TextFieldWidget(this.textRenderer, msgX, rowY + 2, msgW, 20, Text.literal("Message"));
            messageField.setText(t.text);
            this.addDrawableChild(messageField);
            rowMessageFields.add(messageField);

            int rightStart = panelX + panelW - (buttonsW);

            // ON/OFF
            ButtonWidget toggle = ButtonWidget.builder(coloredOnOff(t.enabled), b -> {
                t.enabled = !t.enabled;
                if (t.enabled) {
                    MessengerMod.sendChatAsPlayerClient(t.text);
                    t.scheduleNext();
                    MessengerMod.ok("Task #" + idx1 + " enabled (sent once now)");
                } else {
                    MessengerMod.ok("Task #" + idx1 + " disabled");
                }
                b.setMessage(coloredOnOff(t.enabled));
            }).dimensions(rightStart, rowY + 2, 42, 20).build();
            this.addDrawableChild(toggle);
            rowButtons.add(toggle);

            // Edit
            ButtonWidget save = ButtonWidget.builder(Text.literal("Edit"), b -> {
                String minStr = minutesField.getText();
                String newMsg = messageField.getText();
                if (newMsg == null || newMsg.isBlank()) { MessengerMod.err("Message cannot be empty."); return; }
                double mins = MessengerMod.parseMinutesClient(minStr);
                if (Double.isNaN(mins) || mins <= 0) { MessengerMod.err("Invalid minutes: " + minStr); return; }
                t.intervalMinutes = mins;
                t.text = newMsg;
                t.scheduleNext();
                MessengerMod.ok("Task #" + idx1 + " updated.");
            }).dimensions(rightStart + 44, rowY + 2, 40, 20).build();
            this.addDrawableChild(save);
            rowButtons.add(save);

            // Del
            ButtonWidget remove = ButtonWidget.builder(Text.literal("Del").formatted(Formatting.RED), b -> {
                if (idx1 >= 1 && idx1 <= MessengerMod.TASKS.size()) {
                    MessengerMod.TASKS.remove(idx1 - 1);
                    MessengerMod.ok("Removed task #" + idx1);
                    int totalNow = MessengerMod.TASKS.size();
                    if (firstIndex >= totalNow) firstIndex = Math.max(0, firstIndex - maxRows);
                    rebuildRowWidgets();
                }
            }).dimensions(rightStart + 86, rowY + 2, 28, 20).build();
            this.addDrawableChild(remove);
            rowButtons.add(remove);
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // 1) Backdrop + divider
        ctx.fill(0, 0, this.width, this.height, 0x88000000);
        int hrY = panelY + 24;
        ctx.fill(panelX, hrY, panelX + panelW, hrY + 1, 0x33FFFFFF);

        // 2) Draw all widgets
        super.render(ctx, mouseX, mouseY, delta);

        // 3) Draw text overlays AFTER widgets so they stay visible
        ctx.drawCenteredTextWithShadow(this.textRenderer, "Messenger", this.width / 2, 12, 0xFFFFFFFF);

        // Top form labels (with shadow so they pop)
        ctx.drawTextWithShadow(this.textRenderer, "Minutes", panelX, panelY - 14, 0xFFFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer, "Message", panelX + 75, panelY - 14, 0xFFFFFFFF);

        // Row IDs + tiny "min" + countdown, all with shadow
        int total = MessengerMod.TASKS.size();
        int end = Math.min(total, firstIndex + maxRows);

        for (int i = firstIndex; i < end; i++) {
            MessengerMod.MessageTask t = MessengerMod.TASKS.get(i);
            int rowY = listTopY + (i - firstIndex) * rowHeight + 7;

            // Left-side ID
            ctx.drawTextWithShadow(this.textRenderer, "#" + (i + 1), panelX, rowY, 0xFFAAAAAA);

            // tiny "min" after minutes box
            int gap = 24; int msgX = panelX + 32 + 54 + gap; int minW = this.textRenderer.getWidth("min"); int minLabelX = msgX - gap + (gap - minW)/2; ctx.drawTextWithShadow(this.textRenderer, "min", minLabelX, rowY, 0xFFAAAAAA);

            // countdown right before buttons
            String timerText = t.enabled ? "next: " + formatRemaining(Math.max(0, t.nextSendMs - MessengerMod.now())) : "paused";
            int timerWidth = this.textRenderer.getWidth(timerText);
            int buttonsW = 42 + 40 + 28 + 8;
            int timerX = panelX + panelW - buttonsW - 6 - timerWidth;
            int timerColor = t.enabled ? 0xFF77FF77 : 0xFFFF7777;
            ctx.drawTextWithShadow(this.textRenderer, timerText, timerX, rowY, timerColor);
        }
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        super.resize(client, width, height);
        this.clearChildren();
        this.init(client, width, height);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}


