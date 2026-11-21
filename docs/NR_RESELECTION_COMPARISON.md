# NR 与 IMS 调用路径比较

这是公开参考实现的技术比较，不包含个人设备配置或现场记录。

Carrier IMS 3.8.5 r98 与 3.9.0 r106 的 ImsModifier / ImsResetter 使用相同文件内容；3.9.1 r107 的相关修复处理权限释放兼容。NR 配置包含 NSA/SA `[1,2]`，用户触发的 IMS 重注册调用 `resetIms(slot)`。未在所查路径发现挂断后自动切换网络制式的处理。

TensorIMS 在 revision `0b114fe038c259f5b3889aa82f618f8873b109c7` 使用单卡合并 bundle 与有限回读；配置可用性、用户偏好、注册和图标仍需分别解释。参考参数或相同配置值不能证明不同固件上的相同行为。

参考：

- https://github.com/ryfineZ/carrier-ims-for-pixel/tree/4312b5a9244a20444cccf5c4a5f763f6fd9d2ab5
- https://github.com/Pixel-Tailor-CN/TensorIMS/tree/0b114fe038c259f5b3889aa82f618f8873b109c7
- https://android.googlesource.com/platform/packages/services/Telephony/+/refs/heads/android17-release/src/com/android/phone/PhoneInterfaceManager.java

本项目后续使用独立白名单批量协议与整组核验；配套键和用户偏好保持独立，不能据此推定具体网络故障根因。
