package burpss.render;

import burpss.core.Anchor;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

public interface Content {

    void layout(double minWidth);

    double width();

    double height();

    void paint(Graphics2D g);

    Rectangle2D bounds(Anchor anchor);

    default List<Rectangle2D> segments(Anchor anchor) {
        Rectangle2D box = bounds(anchor);
        return box == null ? List.of() : List.of(box);
    }

    Anchor anchorAt(Point2D p);

    Anchor anchorBetween(Point2D from, Point2D to);

    int ink(Rectangle2D area);
}
