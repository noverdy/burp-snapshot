package burpss.core;

public final class Mark {

    public static final java.awt.Color[] PALETTE = {
            new java.awt.Color(0xE5484D),
            new java.awt.Color(0xF76B15),
            new java.awt.Color(0xE2A700),
            new java.awt.Color(0x30A46C),
            new java.awt.Color(0x0090FF),
            new java.awt.Color(0x8E4EC6),
    };
    public static final String[] PALETTE_NAMES = {"Red", "Orange", "Amber", "Green", "Blue", "Purple"};

    public Anchor anchor;
    public int color;
    public String note = "";
    public java.awt.geom.Point2D calloutOffset;

    public Mark(Anchor anchor, int color) {
        this.anchor = anchor;
        this.color = color;
    }

    public java.awt.Color awtColor() {
        return PALETTE[Math.floorMod(color, PALETTE.length)];
    }

    public boolean hasNote() {
        return note != null && !note.isBlank();
    }

    public Mark copy() {
        Mark m = new Mark(anchor, color);
        m.note = note;
        m.calloutOffset = calloutOffset == null ? null : (java.awt.geom.Point2D) calloutOffset.clone();
        return m;
    }
}
