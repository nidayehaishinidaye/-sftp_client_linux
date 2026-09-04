package com.zzzj.soft.sftp.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.Vector;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;

/**
 * 手动SFTP客户端工具(命令行)
 *
 * 用法:
 *   ./sftp_client.sh -h 主机 -P 端口 -u 用户 -pw 密码 -cmd 命令 [命令参数...]
 *
 * 命令:
 *   pwd                                 显示当前目录(可判断是否chroot)
 *   ls [路径]                           列目录,默认 /
 *   put <本地文件> <远端目录>           上传文件到远端目录
 *   get <远端文件> <本地路径>           下载文件到本地
 *   rm <远端文件>                       删除远端文件
 *   mkdir <远端目录>                    创建远端目录(逐级创建)
 *   probe [路径...]                     探查路径是否存在及权限
 *
 * 示例:
 *   ./sftp_client.sh -h 主机IP -P 端口 -u 用户 -pw '密码' -cmd pwd
 *   ./sftp_client.sh -h 主机IP -P 端口 -u 用户 -pw '密码' -cmd ls /upload
 *   ./sftp_client.sh -h 主机IP -P 端口 -u 用户 -pw '密码' -cmd put ./dz.json /upload
 *   ./sftp_client.sh -h 主机IP -P 端口 -u 用户 -pw '密码' -cmd probe /upload
 */
public class SftpClient {

    private static Session sshSession = null;

    public static void main(String[] args) {
        String host = null;
        String port = null;
        String user = null;
        String password = null;
        String cmd = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-h": host = args[++i]; break;
                case "-P": port = args[++i]; break;
                case "-u": user = args[++i]; break;
                case "-pw": password = args[++i]; break;
                case "-cmd": cmd = args[++i]; break;
                default:
                    // 跳过
            }
        }

        if (host == null || port == null || user == null || password == null || cmd == null) {
            System.out.println("错误: 必须提供 -h(主机) -P(端口) -u(用户) -pw(密码) -cmd(命令)");
            printUsage();
            return;
        }

        // 合法命令校验(提前到连接前,避免拼错命令时白连)
        java.util.Set<String> cmds = new java.util.HashSet<String>();
        cmds.add("pwd"); cmds.add("ls"); cmds.add("put"); cmds.add("get");
        cmds.add("rm"); cmds.add("mkdir"); cmds.add("probe");
        if (!cmds.contains(cmd)) {
            System.out.println("未知命令: " + cmd);
            printUsage();
            return;
        }

        // 收集命令剩余参数(第一个-cmd之后的非选项参数)
        String[] rest = collectRest(args);

        ChannelSftp sftp = null;
        try {
            sftp = connect(host, port, user, password);
            System.out.println("连接成功: " + host + ":" + port + " user=" + user);
            execute(sftp, cmd, rest);
        } catch (Exception e) {
            System.out.println("操作失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (sftp != null && sftp.isConnected()) {
                sftp.disconnect();
            }
            if (sshSession != null && sshSession.isConnected()) {
                sshSession.disconnect();
            }
        }
    }

    /** 收集-cmd之后的非选项参数 */
    private static String[] collectRest(String[] args) {
        java.util.List<String> rest = new java.util.ArrayList<String>();
        boolean afterCmd = false;
        for (int i = 0; i < args.length; i++) {
            if ("-cmd".equals(args[i])) {
                afterCmd = true;
                i++;
                continue;
            }
            if (afterCmd) {
                rest.add(args[i]);
            }
        }
        return rest.toArray(new String[0]);
    }

    private static void printUsage() {
        System.out.println("用法:");
        System.out.println("  java -cp jsch.jar:sftp_client.jar com.zzzj.soft.sftp.util.SftpClient \\");
        System.out.println("       -h 主机 -P 端口 -u 用户 -pw 密码 -cmd 命令 [命令参数...]");
        System.out.println("说明: -h -P -u -pw -cmd 均为必填");
        System.out.println("命令:");
        System.out.println("  pwd                 显示当前目录(判断是否chroot)");
        System.out.println("  ls [路径]           列目录,默认 /");
        System.out.println("  put <本地文件> <远端目录>   上传文件");
        System.out.println("  get <远端文件> <本地路径>   下载文件");
        System.out.println("  rm <远端文件>              删除远端文件");
        System.out.println("  mkdir <远端目录>           创建远端目录(逐级创建)");
        System.out.println("  probe [路径...]           探查路径是否存在及权限");
        System.out.println("示例:");
        System.out.println("  ./sftp_client.sh -h 主机IP -P 端口 -u 用户 -pw '密码' -cmd pwd");
        System.out.println("  ./sftp_client.sh -h 主机IP -P 端口 -u 用户 -pw '密码' -cmd ls /upload");
        System.out.println("  ./sftp_client.sh -h 主机IP -P 端口 -u 用户 -pw '密码' -cmd put ./dz.json /upload");
    }

    /**
     * 建立SFTP连接,复用已验证的算法配置(兼容新老服务器,JDK1.8原生可用)
     */
    public static ChannelSftp connect(String host, String sOnlineSftpPort, String username, String password)
            throws Exception {
        int port = Integer.parseInt(sOnlineSftpPort);
        JSch jsch = new JSch();
        sshSession = jsch.getSession(username, host, port);
        sshSession.setPassword(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Properties sshConfig = new Properties();
        sshConfig.put("StrictHostKeyChecking", "no");
        sshConfig.put("server_host_key", "ssh-ed25519,rsa-sha2-512,rsa-sha2-256,ecdsa-sha2-nistp256,ssh-rsa");
        sshConfig.put("kex", "ecdh-sha2-nistp256,ecdh-sha2-nistp384,ecdh-sha2-nistp521,diffie-hellman-group14-sha1,diffie-hellman-group1-sha1");
        sshConfig.put("cipher.s2c", "aes128-ctr,aes192-ctr,aes256-ctr,aes128-cbc,3des-cbc");
        sshConfig.put("cipher.c2s", "aes128-ctr,aes192-ctr,aes256-ctr,aes128-cbc,3des-cbc");
        sshConfig.put("mac.s2c", "hmac-sha2-256,hmac-sha2-512,hmac-sha1");
        sshConfig.put("mac.c2s", "hmac-sha2-256,hmac-sha2-512,hmac-sha1");
        sshConfig.put("CheckKexes", "none");
        sshConfig.put("CheckCiphers", "none");
        sshConfig.put("CheckSignatures", "none");
        sshSession.setConfig(sshConfig);
        sshSession.setTimeout(30000);
        sshSession.connect();
        Channel channel = sshSession.openChannel("sftp");
        channel.connect();
        return (ChannelSftp) channel;
    }

    private static void execute(ChannelSftp sftp, String cmd, String[] rest) throws Exception {
        if ("pwd".equals(cmd)) {
            System.out.println("pwd: " + sftp.pwd());
        } else if ("ls".equals(cmd)) {
            String path = rest.length > 0 ? rest[0] : "/";
            listDir(sftp, path);
        } else if ("put".equals(cmd)) {
            if (rest.length < 2) {
                System.out.println("put 需要参数: put <本地文件> <远端目录>");
                return;
            }
            upload(sftp, rest[0], rest[1]);
        } else if ("get".equals(cmd)) {
            if (rest.length < 2) {
                System.out.println("get 需要参数: get <远端文件> <本地路径>");
                return;
            }
            download(sftp, rest[0], rest[1]);
        } else if ("rm".equals(cmd)) {
            if (rest.length < 1) {
                System.out.println("rm 需要参数: rm <远端文件>");
                return;
            }
            sftp.rm(rest[0]);
            System.out.println("已删除: " + rest[0]);
        } else if ("mkdir".equals(cmd)) {
            if (rest.length < 1) {
                System.out.println("mkdir 需要参数: mkdir <远端目录>");
                return;
            }
            mkdirs(sftp, rest[0]);
            System.out.println("已创建目录: " + rest[0]);
        } else if ("probe".equals(cmd)) {
            if (rest.length == 0) {
                probePath(sftp, "/");
            } else {
                for (String p : rest) {
                    probePath(sftp, p);
                }
            }
        }
    }

    private static void listDir(ChannelSftp sftp, String path) {
        System.out.println("目录: " + path);
        try {
            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> list = sftp.ls(path);
            System.out.println("共 " + list.size() + " 项:");
            for (ChannelSftp.LsEntry en : list) {
                SftpATTRS a = en.getAttrs();
                String type = a.isDir() ? "D" : (a.isReg() ? "F" : "?");
                System.out.println("  [" + type + "] " + en.getFilename() + "  权限=" + a.getPermissionsString());
            }
        } catch (Exception e) {
            System.out.println("ls 失败: " + e.getMessage());
        }
    }

    private static void upload(ChannelSftp sftp, String localFile, String remoteDir) throws Exception {
        File f = new File(localFile);
        if (!f.exists() || !f.isFile()) {
            System.out.println("本地文件不存在: " + localFile);
            return;
        }
        try {
            sftp.cd(remoteDir);
        } catch (Exception e) {
            System.out.println("远端目录不存在: " + remoteDir + " (可用 mkdir 创建)");
            return;
        }
        try (InputStream in = new FileInputStream(f)) {
            sftp.put(in, f.getName());
        }
        System.out.println("上传成功: " + localFile + " -> " + remoteDir + "/" + f.getName());
    }

    private static void download(ChannelSftp sftp, String remoteFile, String localPath) throws Exception {
        File outFile = new File(localPath);
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (OutputStream out = new FileOutputStream(outFile)) {
            sftp.get(remoteFile, out);
        }
        System.out.println("下载成功: " + remoteFile + " -> " + localPath);
    }

    private static void mkdirs(ChannelSftp sftp, String directory) throws Exception {
        if (directory == null || directory.length() == 0) {
            return;
        }
        try {
            sftp.cd(directory);
            return;
        } catch (Exception e) {
            // 不存在,继续创建
        }
        String[] dirs = directory.replace("\\", "/").split("/");
        StringBuilder cur = new StringBuilder();
        for (String dir : dirs) {
            if (dir.length() == 0) {
                continue;
            }
            cur.append("/").append(dir);
            String path = cur.toString();
            try {
                sftp.cd(path);
            } catch (Exception e) {
                sftp.mkdir(path);
                sftp.cd(path);
            }
        }
    }

    private static void probePath(ChannelSftp sftp, String path) {
        System.out.println("--------------------------------------------------");
        System.out.println("路径: " + path);
        try {
            SftpATTRS attrs = sftp.lstat(path);
            System.out.println("  lstat OK: 类型=" + (attrs.isDir() ? "目录" : (attrs.isReg() ? "文件" : "其他"))
                    + " 权限=" + attrs.getPermissionsString() + " uid=" + attrs.getUId() + " gid=" + attrs.getGId());
        } catch (Exception e) {
            System.out.println("  lstat FAIL: " + e.getMessage());
        }
        try {
            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> list = sftp.ls(path);
            System.out.println("  ls 内容(" + list.size() + "项):");
            for (ChannelSftp.LsEntry en : list) {
                SftpATTRS a = en.getAttrs();
                System.out.println("    " + (a.isDir() ? "[D]" : (a.isReg() ? "[F]" : "[?]")) + " " + en.getFilename()
                        + "  权限=" + a.getPermissionsString());
            }
        } catch (Exception e) {
            System.out.println("  ls FAIL: " + e.getMessage());
        }
    }
}
