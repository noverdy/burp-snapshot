package burpss.render;

import burpss.core.Anchor;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

public interface Content {

    void layout(double minWidth);

    double width();

    double height();

    void paint(Graphics2D g);

    Rectangle2D bounds(Anchor anchor);

    Anchor anchorAt(Point2D p);

    Anchor anchorBetween(Point2D from, Point2D to);

    int ink(Rectangle2D area);
}
