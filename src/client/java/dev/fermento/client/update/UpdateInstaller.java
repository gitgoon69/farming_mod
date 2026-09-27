package dev.fermento.client.update;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import dev.fermento.FermentoMod;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Downloads the JAR into {@code mods/} then starts a detached process
 * (like libautoupdate / ModUpdater) to swap after shutdown,
 * because Windows locks the loaded JAR.
 */
public final class UpdateInstaller {
	private static final String PENDING_NAME = "fermento-update.tmp";

	private UpdateInstaller() {
	}

	public static Path modsDir() {
		return FabricLoader.getInstance().getGameDir().resolve("mods");
	}

	public static boolean development() {
		return FabricLoader.getInstance().isDevelopmentEnvironment();
	}

	public static Path currentJar() {
		try {
			var source = FermentoMod.class.getProtectionDomain().getCodeSource();
			if (source == null || source.getLocation() == null) {
				return null;
			}
			Path path = Path.of(source.getLocation().toURI());
			if (Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
				return path;
			}
		} catch (Exception e) {
			FermentoMod.LOGGER.warn("Current JAR not found: {}", e.toString());
		}
		return null;
	}

	public static List<Path> installedJars() {
		List<Path> jars = new ArrayList<>();
		Path mods = modsDir();
		if (!Files.isDirectory(mods)) {
			return jars;
		}
		for (String glob : List.of("fermento*.jar", "farmingprofit*.jar")) {
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(mods, glob)) {
				for (Path path : stream) {
					String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
					if (name.contains("sources") || name.contains("-dev")) {
						continue;
					}
					Path normalized = path.toAbsolutePath().normalize();
					if (!jars.contains(normalized)) {
						jars.add(normalized);
					}
				}
			} catch (IOException e) {
				FermentoMod.LOGGER.warn("Scan mods/ : {}", e.toString());
			}
		}
		return jars;
	}

	public static void download(HttpClient http, String jarUrl, Path destination) throws IOException, InterruptedException {
		Files.createDirectories(destination.getParent());
		HttpRequest request = HttpRequest.newBuilder(URI.create(jarUrl))
				.timeout(Duration.ofMinutes(2))
				.header("User-Agent", "Fermento-Updater")
				.GET()
				.build();
		HttpResponse<Path> response = http.send(request, HttpResponse.BodyHandlers.ofFile(destination));
		if (response.statusCode() / 100 != 2) {
			Files.deleteIfExists(destination);
			throw new IOException("HTTP download " + response.statusCode());
		}
		if (Files.size(destination) < 1024) {
			Files.deleteIfExists(destination);
			throw new IOException("File too small, invalid download.");
		}
	}

	public static Path pendingFile() {
		return modsDir().resolve(PENDING_NAME);
	}

	public static Path destinationJar(String jarUrl, String version) {
		String fileName = fileNameFromUrl(jarUrl);
		if (fileName != null) {
			return modsDir().resolve(fileName);
		}
		return modsDir().resolve("fermento-" + version + ".jar");
	}

	private static String fileNameFromUrl(String jarUrl) {
		if (jarUrl == null || jarUrl.isBlank()) {
			return null;
		}
		try {
			String path = URI.create(jarUrl).getPath();
			int slash = path.lastIndexOf('/');
			String name = slash >= 0 ? path.substring(slash + 1) : path;
			if (name.toLowerCase(Locale.ROOT).endsWith(".jar") && !name.isBlank()) {
				return name;
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	public static boolean launchSwapAndExit(Path pending, Path destination, List<Path> oldJars) throws IOException {
		String command = captureCommandLine();
		boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
		if (windows) {
			launchWindows(pending, destination, oldJars, command);
		} else {
			launchUnix(pending, destination, oldJars, command);
		}
		return command != null && !command.isBlank();
	}

	private static void launchWindows(Path pending, Path dest, List<Path> oldJars, String command) throws IOException {
		Path script = modsDir().resolve("fermento-install.ps1");
		Path launcher = modsDir().resolve("fermento-install.vbs");
		Path exeFile = modsDir().resolve("fermento-relaunch-exe.txt");
		Path argsFile = modsDir().resolve("fermento-relaunch-args.txt");
		WindowsLaunch relaunch = splitWindowsLaunch(command);
		if (relaunch != null) {
			writeUtf8Bom(exeFile, relaunch.executable());
			writeUtf8Bom(argsFile, relaunch.arguments());
		} else {
			Files.deleteIfExists(exeFile);
			Files.deleteIfExists(argsFile);
			FermentoMod.LOGGER.warn("Update will install, but the game command line could not be read for relaunch.");
		}

		String gameDir = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize().toString();
		StringBuilder ps = new StringBuilder();
		ps.append("$ErrorActionPreference = 'Continue'\r\n");
		ps.append("$targetPid = ").append(ProcessHandle.current().pid()).append("\r\n");
		ps.append("$pending = ").append(psQuote(pending.toAbsolutePath().normalize().toString())).append("\r\n");
		ps.append("$dest = ").append(psQuote(dest.toAbsolutePath().normalize().toString())).append("\r\n");
		ps.append("$gameDir = ").append(psQuote(gameDir)).append("\r\n");
		ps.append("$exeFile = ").append(psQuote(exeFile.toAbsolutePath().normalize().toString())).append("\r\n");
		ps.append("$argsFile = ").append(psQuote(argsFile.toAbsolutePath().normalize().toString())).append("\r\n");
		ps.append("$log = ").append(psQuote(modsDir().resolve("fermento-update.log").toAbsolutePath().normalize().toString())).append("\r\n");
		ps.append("$old = @(\r\n");
		for (Path old : oldJars) {
			ps.append("  ").append(psQuote(old.toAbsolutePath().normalize().toString())).append("\r\n");
		}
		ps.append(")\r\n");
		ps.append("function Write-Log($m) { Add-Content -LiteralPath $log -Value ((Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + ' ' + $m) }\r\n");
		ps.append("Write-Log 'waiting for Minecraft to exit'\r\n");
		ps.append("$deadline = (Get-Date).AddSeconds(90)\r\n");
		ps.append("while ((Get-Process -Id $targetPid -ErrorAction SilentlyContinue) -and ((Get-Date) -lt $deadline)) { Start-Sleep -Seconds 1 }\r\n");
		ps.append("Start-Sleep -Seconds 2\r\n");
		ps.append("foreach ($path in $old) {\r\n");
		ps.append("  for ($i = 0; $i -lt 15; $i++) {\r\n");
		ps.append("    try {\r\n");
		ps.append("      if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Force -ErrorAction Stop }\r\n");
		ps.append("      break\r\n");
		ps.append("    } catch { Start-Sleep -Milliseconds 400 }\r\n");
		ps.append("  }\r\n");
		ps.append("}\r\n");
		ps.append("for ($i = 0; $i -lt 15; $i++) {\r\n");
		ps.append("  try { Move-Item -LiteralPath $pending -Destination $dest -Force -ErrorAction Stop; break } catch { Start-Sleep -Milliseconds 400 }\r\n");
		ps.append("}\r\n");
		ps.append("if (Test-Path -LiteralPath $pending) { Write-Log 'could not replace the mod jar' }\r\n");
		ps.append("if ((Test-Path -LiteralPath $exeFile) -and (Test-Path -LiteralPath $argsFile)) {\r\n");
		ps.append("  $exe = (Get-Content -LiteralPath $exeFile -Raw -Encoding UTF8).Trim().Trim([char]0xFEFF)\r\n");
		ps.append("  $arg = (Get-Content -LiteralPath $argsFile -Raw -Encoding UTF8).Trim().Trim([char]0xFEFF)\r\n");
		ps.append("  try {\r\n");
		ps.append("    $psi = New-Object System.Diagnostics.ProcessStartInfo\r\n");
		ps.append("    $psi.FileName = $exe\r\n");
		ps.append("    $psi.Arguments = $arg\r\n");
		ps.append("    $psi.WorkingDirectory = $gameDir\r\n");
		ps.append("    $psi.UseShellExecute = $false\r\n");
		ps.append("    [void][System.Diagnostics.Process]::Start($psi)\r\n");
		ps.append("    Remove-Item -LiteralPath $log -Force -ErrorAction SilentlyContinue\r\n");
		ps.append("  } catch {\r\n");
		ps.append("    Write-Log ('relaunch failed: ' + $_.Exception.Message)\r\n");
		ps.append("  }\r\n");
		ps.append("} else {\r\n");
		ps.append("  Write-Log 'no relaunch command'\r\n");
		ps.append("}\r\n");
		ps.append("Remove-Item -LiteralPath $exeFile, $argsFile -Force -ErrorAction SilentlyContinue\r\n");
		ps.append("Remove-Item -LiteralPath ").append(psQuote(script.toAbsolutePath().normalize().toString()));
		ps.append(", ").append(psQuote(launcher.toAbsolutePath().normalize().toString())).append(" -Force -ErrorAction SilentlyContinue\r\n");
		writeUtf8Bom(script, ps.toString());

		String psPath = script.toAbsolutePath().normalize().toString().replace("\"", "\"\"");
		String vbs = "Set sh = CreateObject(\"Wscript.Shell\")\r\n"
				+ "sh.Run \"powershell.exe -NoProfile -ExecutionPolicy Bypass -File \"\"" + psPath + "\"\"\", 0, False\r\n";
		Files.writeString(launcher, vbs, StandardCharsets.UTF_8);

		new ProcessBuilder("wscript.exe", "//B", "//nologo", launcher.toAbsolutePath().toString())
				.directory(modsDir().toFile())
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.redirectError(ProcessBuilder.Redirect.DISCARD)
				.start();
	}

	private static void launchUnix(Path pending, Path dest, List<Path> oldJars, String command) throws IOException {
		Path script = modsDir().resolve("fermento-install.sh");
		String gameDir = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize().toString();
		StringBuilder sh = new StringBuilder();
		sh.append("#!/bin/sh\n");
		sh.append("pid=").append(ProcessHandle.current().pid()).append("\n");
		sh.append("i=0\n");
		sh.append("while kill -0 \"$pid\" 2>/dev/null && [ \"$i\" -lt 90 ]; do\n");
		sh.append("  sleep 1\n");
		sh.append("  i=$((i+1))\n");
		sh.append("done\n");
		sh.append("sleep 2\n");
		for (Path old : oldJars) {
			sh.append("rm -f ").append(shQuote(old)).append("\n");
		}
		sh.append("mv -f ").append(shQuote(pending)).append(" ").append(shQuote(dest)).append("\n");
		if (command != null && !command.isBlank()) {
			sh.append("cd ").append(shQuoteString(gameDir)).append("\n");
			sh.append("nohup ").append(command).append(" >/dev/null 2>&1 &\n");
		} else {
			FermentoMod.LOGGER.warn("Update will install, but the game command line could not be read for relaunch.");
		}
		sh.append("rm -f ").append(shQuote(script)).append("\n");
		Files.writeString(script, sh.toString(), StandardCharsets.UTF_8);
		script.toFile().setExecutable(true);
		new ProcessBuilder("/bin/sh", "-c", "nohup " + shQuote(script) + " >/dev/null 2>&1 &")
				.directory(modsDir().toFile())
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.redirectError(ProcessBuilder.Redirect.DISCARD)
				.start();
	}

	private static String captureCommandLine() {
		var info = ProcessHandle.current().info();
		if (isWindows()) {
			String fromOs = captureWindowsCommandLine();
			if (fromOs != null && !fromOs.isBlank()) {
				return fromOs;
			}
		}
		if (info.commandLine().isPresent() && !info.commandLine().get().isBlank()) {
			return info.commandLine().get().trim();
		}
		String executable = info.command().orElse("");
		if (executable.isBlank()) {
			return null;
		}
		String[] args = info.arguments().orElse(null);
		if (!isWindows()) {
			StringBuilder shell = new StringBuilder(shQuoteString(executable));
			if (args != null) {
				for (String arg : args) {
					shell.append(' ').append(shQuoteString(arg));
				}
			}
			return shell.toString();
		}
		if (args == null || args.length == 0) {
			return null;
		}
		StringBuilder line = new StringBuilder();
		line.append('"').append(executable).append('"');
		for (String arg : args) {
			line.append(' ').append(quoteWindowsArg(arg));
		}
		return line.toString();
	}

	private static String captureWindowsCommandLine() {
		long pid = ProcessHandle.current().pid();
		Path output = modsDir().resolve("fermento-cmdline.txt");
		try {
			Files.createDirectories(modsDir());
			Files.deleteIfExists(output);
			ProcessBuilder builder = new ProcessBuilder(
					"powershell.exe",
					"-NoProfile",
					"-NonInteractive",
					"-Command",
					"$p = Get-CimInstance Win32_Process -Filter ('ProcessId=' + $env:FERMENTO_PID); "
							+ "Set-Content -LiteralPath $env:FERMENTO_CMDLINE -Value $p.CommandLine -Encoding utf8"
			);
			builder.environment().put("FERMENTO_PID", Long.toString(pid));
			builder.environment().put("FERMENTO_CMDLINE", output.toAbsolutePath().toString());
			builder.redirectErrorStream(true);
			Process process = builder.start();
			process.getInputStream().readAllBytes();
			if (!process.waitFor(8, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				return null;
			}
			if (!Files.isRegularFile(output)) {
				return null;
			}
			String text = Files.readString(output, StandardCharsets.UTF_8).replace("\uFEFF", "").trim();
			if (text.length() < 40 || text.length() > 32_000) {
				return null;
			}
			return text.replace("\r", " ").replace("\n", " ").trim();
		} catch (Exception e) {
			FermentoMod.LOGGER.warn("Launch command unreadable: {}", e.toString());
			return null;
		} finally {
			try {
				Files.deleteIfExists(output);
			} catch (IOException ignored) {
			}
		}
	}

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}

	private record WindowsLaunch(String executable, String arguments) {
	}

	private static WindowsLaunch splitWindowsLaunch(String command) {
		if (command == null || command.isBlank()) {
			return null;
		}
		String line = command.trim();
		String executable;
		String arguments;
		if (line.startsWith("\"")) {
			int end = line.indexOf('"', 1);
			if (end <= 1) {
				return null;
			}
			executable = line.substring(1, end);
			arguments = line.substring(end + 1).trim();
		} else {
			int space = line.indexOf(' ');
			if (space <= 0) {
				return null;
			}
			executable = line.substring(0, space);
			arguments = line.substring(space + 1).trim();
		}
		if (executable.isBlank() || arguments.length() < 20 || arguments.length() > 32_000) {
			return null;
		}
		return new WindowsLaunch(executable, arguments);
	}

	private static String psQuote(String value) {
		return "'" + value.replace("'", "''") + "'";
	}

	private static void writeUtf8Bom(Path path, String text) throws IOException {
		byte[] body = text.getBytes(StandardCharsets.UTF_8);
		byte[] data = new byte[body.length + 3];
		data[0] = (byte) 0xEF;
		data[1] = (byte) 0xBB;
		data[2] = (byte) 0xBF;
		System.arraycopy(body, 0, data, 3, body.length);
		Files.write(path, data);
	}

	private static String quoteWindowsArg(String arg) {
		if (arg.isEmpty()) {
			return "\"\"";
		}
		if (arg.indexOf(' ') < 0 && arg.indexOf('"') < 0) {
			return arg;
		}
		return "\"" + arg.replace("\"", "\\\"") + "\"";
	}

	private static String shQuote(Path path) {
		return shQuoteString(path.toAbsolutePath().normalize().toString());
	}

	private static String shQuoteString(String value) {
		return "'" + value.replace("'", "'\\''") + "'";
	}
}
