#!/system/bin/sh
# KernelSU / Magisk 模块服务：root 侧采集各进程 PSS，App 只负责读文件
MODDIR=${0%/*}
APPF=/data/data/com.dsh.sysmon/files
OUT=$APPF/root_mem.txt

# 等系统启动完成，避免开机阶段刷爆 IO
while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done
sleep 5

while :; do
  if [ -d "$APPF" ]; then
    uid=$(stat -c %u "$APPF" 2>/dev/null)
    tmp="$APPF/.memtmp"
    {
      echo "@T $(date +%s)"
      for d in /proc/[0-9]*; do
        [ -r "$d/smaps_rollup" ] || continue
        name=$(tr '\0' ' ' < "$d/cmdline" 2>/dev/null)
        [ -z "$name" ] && continue
        pss=$(awk '/^Pss:/{print $2; exit}' "$d/smaps_rollup" 2>/dev/null)
        [ -n "$pss" ] && echo "${d#/proc/}|$pss|$name"
      done
      echo "@END"
    } > "$tmp" 2>/dev/null
    if [ -n "$uid" ]; then
      chown "$uid:$uid" "$tmp" 2>/dev/null
    fi
    chmod 600 "$tmp" 2>/dev/null
    mv -f "$tmp" "$OUT" 2>/dev/null
  fi
  sleep 3
done
