package com.shenyong.flutter.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@State(
        name = "asset_gen_config",
        storages = {
                @Storage("asset_gen_config.xml")
        }
)
public
class AssetSettingService implements PersistentStateComponent<AssetSettingService.AssetConfig> {
    public static AssetSettingService getInstance() {
        return ApplicationManager.getApplication().getService(AssetSettingService.class);
    }

    @Override
    public @Nullable AssetConfig getState() {
        return assetConfig;
    }

    @Override
    public void loadState(@NotNull AssetConfig assetConfig) {
        this.assetConfig = assetConfig;
    }

    public static class AssetConfig {
        public String generatePath;
        public String generateFileName;
        public List<String> excludePath;
    }

    private AssetConfig assetConfig = new AssetConfig();
}
