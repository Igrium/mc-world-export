package com.igrium.replay_debugger;

import java.awt.AWTException;
import java.awt.HeadlessException;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.apache.logging.log4j.LogManager;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class ReplayDebugger {
    private JFrame frame;

    public void launch() throws HeadlessException {
        System.setProperty("java.awt.headless", "false"); // This could easily fuck things up

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                | UnsupportedLookAndFeelException e) {
            LogManager.getLogger().error("Error setting debugger look and feel.", e);
        }
        frame = new JFrame();

        JButton b = new JButton("click");
        b.setBounds(130, 100, 100, 40);
        frame.add(b);

        frame.setSize(400, 500);
        frame.setLayout(null);
        frame.setVisible(true);
    }
}