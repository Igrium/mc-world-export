package com.igrium.replay_debugger;

import java.awt.HeadlessException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.igrium.replay_debugger.ui.ExceptionDialog;
import com.igrium.replay_debugger.ui.Outliner;
import com.igrium.replay_debugger.ui.ProgressDialog;

import org.apache.logging.log4j.LogManager;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import java.awt.BorderLayout;
import java.awt.EventQueue;

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
        List<ParsedReplayEntity> entities = new ArrayList<>(file.getEntities());
        Collections.sort(entities, (o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));
        for (ParsedReplayEntity entity : entities) {
            outliner.addReplayEntity(entity);
        }
        outliner.getTree().expandRow(0);
        this.file = file;
    }

    public void parseFile(File file) {
        CompletableFuture<ParsedReplayFile> future = ProgressDialog.openAndExecute((listener) -> {
            try {
                return ParsedReplayFile.load(file, listener);
            } catch (IOException e) {
                throw new ReplayParseException("An unexpected IO exception occured while parsing the file.");
            }
        });

        future.exceptionallyAsync((e) -> {
            ExceptionDialog.showExceptionMessage(frame, e);
            return null;
        }, EventQueue::invokeLater);

        future.thenAcceptAsync(this::loadReplayFile, EventQueue::invokeLater);
    }

    public void browseFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Replay File", "replay"));

        int returnVal = chooser.showOpenDialog(frame);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            parseFile(chooser.getSelectedFile());
        }
    }

    public Outliner getOutliner() {
        return outliner;
    }
}