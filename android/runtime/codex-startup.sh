# Sourced by interactive login shells only. Exiting Codex returns to this shell.
case "$-" in
    *i*)
        if [ -z "${CODEX_TERMINAL_ENTERED:-}" ]; then
            export CODEX_TERMINAL_ENTERED=1
            printf '\nCodex Terminal — 输入 codex 启动，退出 Codex 后可继续使用命令行。\n\n'
            if [ ! -f "$HOME/.codex-terminal-no-autostart" ]; then
                codex
            fi
        fi
        ;;
esac
