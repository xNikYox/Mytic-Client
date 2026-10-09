package de.myticlegacy.client.gui;

/** Versionsunabhängige Eingabe-Ereignisse (statt der Records der neuen Versionen). */
public final class Input {
    private Input() {
    }

    public static final class Click {
        private final double x;
        private final double y;
        private final int button;

        public Click(double x, double y, int button) {
            this.x = x;
            this.y = y;
            this.button = button;
        }

        public double x() {
            return x;
        }

        public double y() {
            return y;
        }

        public int button() {
            return button;
        }
    }

    public static final class Key {
        private final int key;
        private final boolean hasControlDown;

        public Key(int key, boolean hasControlDown) {
            this.key = key;
            this.hasControlDown = hasControlDown;
        }

        public int key() {
            return key;
        }

        public boolean hasControlDown() {
            return hasControlDown;
        }
    }

    public static final class Typed {
        private final String text;
        private final boolean allowed;

        public Typed(String text, boolean allowed) {
            this.text = text;
            this.allowed = allowed;
        }

        public String codepointAsString() {
            return text;
        }

        public boolean isAllowedChatCharacter() {
            return allowed;
        }
    }
}
