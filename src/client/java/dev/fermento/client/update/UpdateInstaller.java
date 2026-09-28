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
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

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
		if (isWindows()) {
			return launchWindows(pending, destination, oldJars);
		}
		return launchUnix(pending, destination, oldJars);
	}

	private static boolean launchWindows(Path pending, Path dest, List<Path> oldJars) throws IOException {
		Path script = modsDir().resolve("fermento-install.ps1");
		Path vbs = modsDir().resolve("fermento-install.vbs");
		Path exeFile = modsDir().resolve("fermento-relaunch-exe.txt");
		Path argsFile = modsDir().resolve("fermento-relaunch-args.txt");
		Path envFile = modsDir().resolve("fermento-relaunch-env.json");
		Path launcherFile = modsDir().resolve("fermento-relaunch-launcher.txt");
		Path instanceFile = modsDir().resolve("fermento-relaunch-instance.txt");

		Path launcherExe = findLauncherExecutable();
		String instanceId = instanceId();
		boolean launcherRelaunch = launcherExe != null && instanceId != null && !instanceId.isBlank();
		if (launcherRelaunch) {
			writeUtf8Bom(launcherFile, launcherExe.toAbsolutePath().normalize().toString());
			writeUtf8Bom(instanceFile, instanceId);
			FermentoMod.LOGGER.info("Update relaunch via launcher {} --launch {}", launcherExe.getFileName(), instanceId);
		} else {
			Files.deleteIfExists(launcherFile);
			Files.deleteIfExists(instanceFile);
		}

		WindowsLaunch javaLaunch = captureWindowsLaunch();
		if (javaLaunch != null) {
			writeUtf8Bom(exeFile, javaLaunch.executable());
			writeUtf8Bom(argsFile, javaLaunch.arguments());
			writeEnvSnapshot(envFile);
		} else {
			Files.deleteIfExists(exeFile);
			Files.deleteIfExists(argsFile);
			Files.deleteIfExists(envFile);
			if (!launcherRelaunch) {
				FermentoMod.LOGGER.warn("Update will install, but no relaunch command was found.");
			}
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
		ps.append("$envFile = ").append(psQuote(envFile.toAbsolutePath().normalize().toString())).append("\r\n");
		ps.append("$launcherFile = ").append(psQuote(launcherFile.toAbsolutePath().normalize().toString())).append("\r\n");
		ps.append("$instanceFile = ").append(psQuote(instanceFile.toAbsolutePath().normalize().toString())).append("\r\n");
		ps.append("$log = ").append(psQuote(modsDir().resolve("fermento-update.log").toAbsolutePath().normalize().toString())).append("\r\n");
		ps.append("$old = @(\r\n");
		for (Path old : oldJars) {
			ps.append("  ").append(psQuote(old.toAbsolutePath().normalize().toString())).append("\r\n");
		}
		ps.append(")\r\n");
		ps.append("function Write-Log($m) { Add-Content -LiteralPath $log -Value ((Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + ' ' + $m) }\r\n");
		ps.append("function Read-Utf8($path) { (Get-Content -LiteralPath $path -Raw -Encoding UTF8).Trim().Trim([char]0xFEFF) }\r\n");
		ps.append("Write-Log 'waiting for Minecraft to exit'\r\n");
		ps.append("$deadline = (Get-Date).AddSeconds(90)\r\n");
		ps.append("while ((Get-Process -Id $targetPid -ErrorAction SilentlyContinue) -and ((Get-Date) -lt $deadline)) { Start-Sleep -Seconds 1 }\r\n");
		ps.append("Start-Sleep -Seconds 4\r\n");
		ps.append("foreach ($path in $old) {\r\n");
		ps.append("  for ($i = 0; $i -lt 20; $i++) {\r\n");
		ps.append("    try {\r\n");
		ps.append("      if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Force -ErrorAction Stop }\r\n");
		ps.append("      break\r\n");
		ps.append("    } catch { Start-Sleep -Milliseconds 400 }\r\n");
		ps.append("  }\r\n");
		ps.append("}\r\n");
		ps.append("for ($i = 0; $i -lt 20; $i++) {\r\n");
		ps.append("  try { Move-Item -LiteralPath $pending -Destination $dest -Force -ErrorAction Stop; break } catch { Start-Sleep -Milliseconds 400 }\r\n");
		ps.append("}\r\n");
		ps.append("$launched = $false\r\n");
		ps.append("if (Test-Path -LiteralPath $pending) {\r\n");
		ps.append("  Write-Log 'could not replace the mod jar'\r\n");
		ps.append("} else {\r\n");
		ps.append("  Write-Log 'jar replaced'\r\n");
		ps.append("  if ((Test-Path -LiteralPath $launcherFile) -and (Test-Path -LiteralPath $instanceFile)) {\r\n");
		ps.append("    try {\r\n");
		ps.append("      $launcherExe = Read-Utf8 $launcherFile\r\n");
		ps.append("      $inst = Read-Utf8 $instanceFile\r\n");
		ps.append("      $launcherDir = Split-Path -Parent $launcherExe\r\n");
		ps.append("      Write-Log ('starting launcher ' + $launcherExe + ' --launch ' + $inst)\r\n");
		ps.append("      Start-Process -FilePath $launcherExe -ArgumentList @('--launch', $inst) -WorkingDirectory $launcherDir | Out-Null\r\n");
		ps.append("      $launched = $true\r\n");
		ps.append("      Write-Log 'launcher started'\r\n");
		ps.append("    } catch {\r\n");
		ps.append("      Write-Log ('launcher relaunch failed: ' + $_.Exception.Message)\r\n");
		ps.append("    }\r\n");
		ps.append("  }\r\n");
		ps.append("  if (-not $launched -and (Test-Path -LiteralPath $exeFile) -and (Test-Path -LiteralPath $argsFile)) {\r\n");
		ps.append("    try {\r\n");
		ps.append("      $exe = Read-Utf8 $exeFile\r\n");
		ps.append("      $arg = Read-Utf8 $argsFile\r\n");
		ps.append("      Write-Log ('starting java ' + $exe)\r\n");
		ps.append("      $psi = New-Object System.Diagnostics.ProcessStartInfo\r\n");
		ps.append("      $psi.FileName = $exe\r\n");
		ps.append("      $psi.Arguments = $arg\r\n");
		ps.append("      $psi.WorkingDirectory = $gameDir\r\n");
		ps.append("      $psi.UseShellExecute = $false\r\n");
		ps.append("      if (Test-Path -LiteralPath $envFile) {\r\n");
		ps.append("        $raw = Get-Content -LiteralPath $envFile -Raw -Encoding UTF8\r\n");
		ps.append("        $map = $raw | ConvertFrom-Json\r\n");
		ps.append("        $psi.Environment.Clear()\r\n");
		ps.append("        $map.PSObject.Properties | ForEach-Object {\r\n");
		ps.append("          try { $psi.Environment[$_.Name] = [string]$_.Value } catch {}\r\n");
		ps.append("        }\r\n");
		ps.append("      }\r\n");
		ps.append("      [void][System.Diagnostics.Process]::Start($psi)\r\n");
		ps.append("      $launched = $true\r\n");
		ps.append("      Write-Log 'java started'\r\n");
		ps.append("    } catch {\r\n");
		ps.append("      Write-Log ('java relaunch failed: ' + $_.Exception.Message)\r\n");
		ps.append("    }\r\n");
		ps.append("  }\r\n");
		ps.append("  if (-not $launched) { Write-Log 'no relaunch command' }\r\n");
		ps.append("}\r\n");
		ps.append("Remove-Item -LiteralPath $exeFile, $argsFile, $envFile, $launcherFile, $instanceFile -Force -ErrorAction SilentlyContinue\r\n");
		ps.append("Remove-Item -LiteralPath ").append(psQuote(script.toAbsolutePath().normalize().toString()));
		ps.append(", ").append(psQuote(vbs.toAbsolutePath().normalize().toString())).append(" -Force -ErrorAction SilentlyContinue\r\n");
		writeUtf8Bom(script, ps.toString());

		String fileArg = script.toAbsolutePath().normalize().toString();
		String hiddenPs = "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "
				+ quoteWindowsArg(fileArg);
		if (!startWindowsDetached(hiddenPs, modsDir())) {
			FermentoMod.LOGGER.warn("Detached updater start failed; the relaunch script may die with Minecraft.");
			String psPath = fileArg.replace("\"", "\"\"");
			String vbsBody = "Set sh = CreateObject(\"Wscript.Shell\")\r\n"
					+ "sh.Run \"powershell.exe -NoProfile -ExecutionPolicy Bypass -File \"\"" + psPath + "\"\"\", 0, False\r\n";
			Files.writeString(vbs, vbsBody, StandardCharsets.UTF_8);
			new ProcessBuilder("wscript.exe", "//B", "//nologo", vbs.toAbsolutePath().toString())
					.directory(modsDir().toFile())
					.redirectOutput(ProcessBuilder.Redirect.DISCARD)
					.redirectError(ProcessBuilder.Redirect.DISCARD)
					.start();
		}

		return launcherRelaunch || javaLaunch != null;
	}

	private static boolean launchUnix(Path pending, Path dest, List<Path> oldJars) throws IOException {
		Path script = modsDir().resolve("fermento-install.sh");
		String gameDir = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize().toString();
		Path launcherExe = findLauncherExecutable();
		String instanceId = instanceId();
		boolean launcherRelaunch = launcherExe != null && instanceId != null && !instanceId.isBlank();
		String command = captureCommandLine();
		StringBuilder sh = new StringBuilder();
		sh.append("#!/bin/sh\n");
		sh.append("pid=").append(ProcessHandle.current().pid()).append("\n");
		sh.append("i=0\n");
		sh.append("while kill -0 \"$pid\" 2>/dev/null && [ \"$i\" -lt 90 ]; do\n");
		sh.append("  sleep 1\n");
		sh.append("  i=$((i+1))\n");
		sh.append("done\n");
		sh.append("sleep 4\n");
		for (Path old : oldJars) {
			sh.append("rm -f ").append(shQuote(old)).append("\n");
		}
		sh.append("mv -f ").append(shQuote(pending)).append(" ").append(shQuote(dest)).append("\n");
		if (launcherRelaunch) {
			sh.append("nohup ").append(shQuoteString(launcherExe.toAbsolutePath().normalize().toString()));
			sh.append(" --launch ").append(shQuoteString(instanceId)).append(" >/dev/null 2>&1 &\n");
			FermentoMod.LOGGER.info("Update relaunch via launcher {} --launch {}", launcherExe.getFileName(), instanceId);
		} else if (command != null && !command.isBlank()) {
			sh.append("cd ").append(shQuoteString(gameDir)).append("\n");
			sh.append("nohup ").append(command).append(" >/dev/null 2>&1 &\n");
		} else {
			FermentoMod.LOGGER.warn("Update will install, but the game command line could not be read for relaunch.");
		}
		sh.append("rm -f ").append(shQuote(script)).append("\n");
		Files.writeString(script, sh.toString(), StandardCharsets.UTF_8);
		script.toFile().setExecutable(true);
		new ProcessBuilder("/bin/sh", "-c",
				"if command -v setsid >/dev/null 2>&1; then setsid " + shQuote(script)
						+ " >/dev/null 2>&1 & else nohup " + shQuote(script) + " >/dev/null 2>&1 & fi")
				.directory(modsDir().toFile())
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.redirectError(ProcessBuilder.Redirect.DISCARD)
				.start();
		return launcherRelaunch || (command != null && !command.isBlank());
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

	private static WindowsLaunch captureWindowsLaunch() {
		WindowsLaunch fromWmi = captureWindowsLaunchFromWmi();
		if (fromWmi != null) {
			return fromWmi;
		}
		String executable = ProcessHandle.current().info().command().orElse("");
		WindowsLaunch split = splitWindowsLaunch(captureCommandLine());
		if (split != null) {
			if ((executable == null || executable.isBlank()) || executable.equalsIgnoreCase(split.executable())) {
				return split;
			}
			return new WindowsLaunch(executable, split.arguments());
		}
		return null;
	}

	private static WindowsLaunch captureWindowsLaunchFromWmi() {
		Path exeOut = modsDir().resolve("fermento-wmi-exe.txt");
		Path argsOut = modsDir().resolve("fermento-wmi-args.txt");
		try {
			Files.createDirectories(modsDir());
			Files.deleteIfExists(exeOut);
			Files.deleteIfExists(argsOut);
			ProcessBuilder builder = new ProcessBuilder(
					"powershell.exe",
					"-NoProfile",
					"-NonInteractive",
					"-Command",
					"$p = Get-CimInstance Win32_Process -Filter ('ProcessId=' + $env:FERMENTO_PID); "
							+ "Set-Content -LiteralPath $env:FERMENTO_EXE -Value $p.ExecutablePath -Encoding utf8; "
							+ "$cmd = [string]$p.CommandLine; $exe = [string]$p.ExecutablePath; $args = $cmd; "
							+ "if ($cmd.StartsWith([char]34)) { $i = $cmd.IndexOf([char]34, 1); "
							+ "if ($i -gt 0) { $args = $cmd.Substring($i + 1).Trim() } } "
							+ "elseif ($exe -and $cmd.StartsWith($exe)) { $args = $cmd.Substring($exe.Length).Trim() } "
							+ "Set-Content -LiteralPath $env:FERMENTO_ARGS -Value $args -Encoding utf8"
			);
			builder.environment().put("FERMENTO_PID", Long.toString(ProcessHandle.current().pid()));
			builder.environment().put("FERMENTO_EXE", exeOut.toAbsolutePath().toString());
			builder.environment().put("FERMENTO_ARGS", argsOut.toAbsolutePath().toString());
			builder.redirectErrorStream(true);
			Process process = builder.start();
			process.getInputStream().readAllBytes();
			if (!process.waitFor(8, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				return null;
			}
			String exe = readUtf8(exeOut);
			String args = readUtf8(argsOut);
			if (exe == null || exe.isBlank()) {
				exe = ProcessHandle.current().info().command().orElse("");
			}
			if (exe == null || exe.isBlank() || args == null || args.length() < 20 || args.length() > 32_000) {
				return null;
			}
			return new WindowsLaunch(exe, args);
		} catch (Exception e) {
			FermentoMod.LOGGER.warn("Launch command unreadable: {}", e.toString());
			return null;
		} finally {
			try {
				Files.deleteIfExists(exeOut);
				Files.deleteIfExists(argsOut);
			} catch (IOException ignored) {
			}
		}
	}

	private static String readUtf8(Path path) throws IOException {
		if (!Files.isRegularFile(path)) {
			return null;
		}
		return Files.readString(path, StandardCharsets.UTF_8).replace("\uFEFF", "").trim();
	}

	private static boolean startWindowsDetached(String commandLine, Path workDir) {
		if (wmiCreateProcess(commandLine, workDir)) {
			FermentoMod.LOGGER.info("Updater started via WMI (outside the launcher job).");
			return true;
		}
		try {
			String script = modsDir().resolve("fermento-install.ps1").toAbsolutePath().normalize().toString();
			Process process = new ProcessBuilder(
					"cmd.exe",
					"/c",
					"start \"FermentoUpdate\" /min powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "
							+ quoteWindowsArg(script))
					.directory(workDir.toFile())
					.redirectOutput(ProcessBuilder.Redirect.DISCARD)
					.redirectError(ProcessBuilder.Redirect.DISCARD)
					.start();
			process.waitFor(3, TimeUnit.SECONDS);
			FermentoMod.LOGGER.info("Updater started via cmd start.");
			return true;
		} catch (Exception e) {
			FermentoMod.LOGGER.warn("cmd start updater failed: {}", e.toString());
			return false;
		}
	}

	private static boolean wmiCreateProcess(String commandLine, Path workDir) {
		try {
			String ps = "$r = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = "
					+ psQuote(commandLine) + "; CurrentDirectory = " + psQuote(workDir.toAbsolutePath().normalize().toString())
					+ " }; if ($null -eq $r) { exit 1 }; exit [int]$r.ReturnValue";
			Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", ps)
					.redirectErrorStream(true)
					.start();
			process.getInputStream().readAllBytes();
			if (!process.waitFor(8, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				return false;
			}
			if (process.exitValue() == 0) {
				return true;
			}
			String alt = "$r = ([wmiclass]'Win32_Process').Create("
					+ psQuote(commandLine) + ", "
					+ psQuote(workDir.toAbsolutePath().normalize().toString())
					+ "); if ($null -eq $r) { exit 1 }; exit [int]$r.ReturnValue";
			Process fallback = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", alt)
					.redirectErrorStream(true)
					.start();
			fallback.getInputStream().readAllBytes();
			if (!fallback.waitFor(8, TimeUnit.SECONDS)) {
				fallback.destroyForcibly();
				return false;
			}
			return fallback.exitValue() == 0;
		} catch (Exception e) {
			FermentoMod.LOGGER.warn("WMI updater start failed: {}", e.toString());
			return false;
		}
	}

	private static Path findLauncherExecutable() {
		ProcessHandle handle = ProcessHandle.current();
		for (int i = 0; i < 10; i++) {
			Optional<ProcessHandle> parent = handle.parent();
			if (parent.isEmpty()) {
				break;
			}
			handle = parent.get();
			String command = handle.info().command().orElse("");
			if (command.isBlank()) {
				continue;
			}
			Path path = Path.of(command);
			if (isKnownLauncher(path.getFileName().toString()) && Files.isRegularFile(path)) {
				return path.toAbsolutePath().normalize();
			}
		}

		String instDir = env("INST_DIR");
		if (instDir != null) {
			Path instance = Path.of(instDir).toAbsolutePath().normalize();
			Path instances = instance.getParent();
			Path root = instances == null ? null : instances.getParent();
			Path found = firstExisting(root, launcherFileNames());
			if (found != null) {
				return found;
			}
		}

		return firstExistingPath(
				pathFromEnv("LOCALAPPDATA", "Programs", "PrismLauncher", "prismlauncher.exe"),
				pathFromEnv("ProgramFiles", "PrismLauncher", "prismlauncher.exe"),
				pathFromEnv("ProgramFiles(x86)", "PrismLauncher", "prismlauncher.exe"),
				pathFromEnv("LOCALAPPDATA", "Programs", "PolyMC", "polymc.exe"),
				pathFromEnv("ProgramFiles", "PolyMC", "polymc.exe"),
				pathFromEnv("ProgramFiles", "MultiMC", "MultiMC.exe"),
				pathFromEnv("LOCALAPPDATA", "Programs", "MultiMC", "MultiMC.exe"));
	}

	private static String instanceId() {
		return firstNonBlank(
				env("INST_ID"),
				prop("org.prismlauncher.instance.id"),
				env("INST_NAME"),
				prop("org.prismlauncher.instance.name"),
				prop("multimc.instance.title"));
	}

	private static boolean isKnownLauncher(String fileName) {
		String name = fileName.toLowerCase(Locale.ROOT);
		return name.contains("prismlauncher")
				|| name.equals("polymc.exe")
				|| name.equals("polymc")
				|| name.equals("multimc.exe")
				|| name.equals("multimc")
				|| name.equals("multimc5.exe");
	}

	private static List<String> launcherFileNames() {
		if (isWindows()) {
			return List.of("prismlauncher.exe", "PolyMC.exe", "MultiMC.exe", "MultiMC5.exe");
		}
		return List.of("prismlauncher", "polymc", "multimc");
	}

	private static Path firstExisting(Path directory, List<String> names) {
		if (directory == null) {
			return null;
		}
		for (String name : names) {
			Path candidate = directory.resolve(name);
			if (Files.isRegularFile(candidate)) {
				return candidate.toAbsolutePath().normalize();
			}
		}
		return null;
	}

	private static Path firstExistingPath(Path... paths) {
		for (Path path : paths) {
			if (path != null && Files.isRegularFile(path)) {
				return path.toAbsolutePath().normalize();
			}
		}
		return null;
	}

	private static Path pathFromEnv(String envName, String... parts) {
		String root = env(envName);
		if (root == null) {
			return null;
		}
		Path path = Path.of(root);
		for (String part : parts) {
			path = path.resolve(part);
		}
		return path;
	}

	private static void writeEnvSnapshot(Path path) throws IOException {
		JsonObject json = new JsonObject();
		for (var entry : System.getenv().entrySet()) {
			String key = entry.getKey();
			if (key == null || looksSecret(key)) {
				continue;
			}
			json.addProperty(key, entry.getValue() == null ? "" : entry.getValue());
		}
		writeUtf8Bom(path, new Gson().toJson(json));
	}

	private static boolean looksSecret(String key) {
		String name = key.toUpperCase(Locale.ROOT);
		return name.contains("TOKEN")
				|| name.contains("SECRET")
				|| name.contains("PASSWORD")
				|| name.contains("PASSWD")
				|| name.contains("AUTHORIZATION")
				|| name.contains("API_KEY")
				|| name.contains("APIKEY");
	}

	private static String firstNonBlank(String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return null;
	}

	private static String env(String key) {
		try {
			String value = System.getenv(key);
			return value == null || value.isBlank() ? null : value;
		} catch (SecurityException e) {
			return null;
		}
	}

	private static String prop(String key) {
		try {
			String value = System.getProperty(key);
			return value == null || value.isBlank() ? null : value;
		} catch (SecurityException e) {
			return null;
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
