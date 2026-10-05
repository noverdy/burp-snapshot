package burpss.core;

import java.awt.geom.Point2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

public final class StateCodec {

    private static final int VERSION = 1;

    private StateCodec() {
    }

    public static byte[] encode(EditState s) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(VERSION);
            out.writeUTF(s.title);
            out.writeInt(s.marks.size());
            for (Mark m : s.marks) {
                writeAnchor(out, m.anchor);
                out.writeInt(m.color);
                out.writeUTF(m.note);
                out.writeBoolean(m.calloutOffset != null);
                if (m.calloutOffset != null) {
                    out.writeDouble(m.calloutOffset.getX());
                    out.writeDouble(m.calloutOffset.getY());
                }
            }
            for (int pane = 0; pane < 2; pane++) {
                out.writeInt(s.manualRedactions.get(pane).size());
                for (TextRange r : s.manualRedactions.get(pane)) {
                    out.writeInt(r.start());
                    out.writeInt(r.end());
                }
                out.writeInt(s.suppressedAuto.get(pane).size());
                for (String key : s.suppressedAuto.get(pane)) out.writeUTF(key);
                out.writeInt(s.toggledHeaders.get(pane).size());
                for (int line : s.toggledHeaders.get(pane)) out.writeInt(line);
            }
            out.writeInt(s.payloadOverrides.size());
            for (Map.Entry<Integer, String> e : s.payloadOverrides.entrySet()) {
                out.writeInt(e.getKey());
                out.writeUTF(e.getValue());
            }
            out.writeInt(s.hiddenRows.size());
            for (int row : s.hiddenRows) out.writeInt(row);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    public static EditState decode(byte[] data) {
        EditState s = new EditState();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            if (in.readInt() != VERSION) return s;
            s.title = in.readUTF();
            for (int i = in.readInt(); i > 0; i--) {
                Mark m = new Mark(readAnchor(in), in.readInt());
                m.note = in.readUTF();
                if (in.readBoolean()) m.calloutOffset = new Point2D.Double(in.readDouble(), in.readDouble());
                s.marks.add(m);
            }
            for (int pane = 0; pane < 2; pane++) {
                for (int i = in.readInt(); i > 0; i--) s.manualRedactions.get(pane).add(new TextRange(in.readInt(), in.readInt()));
                for (int i = in.readInt(); i > 0; i--) s.suppressedAuto.get(pane).add(in.readUTF());
                for (int i = in.readInt(); i > 0; i--) s.toggledHeaders.get(pane).add(in.readInt());
            }
            for (int i = in.readInt(); i > 0; i--) s.payloadOverrides.put(in.readInt(), in.readUTF());
            for (int i = in.readInt(); i > 0; i--) s.hiddenRows.add(in.readInt());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return s;
    }

    private static void writeAnchor(DataOutputStream out, Anchor anchor) throws IOException {
        if (anchor instanceof Anchor.Text t) {
            out.writeByte(0);
            out.writeInt(t.pane());
            out.writeInt(t.start());
            out.writeInt(t.end());
        } else if (anchor instanceof Anchor.Rows r) {
            out.writeByte(1);
            out.writeInt(r.first());
            out.writeInt(r.last());
        }
    }

    private static Anchor readAnchor(DataInputStream in) throws IOException {
        return in.readByte() == 0
                ? new Anchor.Text(in.readInt(), in.readInt(), in.readInt())
                : new Anchor.Rows(in.readInt(), in.readInt());
    }
}
