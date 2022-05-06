package com.igrium.replay_debugger.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.HeadlessException;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;

public final class ExceptionDialog {
    private ExceptionDialog() {
    };

    /**
     * www.java2s.com
     * Show an Exception dialogue.
     * 
     * @param parentComponent
     * @param exception
     * @throws HeadlessException
     * @wbp.parser.entryPoint
     */
    public static void showExceptionMessage(Component parentComponent,
            Throwable exception) throws HeadlessException {

        StringWriter stringWriter = new StringWriter();
        exception.printStackTrace(new PrintWriter(stringWriter));

        JLabel message = new JLabel(exception.getMessage());
        message.setBorder(BorderFactory.createEmptyBorder(3, 0, 10, 0));

        JTextArea text = new JTextArea();
        text.setEditable(false);
        text.setFont(UIManager.getFont("Label.font"));
        text.setText(stringWriter.toString());
        text.setCaretPosition(0);

        JScrollPane scroller = new JScrollPane(text);
        scroller.setPreferredSize(new Dimension(400, 200));

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        panel.add(message, BorderLayout.NORTH);
        panel.add(scroller, BorderLayout.SOUTH);

        JOptionPane.showMessageDialog(parentComponent, panel, "Exception thrown!",
                JOptionPane.ERROR_MESSAGE);

    }
}
