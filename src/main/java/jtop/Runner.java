package jtop;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

public class Runner {
    
    public static final Options options = new Options()
        .addOption(Option.builder("i")
            .longOpt("interval")
            .hasArg(true)
            .desc("Interval with which to sample the JVM.")
            .build())
        .addOption(Option.builder("j")
            .longOpt("jvm")
            .hasArg(true)
            .desc("Pid of the JVM to track. If one isn't specified, you will be prompted for one")
            .build())
        .addOption(Option.builder("f")
            .longOpt("frame")
            .hasArg(true)
            .desc("Filters threads by the presence of this stack frame string. Put a '~' at the beginning to negate.")
            .build())
        .addOption(Option.builder("n")
            .longOpt("name")
            .hasArg(true)
            .desc("Filters threads by this name regex. Put a '~' at the beginning to negate.")
            .build())
        .addOption(Option.builder("c")
            .longOpt("cpu")
            .optionalArg(true)
            .desc("Display cpu usage by thread. An optional usage per second can also be passed. This is mutually exclusive with -s (--stacks).")
            .build())
        .addOption(Option.builder("s")
            .longOpt("stacks")
            .hasArg(false)
            .desc("Display stack sample occurences. This is mutually exclusive with -c (--cpu).")
            .build())
        .addOption(Option.builder("l")
            .longOpt("limit")
            .hasArg(true)
            .desc("Limits the top X threads to display at once (default: 20), OR the top X % of frame occurrences (default: 5).")
            .build())
        .addOption(Option.builder("h")
            .longOpt("help")
            .hasArg(false)
            .desc("Displays the usage statement/help message")
            .build());
            
    public static void main(String[] args) throws IOException, ParseException {
        String pid = null;

        if (args.length > 0 && args[0].matches("[0-9]+")) {
            pid = args[0];
            String[] copy = new String[args.length - 1];
            System.arraycopy(args, 1, copy, 0, copy.length);
            args = copy;
        }

        CommandLine commandLine = new DefaultParser().parse(options, args);
        if (commandLine.hasOption("h")) {
            printHelp();
            return;
        } else if (commandLine.hasOption("c") && commandLine.hasOption("s")) {
            printHelp();
            System.out.println();
            System.out.println("ERROR: only one of -c (--cpu) and -s (--stacks) can be specified.");
            return;
        }

        pid = commandLine.getOptionValue("j", pid);
        if (pid == null) {
            pid = selectJvm();
            if (pid == null) {
                return;
            }
        }

        int interval = Integer.parseInt(commandLine.getOptionValue("i", "5")) * 1000;

        Config config = new Config();
        config.setPid(pid);
        config.setInterval(interval);
        config.parseFrameFilter(commandLine.getOptionValue("f", ""));
        config.parseNameRegex(commandLine.getOptionValue("n", ""));
        
        try (Terminal terminal = TerminalBuilder
            .builder()
            .dumb(false)
            .jna(true)
            .build()) {

            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Printer current;
                Printer other;
                if (commandLine.hasOption("s")) {
                    config.parseStacksLimitPercent(commandLine.getOptionValue("l", "5"));
                    current = new StacksPrinter(config);
                    other = new TopPrinter(config, (StacksPrinter)current);
                } else {
                    config.parseTopLimit(commandLine.getOptionValue("l", "20"));
                    config.parseCpuTimeCutoff(commandLine.getOptionValue("c", "1"));

                    other = new StacksPrinter(config);
                    current = new TopPrinter(config, (StacksPrinter)other);
                }

                current.setActive(true);
                other.setActive(false);

                config.setActive(current, other);

                executor.submit(current);
                executor.submit(other);

                DisplayLoop.run(config, terminal);

            } finally {
                executor.shutdownNow();
            }
        }
    }

    private static void printHelp() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("jtop (pid) (args)", options );
    }

    private static String selectJvm() throws IOException {
        List<String> jvms = Executor.execute("ps", "-fC", "java", "--no-headers");

        List<String> pids = jvms.stream()
            .map(line -> line.split("\\s+")[1])
            .collect(Collectors.toList());

        if (pids.size() == 0) {
            System.out.println("No JVMs detected.");
            return null;
        } else if (pids.size() == 1) {
            return pids.get(0);
        }

        for (int i = 0; i < jvms.size(); i++) {
            System.out.println("" + (i + 1) + " -- " + jvms.get(i));
        }

        while (true) {
            System.out.print("Type number corresponding to the JVM to top (1-" + pids.size() + "): ");
            BufferedReader inReader = new BufferedReader(new InputStreamReader(System.in));
            String input = inReader.readLine();
            if (input == null) {
                return null;
            }

            try {
                int index = Integer.parseInt(input) - 1;
                if (index < pids.size() && index >= 0) {
                    return pids.get(index);
                }
            } catch (Throwable ex) { }
        }
    }
}
