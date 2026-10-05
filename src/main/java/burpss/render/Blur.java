package burpss.render;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.Arrays;
import java.util.Random;

final class Blur {

    private static final String DECOY = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int MARGIN = 6;

    private Blur() {
    }

    static void decoyText(Graphics2D g, Rectangle2D r, int chars, Font font, double baseline, Color color, long seed) {
        double scale = Math.max(1, g.getTransform().getScaleX());
        int w = (int) Math.ceil((r.getWidth() + 2 * MARGIN) * scale);
        int h = (int) Math.ceil((r.getHeight() + 2 * MARGIN) * scale);
        BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D ig = img.createGraphics();
        ig.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        ig.scale(scale, scale);
        ig.setFont(font);
        ig.setColor(color);
        ig.drawString(decoy(chars, seed), MARGIN, (float) (MARGIN + baseline));
        ig.dispose();
        int radius = (int) Math.round(3 * scale);
        img = boxBlur(boxBlur(img, radius), radius);
        AffineTransform at = new AffineTransform(1 / scale, 0, 0, 1 / scale, r.getX() - MARGIN, r.getY() - MARGIN);
        g.drawImage(img, at, null);
    }

    private static String decoy(int chars, long seed) {
        Random random = new Random(seed);
        StringBuilder sb = new StringBuilder(chars);
        for (int i = 0; i < chars; i++) sb.append(DECOY.charAt(random.nextInt(DECOY.length())));
        return sb.toString();
    }

    private static BufferedImage boxBlur(BufferedImage src, int radius) {
        int size = radius * 2 + 1;
        float[] weights = new float[size];
        Arrays.fill(weights, 1f / size);
        BufferedImage h = new ConvolveOp(new Kernel(size, 1, weights), ConvolveOp.EDGE_ZERO_FILL, null).filter(src, null);
        return new ConvolveOp(new Kernel(1, size, weights), ConvolveOp.EDGE_ZERO_FILL, null).filter(h, null);
    }
}
