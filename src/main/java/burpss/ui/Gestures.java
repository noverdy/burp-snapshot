package burpss.ui;

import javax.swing.JComponent;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.DoubleConsumer;

final class Gestures {

    private static final String PACKAGE = "com.apple.eawt.event";

    private Gestures() {
    }

    static void onMagnify(JComponent component, DoubleConsumer onMagnify) {
        try {
            Module desktop = JComponent.class.getModule();
            Method export = Module.class.getDeclaredMethod("implAddExports", String.class);
            export.setAccessible(true);
            export.invoke(desktop, PACKAGE);
            Class<?> listenerType = Class.forName(PACKAGE + ".MagnificationListener");
            Class<?> utilities = Class.forName(PACKAGE + ".GestureUtilities");
            Object listener = Proxy.newProxyInstance(Gestures.class.getClassLoader(), new Class<?>[]{listenerType},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "magnify" -> {
                            onMagnify.accept((double) args[0].getClass().getMethod("getMagnification").invoke(args[0]));
                            yield null;
                        }
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "toString" -> "MagnificationListener";
                        default -> null;
                    });
            utilities.getMethod("addGestureListenerTo", JComponent.class, Class.forName(PACKAGE + ".GestureListener"))
                    .invoke(null, component, listener);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }
    }
}
