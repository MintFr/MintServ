package fr.nantral.mint;

import org.jetbrains.annotations.*;
import java.io.*;

/** Forward a process's stdout with an optional prefix
 *
 * <p>Usage:
 * <pre>
 * var process = ...;
 * var stdoutRun = new ForwardStdout(process.getInputStream(), "p: ");
 * var stdoutThread = new Thread(stdoutRun);
 * </pre>
 * </p>
 */
public class ForwardStdout implements Runnable {

    private BufferedReader bufReader;
    private String prefix;

    public ForwardStdout(@NotNull InputStream reader, @Nullable String prefix) {
        this.bufReader = new BufferedReader(new InputStreamReader(reader));
        this.prefix = prefix != null ? prefix : "";
    }

    @Override
    public void run() {
        bufReader.lines().forEach(x -> System.out.println(prefix + x));
    }
}
