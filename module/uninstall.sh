#!/system/bin/sh
# 卸载模块时清掉采集进程
pkill -f sysmon_mem 2>/dev/null
for p in $(pidof sh); do
  grep -q "sysmon_mem" /proc/$p/cmdline 2>/dev/null && kill $p 2>/dev/null
done
rm -f /data/data/com.dsh.sysmon/files/root_mem.txt
