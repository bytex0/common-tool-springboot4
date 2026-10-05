# IP 示例

真实依赖 IP Starter，通过自动配置加载示例 XDB，提供 `GET /api/ip/search?ip=8.8.8.8`。
构建后运行 `java -jar examples-starter/ip2region-example/target/ip2region-example-4.0.0-SNAPSHOT.jar`。
自动验证：`python3 scripts/test-starter.py ip2region`，自动随机端口启动、请求和停止进程。

`src/main/resources/ip2region.xdb` 复制自参考 common-tool 仓库中的同名资源，
数据来源为 [ip2region](https://github.com/lionsoul2014/ip2region)，仅用作固定回归快照，不代表实时归属地。
真实数据库随示例打包；库制品不携带该文件。
