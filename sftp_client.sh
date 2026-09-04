#!/bin/bash
# 手动SFTP客户端工具(Linux独立版,自包含,不依赖gateway目录)
# 用法: ./sftp_client.sh -h 主机IP -P 端口 -u 用户 -pw '密码' -cmd 命令 [命令参数...]
#   ./sftp_client.sh -h 主机IP -P 端口 -u 用户 -pw '密码' -cmd pwd
#   ./sftp_client.sh -h 主机IP -P 端口 -u 用户 -pw '密码' -cmd ls /upload
#   ./sftp_client.sh -h 主机IP -P 端口 -u 用户 -pw '密码' -cmd put ./dz.json /upload
#   ./sftp_client.sh -h 主机IP -P 端口 -u 用户 -pw '密码' -cmd get /upload/dz.json ./dz.json
#   ./sftp_client.sh -h 主机IP -P 端口 -u 用户 -pw '密码' -cmd probe
SHELL_DIR=$(cd $(dirname $0) && pwd)
exec java -Dfile.encoding=UTF-8 -cp "$SHELL_DIR/sftp_client.jar" com.zzzj.soft.sftp.util.SftpClient "$@"
