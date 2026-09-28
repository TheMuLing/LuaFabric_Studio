package com.androlua;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 防火墙判定引擎（Lua 侧 firewall.lua 与 Java 侧 luajava 拦截共用）。
 *
 * <p>仅 IDE 运行会话激活（ConsoleBridgeImpl 注入 firewall.lua 时 reconfigure(active=true)）；
 * 打包产物恒 inactive → 所有判定直通放行，零行为污染（体积几 KB）。
 *
 * <p>判定规则（已与用户确认）：
 * <ul>
 *   <li>越级写入：目标在 storageRoot/project/ 下、且非当前项目自身 → 拦（增删改），读放行</li>
 *   <li>普通文件（/storage/emulated/0 下非项目区）→ 放行；外部 dex/so 加载 → 放行</li>
 *   <li>自我守护：目标为 storageRoot 或其祖先（删除/移动/改名容器根会连带删光）→ 拦</li>
 * </ul>
 */
public final class FirewallGate {

    /** 拦截事件回调（IDE 宿主注册：弹窗 + 计数）。 */
    public interface Reporter {
        /** @param kind 1=越级写入 2=自我守护；projectName 当前运行项目名；target 被拦路径 */
        void onBlock(int kind, String projectName, String target);
    }

    private static volatile boolean active;
    private static volatile String storageRoot;  // e.g. /storage/emulated/0/LuaFabric-Studio
    private static volatile String projectRoot;  // e.g. /storage/emulated/0/LuaFabric-Studio/project/狐刻星
    private static volatile String projectName;
    private static volatile boolean crossWriteGuard;
    private static volatile boolean selfGuard;
    private static volatile Reporter reporter;

    /** 拦截原因文案（弹窗/返回值共用）。 */
    public static final String SELF_MSG = "防火墙：禁止操作 LuaFabric-Studio/ 根目录";
    public static final String CROSS_MSG = "防火墙：禁止越级写入其它项目";

    private FirewallGate() {}

    /** IDE 会话启动时调用；projectDir 为当前运行项目目录（含项目名）。 */
    public static void reconfigure(String storageRootDir, String projectDir,
                                   boolean crossWrite, boolean self, Reporter rep) {
        storageRoot = norm(storageRootDir);
        projectRoot = norm(projectDir);
        projectName = projectDir == null ? null : new File(projectDir).getName();
        crossWriteGuard = crossWrite;
        selfGuard = self;
        reporter = rep;
        active = true;
    }

    /** 会话结束时停用（产物/退出后恒放行）。 */
    public static void deactivate() {
        active = false;
        storageRoot = null;
        projectRoot = null;
        projectName = null;
        crossWriteGuard = false;
        selfGuard = false;
        reporter = null;
    }

    public static boolean isActive() {
        return active;
    }

    /**
     * Lua 侧 io.write / os.remove / os.rename / shell 路径 / Java 侧 File/LuaUtil 统一写判定入口。
     *
     * @return null=放行；非 null=拦截原因（已上报计数）
     */
    public static String checkWrite(String target) {
        if (!active) return null;
        String p = norm(target);
        if (p == null || p.isEmpty()) return null;

        // 自我守护：删除/移动/改名容器根或其祖先 → 连带删光
        if (selfGuard && storageRoot != null && isSelfDelete(p)) {
            report(2, p, SELF_MSG);
            return SELF_MSG;
        }

        // 越级写入：项目区内、非当前项目 → 拦
        if (crossWriteGuard && projectRoot != null && storageRoot != null) {
            if (isUnder(p, projectRoot)) return null;            // 操作自己 → 放行
            if (isUnder(p, storageRoot + "/project")) {           // 其它项目区
                report(1, p, CROSS_MSG);
                return CROSS_MSG;
            }
        }
        return null;
    }

    /** 是否应放行（供 Java 侧 LuaUtil/File 拦截：命中=应拦，最终判定同一引擎）。 */
    public static boolean shouldBlock(String target) {
        return checkWrite(target) != null;
    }

    /**
     * 重命名/移动判定：源与目标双侧均判。
     * - 自我守护：源或目标命中容器根（storageRoot/projectRoot 或其祖先）→ 拦
     *   （重命名容器根 = 连带删光全项目，等同删除）
     * - 越级写入：源或目标命中其它项目区 → 拦（从 B 移走 = 删 B；移到 B = 写 B）
     */
    public static String checkRename(String from, String to) {
        if (!active) return null;
        String f = norm(from);
        String t = norm(to);
        if (selfGuard && storageRoot != null) {
            // 自我保护：源/目标 = 容器根或其祖先 / 命中 projectRoot 本体 → 拦
            // （projectRoot 是 isUnder 判定边界，isSelfDelete 只覆盖 storageRoot，需单列）
            if (isSelfDelete(f) || isSelfDelete(t)
                    || (projectRoot != null && (f.equals(projectRoot) || t.equals(projectRoot)))) {
                report(2, f, SELF_MSG);
                return SELF_MSG;
            }
        }
        if (crossWriteGuard && projectRoot != null && storageRoot != null) {
            if (crossHit(f)) return CROSS_MSG;
            if (crossHit(t)) return CROSS_MSG;
        }
        return null;
    }

    /** f 是否命中其它项目区（越级写入判定）。 */
    private static boolean crossHit(String p) {
        if (p == null) return false;
        if (isUnder(p, projectRoot)) return false;           // 操作自己 → 放行
        if (isUnder(p, storageRoot + "/project")) {           // 其它项目区
            report(1, p, CROSS_MSG);
            return true;
        }
        return false;
    }

    /** shell 危险命令（Java 侧 Runtime.exec / ProcessBuilder.start 用；与 firewall.lua 词法表一致）。 */
    private static final Set<String> DANGER = new HashSet<>(Arrays.asList(
            "rm", "mv", "cp", "mkdir", "rmdir", "touch", "mkfs", "truncate",
            "install", "dd", "shred", "tee", "unlink", "ln"));

    /**
     * shell 命令串词法判定：提取危险命令的位置参数 / dd of= 目标 → 交 checkWrite 判定。
     * 解析不出确切路径（通配符等）→ 放行（与用户确认的规则一致）。
     */
    public static String checkShellCommand(String cmd) {
        if (cmd == null || cmd.trim().isEmpty()) return null;
        String[] tokens = cmd.trim().split("\\s+");
        int i = 0;
        while (i < tokens.length) {
            String tok = tokens[i].trim();
            if (tok.isEmpty()) {
                i++;
                continue;
            }
            String name = tok.split("[^A-Za-z0-9_+.-]")[0];
            if (DANGER.contains(name)) {
                int j = i + 1;
                // 跳过本命令 flag 与 a=b 赋值（dd 的 of= 单列处理）
                while (j < tokens.length
                        && (tokens[j].startsWith("-")
                        || tokens[j].matches("^\\$\\w+=.*")
                        || tokens[j].matches("^\\w+=.*"))) {
                    j++;
                }
                // 位置参数即候选路径；遇分隔符/重定向/新命令停止
                while (j < tokens.length) {
                    String t = tokens[j];
                    if (";".equals(t) || "&&".equals(t) || "|".equals(t) || "||".equals(t)
                            || "$(".equals(t) || ">".equals(t) || ">>".equals(t)) {
                        if (">".equals(t) || ">>".equals(t)) j += 2; else j++;
                        break;
                    }
                    if (t.startsWith("-") || t.matches("^\\w+=.*")) break;
                    if (t.matches("^\\d?>.*")) break;
                    String r = checkWrite(t);
                    if (r != null) return r;
                    j++;
                }
                // dd 的 of= 输出目标也判
                if ("dd".equals(name)) {
                    for (int k = i + 1; k < tokens.length; k++) {
                        String tk = tokens[k];
                        if (tk.startsWith("of=")) {
                            String of = tk.substring(3);
                            if (!of.isEmpty()) {
                                String r = checkWrite(of);
                                if (r != null) return r;
                            }
                        }
                    }
                }
                i = j;
            } else {
                i++;
            }
        }
        return null;
    }

    /** p 是否为 storageRoot 或其后代（祖先删除/移动会连带容器根）。 */
    private static boolean isSelfDelete(String p) {
        if (p.equals(storageRoot)) return true;
        return storageRoot.startsWith(p + "/");
    }

    /** child 是否等于 base 或位于 base 之下。 */
    private static boolean isUnder(String child, String base) {
        base = norm(base);
        return child.equals(base) || child.startsWith(base + "/");
    }

    private static String norm(String s) {
        if (s == null) return null;
        try {
            File f = new File(s);
            String c = f.getCanonicalPath();
            return c.endsWith("/") && c.length() > 1 ? c.substring(0, c.length() - 1) : c;
        } catch (Exception e) {
            return new File(s).getAbsolutePath();
        }
    }

    private static void report(int kind, String target, String msg) {
        Reporter r = reporter;
        if (r == null) return;
        try {
            r.onBlock(kind, projectName, target);
        } catch (Throwable ignored) {
        }
        if (msg != null) {
            android.util.Log.w("FirewallGate", msg + " -> " + target);
        }
    }
}