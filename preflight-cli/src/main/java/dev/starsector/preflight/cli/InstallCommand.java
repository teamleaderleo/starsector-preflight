package dev.starsector.preflight.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

final class InstallCommand {
    private InstallCommand() {
    }

    static int execute(CommandLine options) throws Exception {
        Platform platform = Platform.current();
        Path home = Path.of(System.getProperty("user.home"));
        DiscoveryResult discovery = StarsectorDiscovery.discover(
                platform,
                home,
                Path.of(System.getProperty("user.dir")),
                System.getenv(),
                options.game(),
                options.launcher());
        LaunchTarget target = discovery.selected();
        if (target == null) {
            return RunCommand.doctor(options);
        }

        Path installDirectory = home.resolve(".starsector-preflight").resolve("bin");
        Files.createDirectories(installDirectory);
        Path installedJar = installDirectory.resolve("preflight.jar");
        Path sourceJar = SelfJar.locate();
        if (!sourceJar.equals(installedJar)) {
            Files.copy(
                    sourceJar,
                    installedJar,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
        }

        return switch (platform) {
            case MAC -> installMac(home, installedJar, target.installRoot());
            case LINUX -> installLinux(home, installedJar, target.installRoot());
            case WINDOWS -> installWindows(home, installedJar, target.installRoot(), System.getenv());
            case OTHER -> {
                System.err.println("Automatic launcher installation is unsupported on this operating system. Use: java -jar "
                        + installedJar + " run --game " + target.installRoot());
                yield 4;
            }
        };
    }

    private static int installMac(Path home, Path jar, Path game) throws IOException {
        Path app = home.resolve("Applications").resolve("Starsector Preflight.app");
        Path macos = app.resolve("Contents").resolve("MacOS");
        Files.createDirectories(macos);
        Path executable = macos.resolve("starsector-preflight");
        String script = "#!/bin/sh\nexec "
                + shellQuote(javaExecutable())
                + " -jar "
                + shellQuote(jar.toString())
                + " run --game "
                + shellQuote(game.toString())
                + " \"$@\"\n";
        Files.writeString(executable, script, StandardCharsets.UTF_8);
        makeExecutable(executable);

        String plist = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0"><dict>
                  <key>CFBundleName</key><string>Starsector Preflight</string>
                  <key>CFBundleDisplayName</key><string>Starsector Preflight</string>
                  <key>CFBundleIdentifier</key><string>dev.starsector.preflight.launcher</string>
                  <key>CFBundleVersion</key><string>1</string>
                  <key>CFBundlePackageType</key><string>APPL</string>
                  <key>CFBundleExecutable</key><string>starsector-preflight</string>
                  <key>LSUIElement</key><true/>
                </dict></plist>
                """;
        Files.writeString(app.resolve("Contents").resolve("Info.plist"), plist, StandardCharsets.UTF_8);
        System.out.println("Installed macOS launcher: " + app);
        return 0;
    }

    private static int installLinux(Path home, Path jar, Path game) throws IOException {
        Path localBin = home.resolve(".local").resolve("bin");
        Files.createDirectories(localBin);
        Path launcher = localBin.resolve("starsector-preflight");
        String script = "#!/bin/sh\nexec "
                + shellQuote(javaExecutable())
                + " -jar "
                + shellQuote(jar.toString())
                + " run --game "
                + shellQuote(game.toString())
                + " \"$@\"\n";
        Files.writeString(launcher, script, StandardCharsets.UTF_8);
        makeExecutable(launcher);

        Path applications = home.resolve(".local").resolve("share").resolve("applications");
        Files.createDirectories(applications);
        Path desktop = applications.resolve("starsector-preflight.desktop");
        String desktopFile = "[Desktop Entry]\n"
                + "Type=Application\n"
                + "Name=Starsector Preflight\n"
                + "Exec=" + launcher + "\n"
                + "Terminal=false\n"
                + "Categories=Game;Utility;\n";
        Files.writeString(desktop, desktopFile, StandardCharsets.UTF_8);
        System.out.println("Installed command: " + launcher);
        System.out.println("Installed desktop entry: " + desktop);
        return 0;
    }

    private static int installWindows(
            Path home,
            Path jar,
            Path game,
            Map<String, String> environment) throws IOException {
        String localAppData = environment.get("LOCALAPPDATA");
        Path directory = localAppData == null || localAppData.isBlank()
                ? home.resolve("AppData").resolve("Local").resolve("Starsector Preflight")
                : Path.of(localAppData).resolve("Starsector Preflight");
        Files.createDirectories(directory);
        Path command = directory.resolve("Starsector Preflight.cmd");
        String content = "@echo off\r\n\""
                + javaExecutable()
                + "\" -jar \""
                + jar
                + "\" run --game \""
                + game
                + "\" %*\r\n";
        Files.writeString(command, content, StandardCharsets.UTF_8);
        System.out.println("Installed Windows launcher: " + command);
        return 0;
    }

    private static String javaExecutable() {
        String executable = Platform.current() == Platform.WINDOWS ? "java.exe" : "java";
        Path bundled = Path.of(System.getProperty("java.home")).resolve("bin").resolve(executable);
        return Files.isRegularFile(bundled) ? bundled.toString() : executable;
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static void makeExecutable(Path file) throws IOException {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
            EnumSet<PosixFilePermission> updated = EnumSet.copyOf(permissions);
            updated.add(PosixFilePermission.OWNER_EXECUTE);
            updated.add(PosixFilePermission.GROUP_EXECUTE);
            updated.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(file, updated);
        } catch (UnsupportedOperationException ignored) {
            file.toFile().setExecutable(true, false);
        }
    }
}
