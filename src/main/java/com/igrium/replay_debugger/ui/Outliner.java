package com.igrium.replay_debugger.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;

import org.scaffoldeditor.worldexport.replay.BaseReplayEntity;
import org.scaffoldeditor.worldexport.replay.models.ArmatureReplayModel;
import org.scaffoldeditor.worldexport.replay.models.Bone;
import org.scaffoldeditor.worldexport.replay.models.MultipartReplayModel;
import org.scaffoldeditor.worldexport.replay.models.ReplayModel;
import org.scaffoldeditor.worldexport.replay.models.ReplayModelPart;

public class Outliner extends JPanel {
    private JTree tree;
    protected final DefaultMutableTreeNode base;

    protected Map<BaseReplayEntity, ModelPartTreeNode> entities = new HashMap<>();
    protected Map<Object, BaseReplayEntity> partOwners = new HashMap<>();

    public static class ModelPartTreeNode extends DefaultMutableTreeNode {
        private BaseReplayEntity entity;
        private Object part;

        public ModelPartTreeNode(BaseReplayEntity entity, @Nullable Object part) {
            super(part == null ? entity : part);
            this.entity = entity;
            this.part = part;
        }

        public BaseReplayEntity getEntity() {
            return entity;
        }

        public Object getPart() {
            return part;
        }
    }

    /**
     * Create the panel.
     */
    public Outliner() {
        
        setPreferredSize(new Dimension(200, 500));
        setLayout(new BorderLayout(0, 0));

        base = new DefaultMutableTreeNode("Replay File");
        
        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        add(scrollPane, BorderLayout.CENTER);
        
        tree = new JTree(base);
        scrollPane.setViewportView(tree);
    }

    public JTree getTree() {
        return tree;
    }

    public void clear() {
        entities.clear();
        partOwners.clear();
        base.removeAllChildren();

        SwingUtilities.updateComponentTreeUI(this);
    }

    public ModelPartTreeNode addReplayEntity(BaseReplayEntity entity) {
        ModelPartTreeNode node = entities.get(entity);
        if (node == null) {
            node = new ModelPartTreeNode(entity, null);
            entities.put(entity, node);
            base.add(node);
        }

        ReplayModel<?> uModel = entity.getModel();
        if (uModel instanceof MultipartReplayModel) {
            MultipartReplayModel model = (MultipartReplayModel) uModel;
            for (ReplayModelPart part : model.bones) {
                node.add(parseReplayModelPart(entity, part));
            }
        } else if (uModel instanceof ArmatureReplayModel) {
            ArmatureReplayModel model = (ArmatureReplayModel) uModel;
            for (Bone bone : model.bones) {
                node.add(parseArmatureBone(entity, bone));
            }
        }

        return node;
    }

    private ModelPartTreeNode parseReplayModelPart(BaseReplayEntity entity, ReplayModelPart part) {
        ModelPartTreeNode node = new ModelPartTreeNode(entity, part);
        for (ReplayModelPart child : part.children) {
            node.add(parseReplayModelPart(entity, child));
        }

        return node;
    }

    private ModelPartTreeNode parseArmatureBone(BaseReplayEntity entity, Bone bone) {
        ModelPartTreeNode node = new ModelPartTreeNode(entity, bone);
        for (Bone child : bone.children) {
            node.add(parseArmatureBone(entity, child));
        }

        return node;
    }
    

    // private MutableTreeNode createBoneNode(ReplayEntity<?> entity, Object bone) {
    //     DefaultMutableTreeNode node = new DefaultMutableTreeNode(bone);
        
    // }
}
