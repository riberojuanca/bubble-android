#!/usr/bin/env bash
# Usage: [STOCK=stock-emulator.txt] try-profile.sh <keep-list-file>
# Creates a PRIVATE profile on the emulator excluding every package of the stock
# profile set that is NOT in the keep list, then checks that no system process
# crashes after creation, start, reboot and restart. Prints PASS/FAIL.
# Run reset.sh (restores the "clean" snapshot) before each attempt.
set -u
ADB="${ADB:-adb} -s ${SERIAL:-emulator-5554}"
DIR=$(cd "$(dirname "$0")" && pwd)
KEEP=$1
export LC_ALL=C

ss_pid() { $ADB shell pidof system_server 2>/dev/null | tr -d '\r'; }
wait_boot() { # $1 = timeout seconds
  local end=$((SECONDS + $1))
  while [ $SECONDS -lt $end ]; do
    [ "$($ADB shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && return 0
    timeout 5 $ADB wait-for-device 2>/dev/null; sleep 3
  done
  return 1
}
ui_pid() { $ADB shell ps -A -o PID,USER,NAME 2>/dev/null | awk '$3=="com.android.systemui" && $2 ~ /^u0_/ {print $1}'; }
crashes() { $ADB logcat -b crash -d 2>/dev/null | grep -E "FATAL EXCEPTION|Process: " | grep -c "Process: "; }
stable() { # system_server and systemui pids unchanged, no new crash, for $1 seconds
  local p0 u0; p0=$(ss_pid); u0=$(ui_pid); sleep "$1"
  [ -n "$p0" ] && [ "$(ss_pid)" = "$p0" ] && [ -n "$u0" ] \
    && [ "$(ui_pid)" = "$u0" ] && [ "$(crashes)" = 0 ]
}

sort -u "$KEEP" > "$DIR/.keep"
comm -23 "${STOCK:-$DIR/stock-emulator.txt}" "$DIR/.keep" > "$DIR/.disallow"
n=$(wc -l < "$DIR/.disallow")
args="s16 Bubble s16 android.os.usertype.profile.PRIVATE i32 0 i32 0 i32 $n"
for p in $(cat "$DIR/.disallow"); do args="$args s16 $p"; done

$ADB logcat -b crash -c
p0=$(ss_pid)
out=$($ADB shell "service call user 5 $args" 2>&1)
uid=$($ADB shell pm list users | tr -d '\r' | sed -n 's/.*UserInfo{\([0-9]*\):Bubble:.*/\1/p')
[ -z "$uid" ] && { echo "FAIL create: $out" | head -3; exit 1; }
stable 20 || { echo "FAIL crash after create (user $uid)"; $ADB logcat -b crash -d | grep -E "Process:|Exception" | sort | uniq -c | head -5; exit 1; }

$ADB shell am start-user -w "$uid" >/dev/null 2>&1
stable 30 || { echo "FAIL crash after start-user $uid"; $ADB logcat -b crash -d | grep -E "Process:|Exception" | sort | uniq -c | head -5; exit 1; }
installed=$($ADB shell pm list packages --user "$uid" | wc -l)

$ADB reboot
sleep 10
$ADB logcat -b crash -c 2>/dev/null
wait_boot 240 || { echo "FAIL no boot_completed after reboot (user $uid, $installed pkgs)"; exit 1; }
stable 45 || { echo "FAIL crash after reboot"; $ADB logcat -b crash -d | grep -E "Process:|Exception" | sort | uniq -c | head -5; exit 1; }
$ADB shell am start-user -w "$uid" >/dev/null 2>&1
stable 30 || { echo "FAIL crash on start-user after reboot"; $ADB logcat -b crash -d | grep -E "Process:|Exception" | sort | uniq -c | head -5; exit 1; }
echo "PASS user=$uid installed=$installed excluded=$n"
