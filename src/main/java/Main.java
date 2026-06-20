import java.util.Scanner;
import java.io.File;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");

            if (!scanner.hasNextLine()) {
                break;
            }

            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                continue;
            }

            String[] inputParts = input.split(" ");
            String command = inputParts[0];

            if (command.equals("exit")) {
                break;
            } else if (command.equals("echo")) {
                System.out.println(input.substring(5));
            } else if (command.equals("pwd")) {
                System.out.println(System.getProperty("user.dir"));
            } else if (command.equals("type")) {
                String targetCommand = input.substring(5);

                if (targetCommand.equals("echo")
                        || targetCommand.equals("exit")
                        || targetCommand.equals("type")
                        || targetCommand.equals("pwd")) {
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
                    Process process = new ProcessBuilder(inputParts).inheritIO().start();
                    process.waitFor();
                } else {
                    System.out.println(input + ": command not found");
                }
            }
        }

        scanner.close();
    }
}