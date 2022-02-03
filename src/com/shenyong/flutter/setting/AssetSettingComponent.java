package com.shenyong.flutter.setting;

import com.intellij.openapi.ui.Messages;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AssetSettingComponent {
    private final JPanel settingPanel;
    private final JBTextField resFileText = new JBTextField();
    private final JBTextField resPathText = new JBTextField();
    private final JBList<String> jbList = new JBList<>();
    private DefaultListModel<String> defaultListModel = new DefaultListModel<>();

    public AssetSettingComponent() {
        resFileText.getEmptyText().setText("输入资源文件名例如res");
        resPathText.getEmptyText().setText("输入资源路径例如a/b 默认在lib文件夹下创建");

        JComponent excludedPanel = new JPanel(new BorderLayout());
        excludedPanel.add(ToolbarDecorator.createDecorator(jbList)
                .setAddAction(anActionButton -> {
                    String path = Messages.showInputDialog("如排除assets下的font目录输入assets/font/",
                            "输入文件夹路径", Messages.getInformationIcon());
                    if (path != null && !path.trim().isEmpty()) {
                        defaultListModel.addElement(path);
                    }
                })
                .setEditAction(editActionButton -> {
                    String initialValue = defaultListModel.get(jbList.getSelectedIndex());
                    String path = Messages.showInputDialog("如排除assets下的font目录输入assets/font/",
                            "输入文件夹路径", Messages.getInformationIcon(), initialValue, null);
                    if (path != null && !path.trim().isEmpty()) {
                        defaultListModel.set(jbList.getSelectedIndex(), path);
                    }
                })
                .setRemoveAction(removeActionButton -> {
                    if (!jbList.isSelectionEmpty()) {
                        defaultListModel.remove(jbList.getSelectedIndex());
                    }
                })
                .disableUpDownActions().createPanel(), BorderLayout.CENTER);
        excludedPanel.setBorder(IdeBorderFactory.createTitledBorder("排除资源文件夹", true));

        settingPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("资源文件生成名称: "), resFileText, 1, false)
                .addLabeledComponent(new JBLabel("资源文件夹路径: "), resPathText, 1, false)
                .addComponent(excludedPanel)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    public JPanel getPanel() {
        return settingPanel;
    }

    public JComponent getPreferredFocusedComponent() {
        return resFileText;
    }

    @NotNull
    public String getFileNameText() {
        return resFileText.getText();
    }

    public void setFileNameText(@NotNull String newText) {
        resFileText.setText(newText);
    }

    @NotNull
    public String getFilePathText() {
        return resPathText.getText();
    }

    public void setFilePathText(@NotNull String newText) {
        resPathText.setText(newText);
    }

    @NotNull
    public List<String> getJBListData() {
        List<Object> asList = Arrays.asList(defaultListModel.toArray());

        return asList.stream()
                .map(object -> Objects.toString(object, null))
                .collect(Collectors.toList());
    }

    public void setJBListData(@NotNull List<String> pathList) {
        defaultListModel.clear();
        for (String item : pathList) {
            defaultListModel.addElement(item);
        }
        jbList.setModel(defaultListModel);
    }
}
