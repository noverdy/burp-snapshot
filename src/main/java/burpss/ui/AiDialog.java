package burpss.ui;

import burpss.ai.AiMarkup;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.function.Function;

final class AiDialog extends JDialog {

    interface Applier {
        String apply(String reply, String context, boolean replace);
    }

    private final JTextArea context;
    private final JCheckBox replace = new JCheckBox("Replace existing marks", true);
    private final JLabel status = new JLabel(" ");
    private final JProgressBar progress = new JProgressBar();
    private final JButton generate = new JButton("Generate");
    private SwingWorker<String, Void> worker;

    AiDialog(Window owner, String initial, boolean hasMarks, AiMarkup.Model model, String system,
             Function<String, String> prompt, Applier applier) {
        super(owner, "AI markup", ModalityType.MODELESS);
        context = new JTextArea(initial, 4, 44);
        context.setLineWrap(true);
        context.setWrapStyleWord(true);
        context.setFont(UIManager.getFont("TextField.font"));
        context.setMargin(new java.awt.Insets(6, 8, 6, 8));

        JLabel label = new JLabel("What does this show?");
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        javax.swing.JComponent example = PreferencesDialog.hint("e.g. IDOR: user B reads user A's project by changing the project ID in the URL");
        JPanel top = column();
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        label.setAlignmentX(LEFT_ALIGNMENT);
        top.add(label);
        top.add(example);

        JPanel options = column();
        options.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        replace.setVisible(hasMarks);
        options.add(replace);
        progress.setIndeterminate(true);
        progress.setVisible(false);
        status.setForeground(new Color(0xD9822B));
        replace.setAlignmentX(LEFT_ALIGNMENT);
        status.setAlignmentX(LEFT_ALIGNMENT);
        options.add(status);

        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setBorder(BorderFactory.createEmptyBorder(14, 16, 6, 16));
        body.add(top, BorderLayout.NORTH);
        body.add(new JScrollPane(context), BorderLayout.CENTER);
        body.add(options, BorderLayout.SOUTH);

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        generate.addActionListener(e -> run(model, system, prompt, applier));
        JPanel buttons = new JPanel(new BorderLayout());
        buttons.setBorder(BorderFactory.createEmptyBorder(4, 16, 12, 16));
        progress.setPreferredSize(new java.awt.Dimension(120, 4));
        JPanel progressHolder = new JPanel(new java.awt.GridBagLayout());
        progressHolder.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));
        java.awt.GridBagConstraints fill = new java.awt.GridBagConstraints();
        fill.fill = java.awt.GridBagConstraints.HORIZONTAL;
        fill.weightx = 1;
        progressHolder.add(progress, fill);
        buttons.add(progressHolder, BorderLayout.CENTER);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.add(cancel);
        right.add(generate);
        buttons.add(right, BorderLayout.EAST);

        getContentPane().add(body, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(generate);
        pack();
        setLocationRelativeTo(owner);
    }

    private void run(AiMarkup.Model model, String system, Function<String, String> prompt, Applier applier) {
        String text = context.getText().strip();
        String user = prompt.apply(text);
        busy(true);
        worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                return model.ask(system, user);
            }

            @Override
            protected void done() {
                if (isCancelled()) return;
                busy(false);
                try {
                    String error = applier.apply(get(), text, replace.isSelected());
                    if (error == null) dispose();
                    else status.setText(error);
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    status.setText("Burp AI failed: " + cause.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void busy(boolean on) {
        progress.setVisible(on);
        generate.setEnabled(!on);
        context.setEnabled(!on);
        status.setText(on ? "Asking Burp AI…" : " ");
        status.setForeground(on ? muted("").getForeground() : new Color(0xD9822B));
    }

    @Override
    public void dispose() {
        if (worker != null) worker.cancel(true);
        super.dispose();
    }

    private static JPanel column() {
        JPanel panel = new JPanel();
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        return panel;
    }

    private static JLabel muted(String text) {
        JLabel label = new JLabel(text);
        Color c = UIManager.getColor("Label.disabledForeground");
        label.setForeground(c == null ? Color.GRAY : c);
        return label;
    }
}
