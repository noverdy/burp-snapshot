package burpss.ui;

import burpss.core.Anchor;
import burpss.render.Scene;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

final class Canvas extends JComponent {

    interface Handler {
        void click(Point2D imagePoint, int clickCount);

        void drag(Point2D from, Point2D to);

        void popup(Point2D imagePoint, Point screenPoint);

        void calloutMoveStarted();

        void calloutMoved(int markIndex, Point2D offset);

        void zoomChanged(double zoom);
    }

    private static final int GUTTER = 24;
    private static final double MIN_ZOOM = 0.1, MAX_ZOOM = 5;

    private final Handler handler;
    private Scene scene;
    private double zoom = 1;
    private double requestedZoom;
    private Point2D dragFrom, dragTo, calloutStart;
    private int draggedCallout = -1;
    private boolean calloutMoved;

    Canvas(Handler handler) {
        this.handler = handler;
        setOpaque(true);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (e.isPopupTrigger()) {
                    handler.popup(toImage(e.getPoint()), e.getPoint());
                    return;
                }
                if (!SwingUtilities.isLeftMouseButton(e) || scene == null) return;
                Point2D p = toImage(e.getPoint());
                draggedCallout = scene.calloutAt(p);
                calloutMoved = false;
                if (draggedCallout >= 0) calloutStart = scene.calloutOffset(draggedCallout);
                dragFrom = p;
                dragTo = null;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragFrom == null) return;
                Point2D p = toImage(e.getPoint());
                if (draggedCallout >= 0) {
                    if (!calloutMoved) handler.calloutMoveStarted();
                    calloutMoved = true;
                    handler.calloutMoved(draggedCallout, new Point2D.Double(
                            calloutStart.getX() + p.getX() - dragFrom.getX(), calloutStart.getY() + p.getY() - dragFrom.getY()));
                } else {
                    dragTo = p;
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    handler.popup(toImage(e.getPoint()), e.getPoint());
                    return;
                }
                if (dragFrom == null) return;
                Point2D from = dragFrom, to = dragTo;
                boolean wasCallout = draggedCallout >= 0 && calloutMoved;
                dragFrom = dragTo = null;
                draggedCallout = -1;
                repaint();
                if (wasCallout) return;
                if (to != null && from.distance(to) > 4 / zoom) handler.drag(from, to);
                else handler.click(from, e.getClickCount());
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (scene == null) return;
                boolean overCallout = scene.calloutAt(toImage(e.getPoint())) >= 0;
                setCursor(Cursor.getPredefinedCursor(overCallout ? Cursor.MOVE_CURSOR : Cursor.CROSSHAIR_CURSOR));
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(this::wheel);
        Gestures.onMagnify(this, magnification -> zoomBy(1 + magnification, getMousePosition()));
    }

    void setScene(Scene scene) {
        this.scene = scene;
        refreshZoom();
    }

    void setRequestedZoom(double requestedZoom) {
        this.requestedZoom = requestedZoom;
        refreshZoom();
    }

    void refreshZoom() {
        zoom = requestedZoom > 0 ? requestedZoom : fitZoom();
        revalidate();
        repaint();
    }

    private void wheel(MouseWheelEvent e) {
        if (e.isControlDown() || e.isMetaDown()) {
            zoomBy(Math.pow(1.1, -e.getPreciseWheelRotation()), e.getPoint());
            e.consume();
            return;
        }
        Component scroll = SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        if (scroll != null) scroll.dispatchEvent(SwingUtilities.convertMouseEvent(this, e, scroll));
    }

    private void zoomBy(double factor, Point anchor) {
        if (scene == null || !(getParent() instanceof JViewport viewport)) return;
        Point pivot = anchor != null ? anchor : new Point(getWidth() / 2, getHeight() / 2);
        Point2D image = toImage(pivot);
        Point view = viewport.getViewPosition();
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));
        requestedZoom = zoom;
        setSize(getPreferredSize());
        viewport.validate();
        double x = originX() + image.getX() * zoom - (pivot.x - view.x);
        double y = GUTTER + image.getY() * zoom - (pivot.y - view.y);
        Dimension extent = viewport.getExtentSize();
        viewport.setViewPosition(new Point(
                (int) Math.max(0, Math.min(x, getWidth() - extent.width)),
                (int) Math.max(0, Math.min(y, getHeight() - extent.height))));
        repaint();
        handler.zoomChanged(zoom);
    }

    private double fitZoom() {
        if (!(getParent() instanceof JViewport viewport) || scene == null || viewport.getWidth() == 0) return 1;
        return Math.min(1, (viewport.getWidth() - 2.0 * GUTTER) / scene.width());
    }

    @Override
    public Dimension getPreferredSize() {
        if (scene == null) return new Dimension(400, 300);
        return new Dimension((int) (scene.width() * zoom) + 2 * GUTTER, (int) (scene.height() * zoom) + 2 * GUTTER);
    }

    private double originX() {
        return Math.max(GUTTER, (getWidth() - scene.width() * zoom) / 2);
    }

    private Point2D toImage(Point p) {
        return new Point2D.Double((p.getX() - originX()) / zoom, (p.getY() - GUTTER) / zoom);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
        if (scene == null) return;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(originX(), GUTTER);
        g.setColor(new Color(0, 0, 0, 40));
        g.fillRect(2, 3, (int) (scene.width() * zoom), (int) (scene.height() * zoom));
        g.scale(zoom, zoom);
        scene.paint(g);
        paintSelection(g);
        g.dispose();
    }

    private void paintSelection(Graphics2D g) {
        if (dragFrom == null || dragTo == null) return;
        Anchor anchor = scene.content().anchorBetween(scene.toContent(dragFrom), scene.toContent(dragTo));
        Rectangle2D local = anchor == null ? null : scene.content().bounds(anchor);
        if (local == null) return;
        Rectangle2D r = scene.contentRectInImage(local);
        g.setColor(new Color(0x0090FF));
        g.setStroke(new BasicStroke(1.5f / (float) zoom, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4, 3}, 0));
        g.draw(r);
        g.setColor(new Color(0, 144, 255, 30));
        g.fill(r);
    }
}
