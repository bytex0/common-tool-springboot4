# 动态线程池示例

真实依赖 Starter，默认 demo 池为 1 个线程、2 个排队槽位。
接口：`GET /api/pools/run?delay=400`、`GET /api/pools/stats`、
`POST /api/pools/resize?core=4&max=4`。仅用于本地验证，不应公开管理接口。

运行 `python3 scripts/test-starter.py dynamic-threadpool` 完成构建、真实并发接口检查和进程清理。
