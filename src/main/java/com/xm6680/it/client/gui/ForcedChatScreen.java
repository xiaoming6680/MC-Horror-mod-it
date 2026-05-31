package com.xm6680.it.client.gui;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-only chat anomaly screen. The server owns the script; local input is ignored.
 */
public class ForcedChatScreen extends ChatScreen {
    private final List<String> lines;
    private final int charIntervalTicks;
    private final int linePauseTicks;
    private int closeDelayTicks;
    private int lineIndex;
    private int visibleChars;
    private int nextCharTicks;
    private int pauseTicks;
    private boolean allowClose;

    public ForcedChatScreen(List<String> lines, int charIntervalTicks, int linePauseTicks, int closeDelayTicks) {
        super("", false);
        this.lines = sanitize(lines);
        this.charIntervalTicks = Math.max(1, charIntervalTicks);
        this.linePauseTicks = Math.max(1, linePauseTicks);
        this.closeDelayTicks = Math.max(1, closeDelayTicks);
    }

    @Override
    protected void init() {
        super.init();
        if (chatField != null) {
            chatField.setMaxLength(256);
            chatField.setEditable(false);
            chatField.setFocusUnlocked(false);
            chatField.setText("");
            chatField.setCursorToEnd(false);
            setFocused(chatField);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (allowClose || chatField == null) {
            return;
        }

        if (lineIndex >= lines.size()) {
            if (--closeDelayTicks <= 0) {
                allowClose = true;
                super.close();
            }
            return;
        }

        if (pauseTicks > 0) {
            pauseTicks--;
            if (pauseTicks == 0) {
                lineIndex++;
                visibleChars = 0;
                nextCharTicks = 0;
                setForcedText("");
            } else {
                enforceCurrentText();
            }
            return;
        }

        String line = lines.get(lineIndex);
        if (visibleChars < line.length()) {
            if (nextCharTicks <= 0) {
                visibleChars++;
                nextCharTicks = charIntervalTicks;
                setForcedText(line.substring(0, visibleChars));
            } else {
                nextCharTicks--;
                enforceCurrentText();
            }
            return;
        }

        pauseTicks = linePauseTicks;
        enforceCurrentText();
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (!allowClose) {
            enforceCurrentText();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (!allowClose) {
            enforceCurrentText();
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return allowClose;
    }

    @Override
    public void close() {
        if (allowClose) {
            super.close();
            return;
        }
        enforceCurrentText();
    }

    @Override
    public void sendMessage(String chatText, boolean addToHistory) {
        if (allowClose) {
            super.sendMessage(chatText, addToHistory);
        } else {
            enforceCurrentText();
        }
    }

    private void enforceCurrentText() {
        if (chatField == null || lineIndex >= lines.size()) {
            return;
        }
        String line = lines.get(lineIndex);
        setForcedText(line.substring(0, Math.min(visibleChars, line.length())));
    }

    private void setForcedText(String text) {
        chatField.setText(text);
        chatField.setCursorToEnd(false);
    }

    private static List<String> sanitize(List<String> source) {
        List<String> result = new ArrayList<>();
        for (String line : source) {
            if (line != null && !line.isBlank()) {
                result.add(line.length() > 160 ? line.substring(0, 160) : line);
            }
        }
        if (result.isEmpty()) {
            result.add("它在这里");
            result.add("它正在替你打字");
        }
        return result;
    }
}
