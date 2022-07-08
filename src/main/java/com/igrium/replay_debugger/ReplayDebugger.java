package com.igrium.replay_debugger;

import java.awt.HeadlessException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.igrium.replay_debugger.ui.ExceptionDialog;
import com.igrium.replay_debugger.ui.Outliner;
import com.igrium.replay_debugger.ui.ProgressDialog;
import com.igrium.replay_debugger.ui.Outliner.ModelPartTreeNode;
import com.igrium.replay_debugger.ui.graph.Graph;

import org.apache.logging.log4j.LogManager;
import org.joml.Vector2d;
import org.joml.Vector2dc;
import org.scaffoldeditor.worldexport.replay.BaseReplayEntity;
import org.scaffoldeditor.worldexport.replay.models.Transform;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel.Pose;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import java.awt.BorderLayout;
import java.awt.EventQueue;

import javax.swing.SwingUtilities;
import javax.swing.JMenuBar;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeSelectionEvent;

@Environment(EnvType.CLIENT)
public class ReplayDebugger {
    private JFrame frame;
    private Outliner outliner;
    private ParsedReplayFile file;
    private Graph graph;
    
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
        outliner.getTree().addTreeSelectionListener(new TreeSelectionListener() {
            public void valueChanged(TreeSelectionEvent e) {
                Object bNode = outliner.getTree().getLastSelectedPathComponent();
                if (bNode instanceof ModelPartTreeNode) {
                    ModelPartTreeNode node = (ModelPartTreeNode) bNode;
                    loadAnimChannel(node.getEntity(), node.getPart());
                } else {
                    graph.clear();
                }
            }
        });
        outliner.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (file == null) browseFile();
            }
        });
        frame.getContentPane().add(outliner, BorderLayout.EAST);
        
        graph = new Graph();
        frame.getContentPane().add(graph, BorderLayout.CENTER);
        
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

    public void closeFile() {
        outliner.clear();
        graph.getChannels().clear();
        this.file = null;
        SwingUtilities.updateComponentTreeUI(frame);
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
            LogManager.getLogger("Replay Debugger").error("Error loading replay file:", e);
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

    public void loadAnimChannel(BaseReplayEntity entity, @Nullable Object bone) {
        graph.clear();

        List<Vector2dc> rotW = new ArrayList<>();
        List<Vector2dc> rotX = new ArrayList<>();
        List<Vector2dc> rotY = new ArrayList<>();
        List<Vector2dc> rotZ = new ArrayList<>();

        List<Vector2dc> posX = new ArrayList<>();
        List<Vector2dc> posY = new ArrayList<>();
        List<Vector2dc> posZ = new ArrayList<>();

        List<Vector2dc> scaleX = new ArrayList<>();
        List<Vector2dc> scaleY = new ArrayList<>();
        List<Vector2dc> scaleZ = new ArrayList<>();

        List<Vector2dc> visible = new ArrayList<>();

        int i = 0;
        for (Pose<?> pose : entity.getFrames()) {
            Transform trans;
            if (bone == null) {
                trans = pose.root;
            } else {
                trans = pose.bones.get(bone);
                if (trans == null) {
                    LogManager.getLogger().warn("Bone: {} doesn't have a transform on frame {}!", bone, i);
                    trans = Transform.NEUTRAL;
                }
            }

            rotW.add(new Vector2d(i, trans.rotation.w()));
            rotX.add(new Vector2d(i, trans.rotation.x()));
            rotY.add(new Vector2d(i, trans.rotation.y()));
            rotZ.add(new Vector2d(i, trans.rotation.z()));

            posX.add(new Vector2d(i, trans.translation.x()));
            posY.add(new Vector2d(i, trans.translation.y()));
            posZ.add(new Vector2d(i, trans.translation.z()));

            scaleX.add(new Vector2d(i, trans.scale.x()));
            scaleY.add(new Vector2d(i, trans.scale.y()));
            scaleZ.add(new Vector2d(i, trans.scale.z()));

            visible.add(new Vector2d(i, trans.visible ? 1f : 0f));
            i++;
        }

        graph.addChannel(rotW);
        graph.addChannel(rotX);
        graph.addChannel(rotY);
        graph.addChannel(rotZ);

        graph.addChannel(posX);
        graph.addChannel(posY);
        graph.addChannel(posZ);

        graph.addChannel(scaleX);
        graph.addChannel(scaleY);
        graph.addChannel(scaleZ);
        SwingUtilities.updateComponentTreeUI(graph);
    }
}