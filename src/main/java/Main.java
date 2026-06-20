import java.util.Scanner;
import java.io.File;
import java.io.PrintStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;

        while (true) {
            System.out.print("$ ");

            if (!scanner.hasNextLine()) {
                break;
            }

            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                continue;
            }

            List<String> inputPartsList = parseArguments(input);
            if (inputPartsList.isEmpty()) {
                continue;
            }

            String stdoutFile = null;
            String stderrFile = null;
            int redirectIndex = -1;

            for (int i = 0; i < inputPartsList.size(); i++) {
                String arg = inputPartsList.get(i);
                if (arg.equals(">") || arg.equals("1>")) {
                    if (i + 1 < inputPartsList.size()) {
                        stdoutFile = inputPartsList.get(i + 1);
                        redirectIndex = i;
                        break;
                    }
                } else if (arg.equals("2>")) {
                    if (i + 1 < inputPartsList.size()) {
                        stderrFile = inputPartsList.get(i + 1);
                        redirectIndex = i;
                        break;
                    }
                }
            }

            String[] inputParts;
            if (redirectIndex != -1) {
                List<String> cleanArgs = new ArrayList<>(inputPartsList.subList(0, redirectIndex));
                inputParts = cleanArgs.toArray(new String[0]);
            } else {
                inputParts = inputPartsList.toArray(new String[0]);
            }

            if (inputParts.length == 0) {
                continue;
            }

            if (stdoutFile != null) {
                File file = new File(stdoutFile);
                if (file.getParentFile() != null && !file.getParentFile().exists()) {
                    file.getParentFile().mkdirs();
                }
                PrintStream fileOut = new PrintStream(new FileOutputStream(file, false));
                System.setOut(fileOut);
            }

            if (stderrFile != null) {
                File file = new File(stderrFile);
                if (file.getParentFile() != null && !file.getParentFile().exists()) {
                    file.getParentFile().mkdirs();
                }
                PrintStream fileErr = new PrintStream(new FileOutputStream(file, false));
                System.setErr(fileErr);
            }

            String command = inputParts[0];

            if (command.equals("exit")) {
                if (stdoutFile != null) {
                    System.out.close();
                    System.setOut(originalOut);
                }
                if (stderrFile != null) {
                    System.err.close();
                    System.setErr(originalErr);
                }
                break;
            } else if (command.equals("echo")) {
                StringBuilder echoOutput = new StringBuilder();
                for (int i = 1; i < inputParts.length; i++) {
                    echoOutput.append(inputParts[i]);
                    if (i < inputParts.length - 1) {
                        echoOutput.append(" ");
                    }
                }
                System.out.println(echoOutput.toString());
            } else if (command.equals("pwd")) {
                System.out.println(System.getProperty("user.dir"));
            } else if (command.equals("cd")) {
                if (inputParts.length >= 2) {
                    String path = inputParts[1];
                    File currentDir = new File(System.getProperty("user.dir"));
                    File targetDir;

                    if (path.equals("~")) {
                        targetDir = new File(System.getenv("HOME"));
                    } else if (path.startsWith("~/")) {
                        targetDir = new File(System.getenv("HOME"), path.substring(2));
                    } else {
                        targetDir = new File(path);
                        if (!targetDir.isAbsolute()) {
                            targetDir = new File(currentDir, path);
                        }
                    }

                    if (targetDir.exists() && targetDir.isDirectory()) {
                        System.setProperty("user.dir", targetDir.getCanonicalPath());
                    } else {
                        System.err.println("cd: " + path + ": No such file or directory");
                    }
                }
            } else if (command.equals("type")) {
                if (inputParts.length >= 2) {
                    String targetCommand = inputParts[1];

                    if (targetCommand.equals("echo")
                            || targetCommand.equals("exit")
                            || targetCommand.equals("type")
                            || targetCommand.equals("pwd")
                            || targetCommand.equals("cd")) {
                        System.out.println(targetCommand + " is a shell builtin");
                    } else {
                        String pathEnv = System.getenv("PATH");
                        String[] directories = pathEnv.split(java.util.regex.Pattern.quote(File.pathSeparator));
                        boolean found = false;

                        for (String directory : directories) {
                            File executableFile = new File(directory, targetCommand);
                            if (executableFile.exists() && executableFile.canExecute()) {
                                System.out.println(targetCommand + " is " + executableFile.getAbsolutePath());
                                found = true;
                                break;
                            }
                        }

                        if (!found) {
                            System.out.println(targetCommand + ": not found");
                        }
                    }
                }
            } else {
                String pathEnv = System.getenv("PATH");
                String[] directories = pathEnv.split(java.util.regex.Pattern.quote(File.pathSeparator));
                boolean found = false;

                for (String directory : directories) {
                    File executableFile = new File(directory, command);
                    if (executableFile.exists() && executableFile.canExecute()) {
                        found = true;
                        break;
                    }
                }

                if (found) {
                    if (stdoutFile != null) {
                        System.out.close();
                        System.setOut(originalOut);
                    }
                    if (stderrFile != null) {
                        System.err.close();
                        System.setErr(originalErr);
                    }

                    ProcessBuilder pb = new ProcessBuilder(inputParts)
                            .directory(new File(System.getProperty("user.dir")));

                    if (stdoutFile != null) {
                        pb.redirectOutput(ProcessBuilder.Redirect.to(new File(stdoutFile)));
                    } else {
                        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    }

                    if (stderrFile != null) {
                        pb.redirectError(ProcessBuilder.Redirect.to(new File(stderrFile)));
                    } else {
                        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    }

                    Process process = pb.start();
                    process.waitFor();
                } else {
                    System.err.println(command + ": command not found");
                }
            }

            if (stdoutFile != null && System.out != originalOut) {
                System.out.close();
                System.setOut(originalOut);
            }
            if (stderrFile != null && System.err != originalErr) {
                System.err.close();
                System.setErr(originalErr);
            }
        }

        scanner.close();
    }

    private static List<String> parseArguments(String input) {
        List<String> args = new ArrayList<>();
        StringBuilder currentArg = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean hasContent = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '\\' && !inSingleQuotes) {
                if (inDoubleQuotes) {
                    if (i + 1 < input.length()) {
                        char nextChar = input.charAt(i + 1);
                        if (nextChar == '"' || nextChar == '\\' || nextChar == '$' || nextChar == '`') {
                            currentArg.append(nextChar);
                            i++;
                        } else {
                            currentArg.append(c);
                        }
                        hasContent = true;
                    } else {
                        currentArg.append(c);
                        hasContent = true;
                    }
                } else {
                    if (i + 1 < input.length()) {
                        currentArg.append(input.charAt(++i));
                        hasContent = true;
                    }
                }
            } else if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
                hasContent = true;
            } else if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
                hasContent = true;
            } else if (c == ' ' && !inSingleQuotes && !inDoubleQuotes) {
                if (hasContent || currentArg.length() > 0) {
                    args.add(currentArg.toString());
                    currentArg.setLength(0);
                    hasContent = false;
                }
            } else {
                currentArg.append(c);
                hasContent = true;
            }
        }

        if (hasContent || currentArg.length() > 0) {
            args.add(currentArg.toString());
        }

        return args;
    }
}