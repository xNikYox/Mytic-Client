package de.myticlegacy.client.module;

public enum Category {
    HUD("HUD"),
    MECHANIK("Mechanik"),
    VISUELL("Visuell"),
    CLIENT("Client");

    public final String label;

    Category(String label) {
        this.label = label;
    }
}
