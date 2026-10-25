package com.termux.shared.shell;

import android.content.Context;
import com.termux.shared.termux.TermuxConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Launch the unmodified Termux package prefix inside this app's private directory. */
public final class CodexRuntime {
    private CodexRuntime() {}

    public static String[] environment(Context context, boolean failsafe, String cwd) {
        List<String> env = new ArrayList<>(Arrays.asList(
            TermuxShellUtils.buildEnvironment(context, failsafe, cwd)));
        env.add("CODEX_NATIVE_DIR=" + context.getApplicationInfo().nativeLibraryDir);
        env.add("CODEX_APP_FILES=" + TermuxConstants.TERMUX_FILES_DIR_PATH);
        return env.toArray(new String[0]);
    }

    public static String[] command(String executable, String[] arguments) {
        // An Android failsafe shell must remain usable if the bootstrap is damaged.
        if ("/system/bin/sh".equals(executable)) {
            List<String> command = new ArrayList<>();
            command.add(executable);
            if (arguments != null) Collections.addAll(command, arguments);
            return command.toArray(new String[0]);
        }
        List<String> command = new ArrayList<>();
        command.add("/system/bin/sh");
        command.add(TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/libexec/codex-host");
        command.add(executable);
        if (arguments != null) Collections.addAll(command, arguments);
        return command.toArray(new String[0]);
    }
}
