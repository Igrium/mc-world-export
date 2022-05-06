package com.igrium.replay_debugger.ui;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JProgressBar;

public class ProgressDialog extends JDialog {
    private JLabel detailText;
    private JProgressBar progressBar;
    
    /**
     * Create the dialog.
     */
    public ProgressDialog() {
        setResizable(false);
        setSize(new Dimension(600, 100));

        getContentPane().setLayout(new GridBagLayout());
        
        detailText = new JLabel("Loading");
        GridBagConstraints gbc_detailText = new GridBagConstraints();
        gbc_detailText.insets = new Insets(5, 5, 5, 5);
        gbc_detailText.gridx = 0;
        gbc_detailText.gridy = 0;
        getContentPane().add(detailText, gbc_detailText);

        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        progressBar = new JProgressBar();
        progressBar.setPreferredSize(new Dimension(500, 20));
        GridBagConstraints gbc_progressBar = new GridBagConstraints();
        gbc_progressBar.insets = new Insets(5, 5, 5, 5);
        gbc_progressBar.fill = GridBagConstraints.HORIZONTAL;
        gbc_progressBar.gridwidth = 2;
        gbc_progressBar.weightx = 1.0;
        gbc_progressBar.gridx = 0;
        gbc_progressBar.gridy = 1;
        getContentPane().add(progressBar, gbc_progressBar);

        pack();
    }

    public JLabel getDetailText() {
        return detailText;
    }

    public JProgressBar getProgressBar() {
        return progressBar;
    }

    public <T> CompletableFuture<T> execute(Function<ParsingUpdateListener, T> function) {
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            T val = function.apply(new ParsingUpdateListener() {

                @Override
                public void setProgress(float progress) {
                    EventQueue.invokeLater(() -> {
                        getProgressBar().setValue((int) (progress * 100));
                    });
                }

                @Override
                public void setInfoText(String text) {
                    EventQueue.invokeLater(() -> {
                        getDetailText().setText(text);
                    });
                }
            });

            return val;
        });

        return future;
    }

    public static <T> CompletableFuture<T> openAndExecute(Function<ParsingUpdateListener, T> function) {
        ProgressDialog dialog = new ProgressDialog();
        CompletableFuture<T> future = dialog.execute(function);
        dialog.setVisible(true);

        future.whenCompleteAsync((val, e) -> {
            dialog.dispose();
        }, EventQueue::invokeLater);

        return future;
    }
}
