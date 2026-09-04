# sftp_client_linux

手动 SFTP 客户端工具（Linux 独立版，自包含，不依赖任何 gateway 目录/外部 jar）。

## 特性

- **单文件可执行**：`sftp_client.jar` 为 fat jar，已内置 jsch 依赖，无需额外 classpath
- **兼容 JDK 8+**：以 `--release 8` 编译，可在 JDK8/11/17 等运行
- **自包含启动脚本**：`sftp_client.sh` 只引用同级目录下的 jar，方便拷贝部署
- **通用加密算法配置**：兼容新老 SFTP 服务器
- **纯命令行**：适合脚本化调用（上传、下载、巡检等）

## 文件结构

```
sftp_client_linux/
├── sftp_client.jar            # fat jar（内含 jsch）
├── sftp_client.sh             # Linux 启动脚本
└── src/com/sdnx/jy/rl/util/SftpClient.java   # 源码
```

## 使用方法

```bash
chmod +x sftp_client.sh
./sftp_client.sh -h 主机IP -P 端口 -u 用户 -pw '密码' -cmd 命令 [命令参数...]
```

### 支持的命令

| 命令 | 说明 |
|------|------|
| `pwd` | 显示当前目录（可判断是否 chroot） |
| `ls [路径]` | 列目录，默认 `/` |
| `put <本地文件> <远端目录>` | 上传文件 |
| `get <远端文件> <本地路径>` | 下载文件 |
| `rm <远端文件>` | 删除远端文件 |
| `mkdir <远端目录>` | 创建远端目录（逐级创建） |
| `probe [路径...]` | 探查路径是否存在及权限 |

### 示例

```bash
./sftp_client.sh -h 主机IP -P 端口 -u 用户 -pw '密码' -cmd pwd
./sftp_client.sh -h 主机IP -P 端口 -u 用户 -pw '密码' -cmd ls /upload
./sftp_client.sh -h 主机IP -P 端口 -u 用户 -pw '密码' -cmd put ./dz.json /upload
./sftp_client.sh -h 主机IP -P 端口 -u 用户 -pw '密码' -cmd get /upload/dz.json ./dz.json
./sftp_client.sh -h 主机IP -P 端口 -u 用户 -pw '密码' -cmd probe
```

### 直接运行 jar

```bash
java -Dfile.encoding=UTF-8 -jar sftp_client.jar -h 主机IP -P 端口 -u 用户 -pw '密码' -cmd pwd
```

## 编译（可选，如需重新构建）

需要 JDK8+ 与 jsch jar（本仓库已内置到 fat jar 中）。

```bash
mkdir -p build/classes
javac --release 8 -encoding UTF-8 -cp jsch-2.28.7.jar -d build/classes src/com/sdnx/jy/rl/util/SftpClient.java
jar cfm sftp_client.jar MANIFEST.MF -C build/classes . 
```

## 说明

- 源码中不包含任何真实服务器地址、账号密码等敏感信息，连接参数一律通过命令行传入
- 本工具为纯 Java 实现（基于 [JSch](https://github.com/mwiede/jsch)），无原生依赖，跨平台可移植
