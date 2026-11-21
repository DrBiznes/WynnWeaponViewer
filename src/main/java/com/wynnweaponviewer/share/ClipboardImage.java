package com.wynnweaponviewer.share;

import com.wynnweaponviewer.WynnWeaponViewer;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Puts a PNG on the system clipboard as an image.
 *
 * <p>Minecraft runs Java's AWT headless and GLFW's clipboard only takes text, so this hands the file to the OS's own
 * tool. The path goes through an environment variable, never into the command itself. Blocking: call off the
 * render thread.
 */
public final class ClipboardImage {

    private static final String PATH_VARIABLE = "WWV_SHARE_PNG";
    private static final long TIMEOUT_SECONDS = 15;

    /**
     * Windows: the raw PNG (keeps transparency; what Discord and browsers paste) plus a bitmap for older apps.
     * Windows PowerShell runs single-threaded-apartment, which the clipboard needs.
     */
    private static final String WINDOWS_SCRIPT = String.join("; ",
            "$ErrorActionPreference = 'Stop'",
            "Add-Type -AssemblyName System.Windows.Forms, System.Drawing",
            "$bytes = [IO.File]::ReadAllBytes($env:" + PATH_VARIABLE + ")",
            "$data = New-Object Windows.Forms.DataObject",
            "$data.SetData('PNG', $false, [IO.MemoryStream]::new($bytes))",
            "$data.SetImage([Drawing.Image]::FromStream([IO.MemoryStream]::new($bytes)))",
            "[Windows.Forms.Clipboard]::SetDataObject($data, $true)");

    private static final String MAC_SCRIPT = "set the clipboard to (read (POSIX file (system attribute \""
            + PATH_VARIABLE + "\")) as «class PNGf»)";

    private ClipboardImage() {}

    /** @return whether the image is now on the clipboard */
    public static boolean copyPng(Path png) {
        Path absolute = png.toAbsolutePath();
        try {
            return switch (Util.getPlatform()) {
                case WINDOWS -> run(new ProcessBuilder(windowsPowerShell(), "-NoProfile", "-NonInteractive", "-STA",
                        "-Command", WINDOWS_SCRIPT), absolute, true);
                case OSX -> run(new ProcessBuilder("osascript", "-e", MAC_SCRIPT), absolute, true);
                case LINUX -> copyLinux(absolute);
                default -> false;
            };
        } catch (IOException e) {
            WynnWeaponViewer.LOGGER.warn("Couldn't copy the item card to the clipboard", e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static boolean copyLinux(Path png) throws IOException, InterruptedException {
        List<String> command = System.getenv("WAYLAND_DISPLAY") != null
                ? List.of("wl-copy", "--type", "image/png")
                : List.of("xclip", "-selection", "clipboard", "-t", "image/png");
        ProcessBuilder builder = new ProcessBuilder(command).redirectInput(png.toFile());
        // xclip / wl-copy leave a child serving the clipboard; reading its output would wait for that to exit.
        return run(builder, png, false);
    }

    private static boolean run(ProcessBuilder builder, Path png, boolean captureOutput)
            throws IOException, InterruptedException {
        builder.environment().put(PATH_VARIABLE, png.toString());
        builder.redirectErrorStream(true);
        if (!captureOutput) builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Process process = builder.start();
        // Drain output so a chatty tool can't block on a full pipe.
        byte[] output = captureOutput ? process.getInputStream().readAllBytes() : new byte[0];
        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            WynnWeaponViewer.LOGGER.warn("Clipboard copy timed out: {}", builder.command().get(0));
            return false;
        }
        if (process.exitValue() != 0) {
            WynnWeaponViewer.LOGGER.warn("Clipboard copy failed ({}): {}", builder.command().get(0),
                    new String(output).trim());
            return false;
        }
        return true;
    }

    private static String windowsPowerShell() {
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot != null) {
            Path path = Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
            if (Files.isRegularFile(path)) return path.toString();
        }
        return "powershell.exe";
    }
}
