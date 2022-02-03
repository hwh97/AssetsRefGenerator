package com.shenyong.flutter.setting;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.shenyong.flutter.service.AssetSettingService;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class AssetSettingConfigurable implements Configurable {
    private AssetSettingComponent assetSettingsComponent;

    @Override
    public String getDisplayName() {
        return "Asset Gen Setting";
    }

    @Override
    public @Nullable JComponent createComponent() {
        assetSettingsComponent = new AssetSettingComponent();
        return assetSettingsComponent.getPanel();
    }

    @Override
    public boolean isModified() {
        AssetSettingService.AssetConfig config = AssetSettingService.getInstance().getState();
        assert config != null;
        boolean modified = !assetSettingsComponent.getFilePathText().equals(config.generatePath);
        modified |= !assetSettingsComponent.getFileNameText().equals(config.generateFileName);
        List<String> list = List.of();
        if (config.excludePath != null) {
            list = config.excludePath;
        }
        modified |= list.size() != assetSettingsComponent.getJBListData().size()
                || !list.containsAll(assetSettingsComponent.getJBListData());
        return modified;
    }

    @Override
    public void apply() throws ConfigurationException {
        AssetSettingService.AssetConfig config = AssetSettingService.getInstance().getState();
        assert config != null;
        config.generateFileName = assetSettingsComponent.getFileNameText();
        config.generatePath = assetSettingsComponent.getFilePathText();
        config.excludePath = assetSettingsComponent.getJBListData();
    }

    @Override
    public void reset() {
        AssetSettingService.AssetConfig config = AssetSettingService.getInstance().getState();
        assert config != null;
        assetSettingsComponent.setFileNameText(config.generateFileName);
        assetSettingsComponent.setFilePathText(config.generatePath);
        if (config.excludePath != null) {
            assetSettingsComponent.setJBListData(config.excludePath);
        } else {
            assetSettingsComponent.setJBListData(List.of());
        }
    }

    @Override
    public void disposeUIResources() {
        assetSettingsComponent = null;
    }
}
