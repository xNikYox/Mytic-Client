package de.myticlegacy.client.gui.ui;

/**
 * Design-Tokens des neuen Mytic-Designs („Dunkles Glas“, Akzent „Mytic-Lila“), übernommen aus der UI-Vorschau.
 * Maße gelten für einen 1920×1080-Rahmen (1 Design-Pixel); Bildschirme multiplizieren sie mit ihrem Faktor
 * (z. B. min(Breite/1920, Höhe/1080)). Farben sind ARGB.
 * (Gleicher Code für alle Versionen, daher Java 8.)
 */
public final class Tokens {
    private Tokens() {
    }

    // ------------------------------------------------------------------ Flächen
    /** Glas: Fenster, Karten (rgba(15,12,24,.60)) */
    public static final int GLASS = 0x990F0C18;
    /** Glas stark: Popover, Dialoge, Dropdowns (rgba(13,10,21,.78)) */
    public static final int GLASS_STRONG = 0xC70D0A15;
    /** Glas ohne Weichzeichnung (1.8.9 bzw. wo kein Blur dahinter liegt): deckender */
    public static final int GLASS_OPAQUE = 0xD00F0C18;
    /** Fläche: Kacheln, Zeilen, Buttons (4,5 % Weiß) */
    public static final int SURFACE = 0x0BFFFFFF;
    public static final int SURFACE_HOVER = 0x16FFFFFF;
    public static final int SURFACE_ACTIVE = 0x1FFFFFFF;
    /** Vertiefung: Eingabefelder, Segment-Hintergrund */
    public static final int INSET = 0x47000000;
    /** 1-px-Rahmen, Trenner (9 % / 17 % Weiß) */
    public static final int LINE = 0x17FFFFFF;
    public static final int LINE_STRONG = 0x2BFFFFFF;
    /** Lichtkante oben an Glasflächen */
    public static final int HIGHLIGHT = 0x0FFFFFFF;

    // ------------------------------------------------------------------ Text
    public static final int TEXT = 0xFFF4F2FA;
    public static final int MUTED = 0xFFB2ADC4;
    public static final int SUBTLE = 0xFF8A849E;

    // ------------------------------------------------------------------ Akzent
    public static final int ACC_1 = 0xFF9B5CFF;
    public static final int ACC_MID = 0xFFC25EF0;
    public static final int ACC_2 = 0xFFEC5FBF;
    public static final int ACC = 0xFFB56CFF;
    public static final int ACC_SOFT = 0x299B5CFF;
    public static final int ACC_LINE = 0x8CB56CFF;
    /** Schrift auf Akzent-Abzeichen */
    public static final int ACC_TEXT = 0xFFE3CFFF;

    // ------------------------------------------------------------------ Status / Seltenheit
    public static final int OK = 0xFF4ADE80;
    public static final int WARN = 0xFFFACC15;
    public static final int BAD = 0xFFFF5C72;
    public static final int INFO = 0xFF60A5FA;
    public static final int R_COMMON = 0xFFA1A1B5;
    public static final int R_RARE = 0xFF5EA2FF;
    public static final int R_EPIC = 0xFFB56CFF;
    public static final int R_LEGEND = 0xFFFFB547;

    // ------------------------------------------------------------------ Radien (Design-Pixel)
    public static final int RAD_XS = 6;
    public static final int RAD_SM = 10;
    public static final int RAD_MD = 14;
    public static final int RAD_LG = 20;
    public static final int RAD_XL = 28;
    /** Pille: halbe Höhe */
    public static final int RAD_PILL = 9999;

    // ------------------------------------------------------------------ Abstände (4er-Raster)
    public static final int S1 = 4, S2 = 8, S3 = 12, S4 = 16, S5 = 24, S6 = 32, S7 = 48, S8 = 64;

    // ------------------------------------------------------------------ Typografie (Design-Pixel)
    public static final float FS_H1 = 30, FS_H2 = 21, FS_H3 = 17, FS_BODY = 16, FS_SMALL = 14.5f, FS_TINY = 13, FS_CAPTION = 12.5f;
    public static final int MEDIUM = 0, SEMIBOLD = 1;

    // ------------------------------------------------------------------ Bewegung (ms), Kurve cubic-bezier(.2,.7,.2,1)
    public static final int MS_HOVER = 180;
    public static final int MS_TOGGLE = 280;
    public static final int MS_ENTER = 350;
}
