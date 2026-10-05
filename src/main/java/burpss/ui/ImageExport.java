package burpss.ui;

import burpss.core.Settings;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.SystemFlavorMap;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

final class ImageExport {

    private ImageExport() {
    }

    private static final DataFlavor PNG = pngFlavor();

    static void copy(BufferedImage image) throws IOException {
        byte[] png = encode(image);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new Transferable() {
            public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{PNG, DataFlavor.imageFlavor}; }
            public boolean isDataFlavorSupported(DataFlavor f) { return PNG.equals(f) || DataFlavor.imageFlavor.equals(f); }
            public Object getTransferData(DataFlavor f) { return PNG.equals(f) ? new ByteArrayInputStream(png) : image; }
        }, null);
    }

    private static DataFlavor pngFlavor() {
        DataFlavor flavor = new DataFlavor("image/png;class=java.io.InputStream", "PNG image");
        if (SystemFlavorMap.getDefaultFlavorMap() instanceof SystemFlavorMap map) {
            map.addUnencodedNativeForFlavor(flavor, "PNG");
            map.addFlavorForUnencodedNative("PNG", flavor);
        }
        return flavor;
    }

    private static byte[] encode(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    static boolean save(Component parent, BufferedImage image, Settings settings, String suggestedName) {
        JFileChooser chooser = new JFileChooser(settings.lastSaveDir.isBlank() ? null : new File(settings.lastSaveDir));
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), suggestedName));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return false;
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".png")) file = new File(file.getParentFile(), file.getName() + ".png");
        if (file.exists() && JOptionPane.showConfirmDialog(parent, file.getName() + " exists. Overwrite?", "Save PNG",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return false;
        try {
            ImageIO.write(image, "png", file);
            settings.lastSaveDir = file.getParent();
            return true;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(parent, "Could not save: " + e.getMessage(), "Save PNG", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    static String fileName(String host, String method, String path) {
        String base = java.time.LocalDate.now() + "_" + host + "_" + method + "_" + path;
        String clean = base.replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("_+", "_").replaceAll("_$", "");
        return (clean.length() > 120 ? clean.substring(0, 120) : clean) + ".png";
    }
}
