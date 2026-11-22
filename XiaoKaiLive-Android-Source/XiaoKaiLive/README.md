# 小凯直播（Android）

深色科技风 IPTV 播放壳，支持导入用户自有或获授权使用的 TXT、M3U、M3U8 播放列表。

## 当前实现
- 小凯直播启动页
- TXT：支持 `频道名,URL`、纯 URL、`频道名,#genre#` 分类行
- M3U/M3U8：解析 `#EXTINF`、`group-title` 和媒体地址
- Android 文件选择器导入本地播放列表
- 输入 HTTP/HTTPS 地址导入网络播放列表
- AndroidX Media3 / ExoPlayer 播放
- 搜索、收藏筛选
- 竖屏列表/播放器布局，横屏左右布局
- 全屏切换、播放列表本地保存
- UTF-8 / GB18030 文本编码兼容

## 编译 APK
### Android Studio
需要 Android Studio、JDK 17、Android SDK Platform 35。打开项目并等待 Gradle 同步，然后选择 **Build > Build Bundle(s) / APK(s) > Build APK(s)**。APK 默认位于 `app/build/outputs/apk/debug/app-debug.apk`。

### GitHub 云端编译
项目已包含 `.github/workflows/android.yml`。把项目文件上传到 GitHub 仓库（确保包含隐藏的 `.github` 文件夹），进入 Actions，运行 **Build XiaoKaiLive APK**。完成后从该次运行的 Artifacts 下载 `XiaoKaiLive-debug-apk.zip`，解压得到 APK。

## 注意
这是播放壳，不附带直播频道或第三方直播源。请只导入自己拥有或获授权使用的播放列表。部分直播源可能需要 DRM、Referer、Cookie、特殊请求头或特殊协议，当前版本不保证兼容所有源。当前源码尚未在真实 Android 设备上完成构建和测试。
