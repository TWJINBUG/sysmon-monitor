#!/system/bin/sh
# 安装模块时顺手把配套 App 装上（覆盖安装、保留数据）
ui_print "- 安装 设备监控 App"
if [ -f "$MODPATH/app/sysmon.apk" ]; then
  pm install -r -d "$MODPATH/app/sysmon.apk" 2>&1 | while read -r l; do ui_print "  $l"; done
fi
ui_print "- 内存采集服务将在开机后自动运行"
