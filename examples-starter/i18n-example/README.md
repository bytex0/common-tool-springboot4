# 国际化示例

真实集成内存消息来源，默认本机18085端口，无需外部服务。

- GET `/api/i18n/message?code=hello&name=Lin`，按Accept-Language协商语言。
- PUT `/api/i18n/message?language=en-US&code=dynamic&text=Hello`，新增或更新消息。
- DELETE 同路径，传入language与code删除消息。
- POST `/api/i18n/refresh`，刷新但不删除内存数据。

执行`python3 scripts/test-starter.py i18n`，自动验证默认中文、加权语言头、区域语言回退、动态消息、刷新保留、删除、code回退与错误语言拒绝。普通构建也运行真实Starter接口测试。
