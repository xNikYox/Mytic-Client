package de.myticlegacy.client.gui;

/** Eingabe-Ereignisse unabhängig von der Minecraft-Version (gleiche Methodennamen wie ab 1.21.9). */
public final class Input {
    private Input() {
    }

    public record Click(double x, double y, int button) {
    }

    public record Key(int key, boolean hasControlDown) {
    }

    public record Typed(String codepointAsString, boolean isAllowedChatCharacter) {
    }
}
