#!/system/bin/sh
# Internal helper. Production paths are supplied only by WallpaperLoop.java.
set -eu
export LC_ALL=C
dir=$1
state=$2
mode=$3
[ -d "$dir" ] && [ ! -L "$dir" ] || { echo '壁纸目录不存在或是符号链接，请先用主题管理导入本地视频。'; exit 1; }
[ -d "$state" ] && [ ! -L "$state" ] || exit 2
lock="$state/wallpaper.lock"
umask 077
[ ! -L "$lock" ] || exit 3
exec 9> "$lock"
flock -n 9 9>&9 || { echo '已有壁纸任务正在运行，或系统缺少 flock，请稍后重试。'; exit 3; }
# Kernel-held lock is released on process death/reboot, never a stale directory.
journal="$state/wallpaper-renames.tsv"
[ ! -L "$journal" ] || exit 5
valid_old() {
    case "$1" in LocalImport[_\&]*) ;; *) return 1 ;; esac
    suffix=${1#LocalImport}
    while :; do case "$suffix" in [_\&]*) suffix=${suffix#?} ;; *) break ;; esac; done
    [ "${#suffix}" -eq 32 ] || return 1
    case "$suffix" in *[!0-9a-fA-F]*) return 1 ;; esac
    canonical="LocalImport_&_$suffix"
}
digest() { sha256sum "$1" | cut -d ' ' -f 1; }
exists() { [ -e "$1" ] || [ -L "$1" ]; }
changed=0
normal=0
skipped=0
conflict=0
failed=0
case "$mode" in
apply)
    for src in "$dir"/LocalImport*; do
        exists "$src" || continue
        if [ ! -f "$src" ] || [ -L "$src" ]; then skipped=$((skipped+1)); continue; fi
        name=${src##*/}
        if ! valid_old "$name"; then skipped=$((skipped+1)); continue; fi
        if [ "$name" = "$canonical" ]; then normal=$((normal+1)); continue; fi
        dst="$dir/$canonical"
        if exists "$dst"; then conflict=$((conflict+1)); continue; fi
        # Only real MP4 containers: ignore metadata, partial downloads and unrelated files.
        magic=$(dd if="$src" bs=1 skip=4 count=4 2>/dev/null)
        if [ "$magic" != ftyp ]; then skipped=$((skipped+1)); continue; fi
        hash=$(digest "$src")
        [ "${#hash}" -eq 64 ] || { failed=$((failed+1)); continue; }
        # Write ahead: interruption before/after mv is handled idempotently by undo.
        printf '%s\t%s\t%s\n' "$hash" "$name" "$canonical" >> "$journal"
        if mv -n "$src" "$dst" && [ ! -e "$src" ] && [ -f "$dst" ] && [ ! -L "$dst" ] && [ "$(digest "$dst")" = "$hash" ]; then
            changed=$((changed+1))
        else
            failed=$((failed+1))
        fi
    done
    echo "已处理 $changed 个；已规范 $normal 个；跳过 $skipped 个；重名冲突 $conflict 个；失败 $failed 个。"
    echo '仅改文件名，不改视频内容；到主题管理重新选择壁纸后查看循环效果。'
    ;;
undo)
    if [ ! -s "$journal" ]; then echo '没有待撤销的壁纸改名。'; exit 0; fi
    # Validate the entire journal before interpreting any names as paths.
    tab=$(printf '\t')
    while IFS="$tab" read -r hash old new extra || [ -n "$hash$old$new$extra" ]; do
        [ -z "$extra" ] && [ "${#hash}" -eq 64 ] && valid_old "$old" && [ "$new" = "$canonical" ] && [ "$old" != "$new" ] || exit 6
        case "$hash" in *[!0-9a-f]*) exit 6 ;; esac
    done < "$journal"
    pending="$state/wallpaper-pending.tsv"
    [ ! -L "$pending" ] || exit 7
    : > "$pending"
    while IFS="$tab" read -r hash old new || [ -n "$hash$old$new" ]; do
        src="$dir/$new"
        dst="$dir/$old"
        restored=0
        if ! exists "$src" && [ -f "$dst" ] && [ ! -L "$dst" ] && [ "$(digest "$dst")" = "$hash" ]; then
            restored=1
        elif [ -f "$src" ] && [ ! -L "$src" ] && ! exists "$dst" && [ "$(digest "$src")" = "$hash" ]; then
            if mv -n "$src" "$dst" && [ ! -e "$src" ] && [ -f "$dst" ]; then restored=1; fi
        fi
        if [ "$restored" -eq 1 ]; then changed=$((changed+1)); else
            conflict=$((conflict+1))
            printf '%s\t%s\t%s\n' "$hash" "$old" "$new" >> "$pending"
        fi
    done < "$journal"
    mv -f "$pending" "$journal"
    echo "已撤销 $changed 个；冲突或内容变化 $conflict 个（保留记录，未覆盖文件）。"
    ;;
*) exit 8 ;;
esac
