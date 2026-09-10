# Uni-App 身份证采集插件（Android）

适用于传统 Uni-App（Vue 2 / App-Plus）的 Android 原生 Module 插件。它提供横屏身份证正反面采集、拍摄框裁切、照片方向修正，并将本地照片路径回传给 Vue 页面。

## 插件信息

- 插件 ID / Module 名称：`uni-id-card-capture`
- Android Module 类：`io.github.uniidcardcapture.IdCardCaptureModule`
- 最低 Android 版本：21
- 成功回调：`{ code: 0, images: [{ side, path, uri }] }`

## 本地构建

1. 安装 Android Studio 与 JDK 17。
2. 从与 HBuilderX 版本匹配的 DCloud Android 离线 SDK 中取得 `uniapp-v8-release.aar`，复制到 `idcardcapture/libs/uniapp-v8-release.aar`。该文件仅用于编译，不会打入最终 AAR，且已被 Git 忽略。
3. 执行：`./gradlew :idcardcapture:assembleRelease`。

构建产物：

```text
idcardcapture/build/outputs/aar/uni-id-card-capture-release.aar
```

## GitHub Actions 自动打包

工作流位于 `.github/workflows/build-android-plugin.yml`，支持推送自动触发和手动运行。使用前请把 `uniapp-v8-release.aar` 上传到私有 GitHub Release，并在本仓库配置：

- Secret：`DCLOUD_SDK_REPO_TOKEN`，可读取私有 SDK 仓库的 Token；
- Variable：`DCLOUD_ANDROID_SDK_REPOSITORY`，私有 SDK 仓库的 `owner/repository`；
- Variable：`DCLOUD_ANDROID_SDK_RELEASE_TAG`，包含 `uniapp-v8-release.aar` 的 Release 标签。

成功后在 Actions 的 Artifacts 下载 `UniIdCardCapture-android-plugin`。解压嵌套压缩包可得到：

```text
artifact/android/uni-id-card-capture-release.aar
artifact/package.json
```

## 在 Uni-App 项目中使用

1. 将 AAR 放到 `nativeplugins/uni-id-card-capture/android/`。
2. 将产物中的 `package.json` 放到 `nativeplugins/uni-id-card-capture/package.json`。
3. 在 `manifest.json` 注册本地 Android 原生插件并声明相机权限，然后重新制作自定义基座或云打包。
4. 在 Vue 页面调用：

```js
const capture = uni.requireNativePlugin('uni-id-card-capture');
capture.capture({ side: 'front' }, result => {
  console.log(result);
});
```

必须在具有摄像头的 Android 真机上验证。
