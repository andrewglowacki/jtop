package jtop;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

import org.jline.terminal.Terminal;

public class TopPrinter extends Printer {

    private final StacksPrinter stacks;

    public TopPrinter(Terminal terminal, Config config, StacksPrinter stacks) throws IOException {
        super(terminal, config);
        this.stacks = stacks;
    }

    @Override
    public boolean handleCommand() throws IOException {
        Character command = handleGeneralCommand();
        if (command == null) {
            return true;
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        switch (command) {
            case 'c':
                System.out.print("Enter cpu time cutoff (ms): ");
                config.parseCpuTimeCutoff(reader.readLine());
                return true;
            case 'l':
                System.out.print("Enter top thread limit: ");
                config.parseTopLimit(reader.readLine());
                return true;
            case 's':
                System.out.println("Exiting top mode, entering stacks mode");
                return false;
            default:
                return true;
        }
    }

    @Override
    public boolean run() throws IOException {
        
        Map<String, ThreadInfo> last = idToInfo(Executor.sampleJvm(config.getPid()));
        stacks.addSample(last.values());
        System.out.println("Waiting for first sample interval after " + config.getInterval() + " ms...");
        for (int i = 2; i < terminal.getHeight(); i++) {
            System.out.println();
        }

        while (true) {
            
            if (waitForInterval(terminal.getHeight())) {
                return true;
            }

            NavigableSet<Stat> stats = new TreeSet<>();

            double max = 0;
            Map<String, ThreadInfo> current = idToInfo(Executor.sampleJvm(config.getPid()));
            stacks.addSample(current.values());
            for (ThreadInfo currentInfo : current.values()) {
                if (!config.keepThread(currentInfo)) {
                    continue;
                }

                ThreadInfo lastInfo = last.get(currentInfo.getId());
                Stat stat;
                if (lastInfo == null) {
                    stat = new Stat(currentInfo);
                } else {
                    stat = new Stat(lastInfo, currentInfo);
                }
                if (stat.getTime() < config.getCpuTimeCutoff()) {
                    continue;
                }

                stats.add(stat);
                if (stats.size() > config.getTopLimit()) {
                    stats.pollFirst();
                }
                if (stat.getTime() > max) {
                    max = stat.getTime();
                }
            }

            stats = stats.descendingSet();
            int timeLen = format.format(max).length() + 2;

            for (Stat stat : stats) {
                String timeStr = format.format(stat.getTime());
                System.out.print(timeStr);
                for (int i = timeStr.length(); i < timeLen; i++) {
                    System.out.print(' ');
                }
                System.out.println(stat.getName());
            }

            last = current;

            for (int i = stats.size() + 2; i < terminal.getHeight(); i++) {
                System.out.println();
            }

            String commands = "Commands: (i) set interval  |  (l) set limit  | (c) set cpu cutoff  |  (n) filter by name ";
            commands += config.hasNameRegex() ? "[+]" : "[-]";
            commands += "  |  (f) filter by frame ";
            commands += config.hasFrameFilter() ? "[+]" : "[-]";
            commands += "  | (s) stacks mode : ";
            System.out.print(commands);
        }
    }

    private Map<String, ThreadInfo> idToInfo(List<ThreadInfo> threadInfo) {
        Map<String, ThreadInfo> map = new HashMap<>();
        threadInfo.forEach(info -> map.put(info.getId(), info));
        return map;
    }

    public static class Stat implements Comparable<Stat> { 
        private final String id;
        private final String name;
        private final double time;

        
        public Stat(ThreadInfo current) {
            id = current.getId();
            name = current.getName();
            time = current.getCpuTime();
        }

        public Stat(ThreadInfo last, ThreadInfo current) {
            this.id = last.getId();
            this.name = current.getName();
            this.time = current.getCpuTime() - last.getCpuTime();
        }

        public String getName() {
            return name;
        }
        public double getTime() {
            return time;
        }

        @Override
        public int compareTo(Stat o) {
            int diff = Double.compare(time, o.time);
            if (diff != 0) {
                return diff;
            }
            diff = name.compareTo(o.name);
            if (diff != 0) {
                return diff;
            }
            return id.compareTo(o.id);
        }

    }
}