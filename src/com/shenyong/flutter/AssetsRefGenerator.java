/*
 * MIT License
 *
 * Copyright (c) 2020 Andrew Shen
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.shenyong.flutter;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.shenyong.flutter.checker.AssetsChecker;
import com.shenyong.flutter.checker.ICheck;
import com.shenyong.flutter.checker.ProjChecker;
import com.shenyong.flutter.service.AssetSettingService;

import java.io.*;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Flutter 资源声明和Dart引用生成工具
 * <p>
 * 功能： 扫描工程 asset/assets/images 目录下的资源文件，自动在 pubspec.yaml 文件中添加资源文件声明；并生成一个 res.dart 文件，
 * 包含所有资源文件的字符串声明。
 * <p>
 * 主要解决问题：无需手动编辑 pubspec.yaml 中的资源文件声明和代码中的资源引用字符串。即避免出错，也方便开发编码，像 Android 中
 * R.drawable.xxx 方式一样，更加愉快的引用资源。
 *
 * @author sy
 * @date 2020年1月8日
 */
public class AssetsRefGenerator extends AnAction {

    private static final String PUBSPEC = "pubspec.yaml";
    public static final String RES_FILE = "res.dart";
    private static final String MAC_OS_DS_STORE = ".DS_Store";

    private final ProjChecker projChecker = new ProjChecker();
    private final AssetsChecker assetsChecker = new AssetsChecker();

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);
        String path = Objects.requireNonNull(project).getBasePath();
        ICheck.CheckResult result = projChecker.check(path);
        if (!result.isOk) {
            StringBuilder sb = new StringBuilder();
            for (String f : result.missingFiles) {
                sb.append(f).append("\n");
            }
            showErrMsg("Current directory does not seem to be a valid Flutter project directory. Files not found:\n" +
                    sb);
            return;
        }
        if (!assetsChecker.check(path).isOk) {
            showErrMsg("No asset directory named asset, assets or images was found.");
            return;
        }

        genAssetRef(path);
    }

    private void showErrMsg(String msg) {
        Messages.showMessageDialog(msg, "Flutter Assets Reference Generator", Messages.getErrorIcon());
    }

    private void showSuccessInfo() {
        Messages.showMessageDialog("Complete!\nAssets reference has been updated successfully.",
                "Flutter Assets Reference Generator", Messages.getInformationIcon());
    }

    private List<String> getAssets(String path) {
        System.out.println("Scanning asset files under asset, assets and images...");
        assetsNames.clear();
        namedAssets.clear();
        List<String> assetsDirs = assetsChecker.getAssetsDirs();
        List<String> assets = new ArrayList<>();
        for (String name : assetsDirs) {
            File dir = new File(path, name);
            getAssets(assets, dir, name, false);
        }
        return assets;
    }

    private final HashSet<String> assetsNames = new HashSet<>();
    private final HashMap<String, String> namedAssets = new HashMap<>();

    /**
     * 遍历资源目录，生成资源声明
     *
     * @param assets          资源声明集合
     * @param dir             目录
     * @param prefix          当前目录的资源路径前缀
     * @param inMultiRatioDir 当前是否在 2.0x 3.0x 等多像素比目录下，用于判断重名资源层级
     */
    private void getAssets(List<String> assets, File dir, String prefix, boolean inMultiRatioDir) {
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                // 忽略 MacOS 中的 .DS_Store 文件
                return !MAC_OS_DS_STORE.equals(name);
            }
        });
        if (files == null) {
            return;
        }
        List<File> fList = Arrays.asList(files);
        /* 处理资源变体，参考：
          https://flutterchina.club/assets-and-images/
          https://flutter.dev/docs/development/ui/assets-and-images
        */
        // 重新排序，文件排在目录前面。先处理文件，然后处理下级目录，方便处理资源变体
        fList.sort((o1, o2) -> {
            if (o1.isFile() && o2.isDirectory()) {
                return -1;
            } else if (o1.isDirectory() && o2.isFile()) {
                return 1;
            }
            return 0;
        });
        for (File f : fList) {
            String name = f.getName();
            if (f.isFile()) {
                // 变体处理：在相邻子目录中查找具有相同名称的任何文件，如果添加过同名的，则认为当前资源为一个变体，不再添加。
                // 但非相邻子目录中的同名文件，不算变体，如：/imageStyle1/1.png 和 /imageStyle2/1.png
                String asset = "    - " + prefix + "/" + name;
                String nameKey = name.split("\\.")[0];
                if (!assetsNames.contains(name)) {
                    namedAssets.put(asset, nameKey);
                    assetsNames.add(name);
                } else {
                    String existedAsset = "";
                    for (String s : assets) {
                        if (s.contains(name)) {
                            existedAsset = s;
                            break;
                        }
                    }
                    int existedDepth = existedAsset.split("/").length;
                    String[] newAsset = asset.split("/");
                    int newDepth = newAsset.length;
                    newDepth = inMultiRatioDir ? newDepth + 1 : newDepth;
                    if (newDepth > existedDepth) {
                        // 同名且有更深的路径层级，认为是变体
                        continue;
                    }
                    nameKey = nameKey.trim().replaceAll(" ", "_");
                    String namePrefix = prefix.replaceAll(" ", "_").replaceAll("/", "_");
                    namedAssets.put(asset, namePrefix + "_" + nameKey);
                }
                assets.add(asset);
                System.out.println(asset);
            } else {
                // 2.0x 3.0x 等多分辨率目录处理
                if (name.matches("^[1-9](\\.\\d)x$")) {
                    getAssets(assets, f, prefix, true);
                } else {
                    getAssets(assets, f, prefix + "/" + name, false);
                }
            }
        }
    }

    private void genAssetRef(String path) {
        List<String> assets = getAssets(path);
        if (assets.isEmpty()) {
            return;
        }
        AssetSettingService.AssetConfig config = AssetSettingService.getInstance().getState();
        List<String> excludePaths = new ArrayList<>();
        if (config != null && config.excludePath != null) {
            excludePaths = config.excludePath;
        }
        removeExclude(assets, excludePaths);

        updatePubspec(path, assets, excludePaths);
        genResDart(path, assets);
    }

    /**
     * 更新pubspec.yaml文件中的资源声明
     *
     * @param path   项目路径
     * @param assets 扫描生成的资源声明
     * @param excludePaths 排除文件夹路径
     */
    private void updatePubspec(String path, List<String> assets, List<String> excludePaths) {
        System.out.println("Updating pubspec.yaml...");
        File pubspec = new File(path, PUBSPEC);
        if (!pubspec.exists()) {
            return;
        }
        List<String> outLines = new ArrayList<>();
        List<String> oldRemained = new ArrayList<>();
        boolean assetStart = false;
        BufferedReader reader = null;
        BufferedWriter writer = null;
        try {
            reader = new BufferedReader(new FileReader(pubspec));
            String line = reader.readLine();
            while (line != null) {
                if (line.matches("^ {2}assets:")) {
                    // 检测到资源声明起始行"  assets:"
                    assetStart = true;
                    outLines.add(line);
                    line = reader.readLine();
                    continue;
                }
                if (assetStart) {
                    // 原pubspec.yaml文件中就有的资源声明，或资源声明之间的空行
                    if (line.matches("^ {2,}- .*") || line.matches("^\\S*$")) {
                        // 原有的其他声明，可能是已删除的，或引入的其他package的资源
                        if (line.matches("^ {2,}- .*") && !assets.contains(line)) {
                            oldRemained.add(line);
                        }
                    } else {
                        // 资源声明结束
                        assetStart = false;
                        removeDeleted(assets, oldRemained, excludePaths);
                        // 默认按字母顺序排序
                        assets.sort(String::compareToIgnoreCase);
                        outLines.addAll(assets);
                        outLines.add(line);
                    }
                } else {
                    outLines.add(line);
                }
                line = reader.readLine();
                if (line == null && assetStart) {
                    // 资源声明在yaml文件末尾的情况。判断asset声明未结束，但已读取到文件末尾了
                    assetStart = false;
                    removeDeleted(assets, oldRemained, excludePaths);
                    // 默认按字母顺序排序
                    assets.sort(String::compareToIgnoreCase);
                    outLines.addAll(assets);
                }
            }
            // 将更新了资源声明的内容写回到pubspec.yaml文件
            writer = new BufferedWriter(new FileWriter(pubspec));
            for (String out : outLines) {
                writer.write(out);
                writer.newLine();
            }
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 去掉已删除资源的旧声明，但保留引入的其他package的资源（以”  - packages/*"形式声明的）和排除文件夹的声明
     *
     * @param newAssets   扫描生成的资源声明
     * @param oldRemained 遗留的其他声明
     * @param excludePaths 排除的文件夹路径
     */
    private void removeDeleted(List<String> newAssets, List<String> oldRemained, List<String> excludePaths) {
        for (String line : oldRemained) {
            if (line.matches("^ {2,}- packages/.*")) {
                newAssets.add(line);
            } else {
                for (String path : excludePaths) {
                    if (line.contains(path)) {
                        newAssets.add(line);
                        break;
                    }
                }
            }
        }
    }


    /**
     * 去掉已被排除声明，
     *
     * @param newAssets   扫描生成的资源声明
     * @param excludePaths 排除的文件夹
     */
    private void removeExclude(List<String> newAssets, List<String> excludePaths) {
        for (String path : excludePaths) {
            newAssets.removeIf(asset -> asset.contains(path));
        }
    }

    /**
     * 首字母大写
     * @param str
     * @return result
     */
    private String captureName(String str) {
        // 进行字母的ascii编码前移，效率要高于截取字符串进行转换的操作
        char[] cs=str.toCharArray();
        cs[0]-=32;
        return String.valueOf(cs);
    }

    private static final Pattern PATTERN = Pattern.compile("packages/(?<pkgName>[a-z_]+)/.*");

    private void genResDart(String path, List<String> assets) {
        System.out.println("Updating res.dart...");
        AssetSettingService.AssetConfig config = AssetSettingService.getInstance().getState();
        File resDirectory = new File(path + "/" + "lib");
        if (config != null && config.generatePath != null) {
            resDirectory = new File(resDirectory.getPath() + "/" + config.generatePath);
        }
        if (!resDirectory.exists()) {
            try {
                resDirectory.mkdirs();
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        }

        File resFile = new File(resDirectory.getPath(), RES_FILE);
        if (config != null && config.generateFileName != null) {
            resFile = new File(resDirectory.getPath(), config.generateFileName + ".dart");
        }
        if (!resFile.exists()) {
            try {
                resFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(resFile));
            // TODO: 2020/1/8 其他语言地区格式处理
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
            writer.write("/// Generated by AssetsRefGenerator on " + sdf.format(Calendar.getInstance().getTime()));
            writer.newLine();
            if (config != null && config.generateFileName != null) {
                writer.write("class "+ captureName(config.generateFileName) +" {");
            } else {
                writer.write("class Res {");
            }
            writer.newLine();
            List<String> packages = new ArrayList<>();
            List<String> assetDefines = new ArrayList<>();
            List<String> excludeDirectory = new ArrayList<>();
            if (config != null && config.excludePath != null) {
                excludeDirectory = config.excludePath;
            }
            removeExclude(assets, excludeDirectory);
            for (String out : assets) {
                String assetPath = out.replaceAll(" {2,}- ", "").trim();
                // 处理其他 package 的资源文件声明
                // 声明格式通常为：   - packages/package_name/...
                if (out.matches("^ {2,}- packages/[a-z_]+/.*")) {
                    // 获取包名称
                    Matcher matcher = PATTERN.matcher(assetPath);
                    if (matcher.find()) {
                        String pkgName = matcher.group("pkgName");
                        if (!packages.contains(pkgName)) {
                            packages.add(pkgName);
                        }
                    }
                    assetPath = assetPath.replaceFirst("packages/[a-z_]+/", "");
                }
                String name = namedAssets.get(out);
                if (name == null) {
                    name = out.substring(out.lastIndexOf("/") + 1).split("\\.")[0];
                }
                // 替换连字符'-'为下划线'_'
                name = name.replace('-', '_');
                // 变音符处理，如：âĉéè.png
                if (name.matches("^.*[\\u00C0-\\u017F]+.*$")) {
                    name = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
                }
                assetDefines.add("  static const String " + name + " = \"" + assetPath + "\";");
            }

            assetDefines.sort(String::compareToIgnoreCase);
            for (String s : assetDefines) {
                writer.write(s);
                writer.newLine();
            }
            writer.write("}");
            writer.newLine();
            if (!packages.isEmpty()) {
                writer.newLine();
                writer.write("class Packages {");
                writer.newLine();
                for (String pkg : packages) {
                    writer.write("  static const String " + pkg + " = \"" + pkg + "\";");
                    writer.newLine();
                }
                writer.write("}");
                writer.newLine();
            }

            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        System.out.println("Flutter assets reference has been updated.");
        showSuccessInfo();
    }
}
