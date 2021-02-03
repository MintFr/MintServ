package fr.nantral.mint;

import org.jetbrains.annotations.*;
import java.io.*;

/** Forward a process's stderr with an optional prefix
 *
 * <p>Usage:
 * <pre>
 * var process = ...;
 * var stderrRun = new ForwardStdout(process.getInputStream(), "p: ");
 * var stderrThread = new Thread(stderrRun);
 * </pre>
 * </p>
 */
public class ForwardStderr implements Runnable {

    private BufferedReader bufReader;
    private String prefix;

    public ForwardStderr(@NotNull InputStream reader, @Nullable String prefix) {
        this.bufReader = new BufferedReader(new InputStreamReader(reader));
        this.prefix = prefix != null ? prefix : "";
    }

    @Override
    public void run() {
        bufReader.lines().forEach(x -> System.err.println(prefix + x));
    }
}
