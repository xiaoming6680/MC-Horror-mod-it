package com.xm6680.it.client.gui;

import com.xm6680.it.client.chase.ChaseOverlayRenderer;
import com.xm6680.it.analog.ReceiverMessageType;
import com.xm6680.it.config.ItConfig;
import com.xm6680.it.config.ItConfigManager;
import com.xm6680.it.network.ItNetwork;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Random;

public class ReceiverScreen extends Screen {
    private static final String CAVE_STALKER_TRAP_MESSAGE = "看看你的后面...";
    private static final String CAVE_STALKER_WARNING_MESSAGE = "矿洞下方有移动信号。";
    private static final String CAVE_STALKER_BLOCKED_MESSAGE = "矿洞信号中断。";

    private ItNetwork.OpenReceiverPayload payload;
    private static ItNetwork.OpenReceiverPayload cachedPayload;
    private final Random random = new Random();

    public ReceiverScreen(ItNetwork.OpenReceiverPayload payload) {
        super(Text.literal("旧式接收器"));
        this.payload = payload;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        int panelWidth = Math.min(360, width - 32);
        int panelHeight = Math.min(230, height - 32);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        int right = left + panelWidth;
        int bottom = top + panelHeight;

        context.fill(0, 0, width, height, 0xD0000000);
        context.fill(left, top, right, bottom, 0xFF161616);
        context.fill(left + 5, top + 5, right - 5, bottom - 5, 0xFF252525);
        context.fill(left + 18, top + 35, right - 18, bottom - 34, 0xFF07140C);
        context.drawStrokedRectangle(left + 14, top + 31, panelWidth - 28, panelHeight - 62, 0xFF425846);

        drawScanlines(context, left + 18, top + 35, right - 18, bottom - 34);

        List<ItNetwork.ReceiverMessagePayload> messages = payload.messages();
        String caveStalkerCorruptionMessage = caveStalkerCorruptionMessage(messages);
        if (caveStalkerCorruptionMessage != null) {
            drawCaveStalkerReceiverCorruption(context, left, top, right, bottom, caveStalkerCorruptionMessage);
            return;
        }

        if (ChaseOverlayRenderer.isReceiverSignalActive()) {
            drawChaseReceiverCorruption(context, left, top, right, bottom);
            return;
        }

        int titleColor = 0xFFC9A45D;
        int lcdColor = 0xFF9CD69A;
        int mutedColor = 0xFF6B8F68;
        context.drawTextWithShadow(textRenderer, "旧式接收器", left + 22, top + 13, titleColor);
        context.drawText(textRenderer, "信号：" + signalName(), left + 26, top + 44, lcdColor, false);
        context.drawText(textRenderer, "信号强度：" + signalStrengthName(), left + 26, top + 57, lcdColor, false);
        context.drawText(textRenderer, "状态：记录中", right - 92, top + 44, mutedColor, false);

        int y = top + 78;
        if (messages.isEmpty()) {
            context.drawText(textRenderer, "没有接收到信号", left + 28, y + 20, 0xFF7FA37A, false);
        } else {
            int maxLines = Math.max(1, ((bottom - 54) - y) / 15 + 1);
            int startIndex = Math.max(0, messages.size() - maxLines);
            for (int i = startIndex; i < messages.size(); i++) {
                ItNetwork.ReceiverMessagePayload message = messages.get(i);
                boolean stale = i < messages.size() - 1;
                drawReceiverLine(context, message, left + 28, y, right - 28, stale);
                y += 15;
                if (y > bottom - 54) {
                    break;
                }
            }
        }

        context.drawText(textRenderer, "按 ESC 关闭", left + 22, bottom - 22, 0xFF8A8A8A, false);
    }

    private void drawChaseReceiverCorruption(DrawContext context, int left, int top, int right, int bottom) {
        int titleColor = 0xFFFF5A4E;
        int noiseColor = 0xFF4D6A4C;
        int messageColor = 0xFFFF3333;
        context.drawTextWithShadow(textRenderer, "旧式接收器", left + 22, top + 13, titleColor);
        context.drawText(textRenderer, "信号：贴近", left + 26, top + 44, titleColor, false);
        context.drawText(textRenderer, "信号强度：" + signalStrengthName(), left + 26, top + 57, titleColor, false);
        context.drawText(textRenderer, "状态：锁定", right - 92, top + 44, titleColor, false);

        int noiseLeft = left + 28;
        int noiseRight = right - 28;
        boolean warningOnly = !ChaseOverlayRenderer.isActive();
        for (int i = 0; i < 7; i++) {
            int y = top + 78 + i * 12;
            String noise = randomNoiseLine(18 + random.nextInt(14));
            if (random.nextDouble() < 0.55) {
                context.drawText(textRenderer, fitText(noise, noiseRight - noiseLeft), noiseLeft + random.nextInt(9) - 4, y, noiseColor, false);
            }
        }

        String headline = warningOnly ? "追猎预警" : "追猎信号锁定";
        String message = warningOnly
                ? "信号源正在接近。不要回头。"
                : "追猎信号已锁定。贴近掌心时，距离会自己浮出来。";
        context.fill(left + 24, bottom - 73, right - 24, bottom - 43, 0x77120000);
        drawBoldText(context, headline, noiseLeft, bottom - 68, 0xFFFF6A5C);
        drawBoldText(context, fitText(message, noiseRight - noiseLeft), noiseLeft, bottom - 55, messageColor);
        context.drawText(textRenderer, "按 ESC 关闭", left + 22, bottom - 22, 0xFF8A8A8A, false);
    }

    private void drawCaveStalkerReceiverCorruption(DrawContext context, int left, int top, int right, int bottom, String message) {
        int noiseLeft = left + 28;
        int noiseRight = right - 28;
        for (int i = 0; i < 12; i++) {
            int y = top + 30 + i * 13;
            String noise = randomNoiseLine(18 + random.nextInt(18));
            int color = random.nextBoolean() ? 0xFF4B1C1C : 0xFF335033;
            context.drawText(textRenderer, fitText(noise, noiseRight - noiseLeft), noiseLeft + random.nextInt(15) - 7, y, color, false);
        }

        for (int y = top + 36; y < bottom - 32; y += 5) {
            if (random.nextDouble() < 0.68D) {
                context.fill(left + 20, y, right - 20, y + 1, 0x33150000);
            }
        }

        int boxTop = (top + bottom) / 2 - 16;
        context.fill(left + 24, boxTop, right - 24, boxTop + 35, 0xBB090000);
        String line = fitText(message, noiseRight - noiseLeft);
        drawBoldText(context, line, (left + right - textRenderer.getWidth(line)) / 2, boxTop + 13, 0xFFFF2424);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void drawReceiverLine(DrawContext context, ItNetwork.ReceiverMessagePayload message, int x, int y, int maxX, boolean stale) {
        ItConfig config = ItConfigManager.getConfig();
        boolean corrupt = config.enablePhaseFiveReceiverCorruption;
        int drawX = x;
        int color = stale ? staleReceiverLineColor(message) : receiverLineColor(message);
        String text = message.text();
        String line = fitText(text, maxX - x);

        if (corrupt && !config.reduceFlashingEffects && random.nextDouble() < 0.18) {
            drawX += random.nextInt(5) - 2;
            color = stale ? 0xFF8B8B7B : isCriticalReceiverLine(message) ? 0xFFFF4D4D : 0xFFC4A35C;
            line = corruptText(line);
        }

        Text rendered;
        if (message.typeOrdinal() == ReceiverMessageType.CAVE_STALKER.ordinal()) {
            rendered = stale ? Text.literal(line).formatted(Formatting.BOLD, Formatting.STRIKETHROUGH) : Text.literal(line).formatted(Formatting.BOLD);
        } else {
            rendered = stale ? Text.literal(line).formatted(Formatting.STRIKETHROUGH) : Text.literal(line);
        }
        context.drawText(textRenderer, rendered, drawX, y, color, false);
    }

    private String caveStalkerCorruptionMessage(List<ItNetwork.ReceiverMessagePayload> messages) {
        if (messages.isEmpty()) {
            return null;
        }

        ItNetwork.ReceiverMessagePayload latest = messages.get(messages.size() - 1);
        if (latest.typeOrdinal() != ReceiverMessageType.CAVE_STALKER.ordinal()) {
            return null;
        }

        String text = latest.text();
        if (text.contains(CAVE_STALKER_TRAP_MESSAGE)) {
            return CAVE_STALKER_TRAP_MESSAGE;
        }

        if (text.contains(CAVE_STALKER_WARNING_MESSAGE)) {
            return CAVE_STALKER_WARNING_MESSAGE;
        }

        if (text.contains(CAVE_STALKER_BLOCKED_MESSAGE)) {
            return CAVE_STALKER_BLOCKED_MESSAGE;
        }

        return null;
    }

    private void drawBoldText(DrawContext context, String text, int x, int y, int color) {
        Text bold = Text.literal(text).formatted(Formatting.BOLD);
        context.drawText(textRenderer, bold, x, y, color, false);
    }

    private int staleReceiverLineColor(ItNetwork.ReceiverMessagePayload message) {
        if (isCriticalReceiverLine(message)) {
            return 0xFFB77777;
        }

        return switch (message.typeOrdinal()) {
            case 0 -> 0xFF70866D;
            case 1 -> 0xFF9E9465;
            case 2 -> 0xFF78977D;
            case 3 -> 0xFFB59668;
            case 4 -> 0xFFB07B60;
            case 5 -> 0xFFB46B6B;
            case 6 -> 0xFFB95D5D;
            case 8 -> 0xFFC45A5A;
            default -> 0xFF748A72;
        };
    }

    private int receiverLineColor(ItNetwork.ReceiverMessagePayload message) {
        if (isCriticalReceiverLine(message)) {
            return 0xFFFF4D4D;
        }

        return switch (message.typeOrdinal()) {
            case 0 -> 0xFF8FBF8A;
            case 1 -> 0xFFD4C06A;
            case 2 -> 0xFF9BD1A2;
            case 3 -> 0xFFFFC069;
            case 4 -> 0xFFFF8A55;
            case 5 -> 0xFFFF6666;
            case 6 -> 0xFFFF3D3D;
            case 8 -> 0xFFFF2A2A;
            default -> 0xFF98D295;
        };
    }

    private boolean isCriticalReceiverLine(ItNetwork.ReceiverMessagePayload message) {
        String text = message.text();
        return message.typeOrdinal() >= 5
                || text.contains("你")
                || text.contains("目标")
                || text.contains("独处")
                || text.contains("地表下")
                || text.contains("贴近")
                || text.contains("很近")
                || text.contains("Y=");
    }

    private String fitText(String text, int maxWidth) {
        if (textRenderer.getWidth(text) <= maxWidth) {
            return text;
        }

        String value = text;
        while (value.length() > 4 && textRenderer.getWidth(value + "...") > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }

        return value + "...";
    }

    private String corruptText(String text) {
        if (text.length() < 8) {
            return text;
        }

        int index = random.nextInt(text.length() - 3);
        return text.substring(0, index) + "##" + text.substring(index + 2);
    }

    private String randomNoiseLine(int length) {
        String chars = "#%&/\\-_=+0110▓▒░";
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(chars.charAt(random.nextInt(chars.length())));
        }

        return builder.toString();
    }

    private void drawScanlines(DrawContext context, int left, int top, int right, int bottom) {
        for (int y = top; y < bottom; y += 4) {
            context.fill(left, y, right, y + 1, 0x22000000);
        }
    }

    private String signalName() {
        if (payload.phaseNumber() >= 5) {
            return "来自内部";
        }

        if (payload.phaseNumber() >= 3) {
            return "不稳定";
        }

        return payload.messages().isEmpty() ? "微弱" : "本地";
    }

    private String signalStrengthName() {
        int bars = Math.max(1, Math.min(5, payload.signalStrengthBars()));
        return "▮".repeat(bars) + "▯".repeat(5 - bars);
    }

    public static void open(ItNetwork.OpenReceiverPayload payload) {
        cachedPayload = payload;
        MinecraftClient.getInstance().setScreen(new ReceiverScreen(payload));
    }

    public static void sync(ItNetwork.ReceiverMessagesSyncPayload payload) {
        ItNetwork.OpenReceiverPayload updated = new ItNetwork.OpenReceiverPayload(
                payload.phaseNumber(),
                payload.phaseName(),
                payload.watchingLevel(),
                payload.signalStrengthBars(),
                payload.messages()
        );
        cachedPayload = updated;
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.currentScreen instanceof ReceiverScreen receiverScreen) {
                receiverScreen.updateMessages(updated);
            }
        });
    }

    public void updateMessages(ItNetwork.OpenReceiverPayload payload) {
        this.payload = payload;
    }
}
