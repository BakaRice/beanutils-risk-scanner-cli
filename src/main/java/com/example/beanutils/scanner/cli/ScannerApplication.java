package com.example.beanutils.scanner.cli;

import picocli.CommandLine;

public final class ScannerApplication {
    private ScannerApplication() {
    }

    public static void main(String[] args) {
        CommandLine commandLine = new CommandLine(new ScanCommand());
        commandLine.setExecutionExceptionHandler((exception, command, parseResult) -> {
            command.getErr().println("扫描失败：" + exception.getMessage());
            return CommandLine.ExitCode.SOFTWARE;
        });
        int exitCode = commandLine.execute(args);
        System.exit(exitCode);
    }
}
