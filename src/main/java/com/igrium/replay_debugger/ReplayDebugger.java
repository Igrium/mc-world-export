package com.igrium.replay_debugger;

import java.awt.HeadlessException;
import java.io.IOException;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.igrium.replay_debugger.ui.Outliner;

import org.apache.logging.log4j.LogManager;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import java.awt.BorderLayout;

import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.JMenuBar;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

@Environment(EnvType.CLIENT)
public class ReplayDebugger {
    private JFrame frame;
    private Outliner outliner;
    private ParsedReplayFile file;
    
    /**
     * @wbp.parser.entryPoint
     */
    public void launch() throws HeadlessException {
        System.setProperty("java.awt.headless", "false"); // This could easily fuck things up

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                | UnsupportedLookAndFeelException e) {
            LogManager.getLogger().error("Error setting debugger look and feel.", e);
        }
        frame = new JFrame();

        frame.setSize(1280, 720);
        frame.getContentPane().setLayout(new BorderLayout(0, 0));

        outliner = new Outliner();
        outliner.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (file == null) browseFile();
            }
        });
        frame.getContentPane().add(outliner, BorderLayout.EAST);
        
        JLabel lblOpenFile = new JLabel("Click to Open File");
        lblOpenFile.setHorizontalAlignment(SwingConstants.CENTER);
        frame.getContentPane().add(lblOpenFile, BorderLayout.CENTER);
        
        JMenuBar menuBar = new JMenuBar();
        frame.setJMenuBar(menuBar);
        
        JMenu mnFile = new JMenu("File");
        menuBar.add(mnFile);
        
        JMenuItem mntmOpen = new JMenuItem("Open");
        mntmOpen.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                browseFile();
            }
        });
        mnFile.add(mntmOpen);
        frame.setVisible(true);
    }

    public void loadReplayFile(ParsedReplayFile file) {
        outliner.clear();
        for (ParsedReplayEntity entity : file.getEntities()) {
            outliner.addReplayEntity(entity);
        }
    }

    public void browseFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Replay File", "replay"));

        int returnVal = chooser.showOpenDialog(frame);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            try {
                ParsedReplayFile replay = ParsedReplayFile.load(chooser.getSelectedFile(), null);
                loadReplayFile(replay);
            } catch (ReplayParseException | IOException e) {
                LogManager.getLogger().error("Error loading replay file.", e);
            }
        }
    }

    public Outliner getOutliner() {
        return outliner;
    }
}