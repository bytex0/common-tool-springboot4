# 脚本示例

`GET /api/script/run?name=sum&a=2&b=3` 返回 5，name 仅接受 sum、product、timeout。
示例真实依赖并自动装配脚本 Starter，不接收任意代码。

新增类型化接口：

- `GET /api/script/typed/run?type=GROOVY&a=2&b=3`：固定求和方法，type 支持
  GROOVY、JAVASCRIPT、LUA、PYTHON、JAVA，Lua 返回字符串 `"5"`，其他返回数字 5。
- `GET /api/script/typed/cache`：使用随机 ID 验证源码更新、刷新、删除后的执行结果。
- `GET /api/script/typed/validate?valid=true`：校验固定源码；valid=false 返回 400。
- `GET /api/script/typed/timeout`：固定可中断循环，超时返回 504，后续正常请求仍成功。
- `GET /api/script/typed/dependency?available=true`：从固定 Ivy 测试依赖读取真实 JAR 内容；
  available=false 验证依赖缺失。默认不存在此端点，自动化显式启用后才可访问。

示例显式引入 GraalJS、LuaJ、Jython；普通消费方不因引入 Starter 而被迫依赖所有引擎。
Python 为 2.7，Java 需要 JDK 21。示例只执行服务端固定源码，不接受客户端源码。

运行 `python3 scripts/test-starter.py script` 自动构建、启动和验证后清理进程。
脚本创建本次独占的本地 Maven 仓库和随机 JAR 内容，传入 `grape.config`、`grape.root`，
仅使用 file 仓库解析，不读取用户 Grape 配置、不访问外网；临时仓库与缓存会一起清理。
普通 Maven 测试只验证依赖接口协议，真实 Grape/Ivy 解析由上述 HTTP 自动化实际执行。
手动运行 `java -jar examples-starter/script-example/target/script-example-4.0.0-SNAPSHOT.jar`。
