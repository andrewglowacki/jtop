package jtop;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

public class TopPrinter extends Printer {

    private static final String[] LABELS = new String[] { "ms", "s", "m", "h", "d" };
    private static final double[] AMOUNTS = new double[] { 1000.0, 60.0, 60.0, 24.0 };
    private final StacksPrinter stacks;

    public TopPrinter(Config config, StacksPrinter stacks) throws IOException {
        super(config);
        this.stacks = stacks;
    }

    @Override
    protected Map<Character, PrinterCommandHandler> createHandlers() {
        Map<Character, PrinterCommandHandler> handlers = super.createHandlers();
        handlers.put('c', this::setCpuTimeCutoff);
        handlers.put('l', this::setTopLimit);
        return handlers;
    }

    private void setCpuTimeCutoff(TerminalLineReader reader) throws IOException {
        config.parseCpuTimeCutoff(reader.readLine("Enter cpu time cutoff (ms): "));
        System.out.print("\rcpu time cutoff is now: " + config.getCpuTimeCutoff() + " ms");
    }

    private void setTopLimit(TerminalLineReader reader) throws IOException {
        config.parseTopLimit(reader.readLine("Enter top thread limit: "));
        System.out.print("\rtop thread limit is now: " + config.getTopLimit());
    }

    @Override
    protected String getCommandsString() {
        String commands = "Cmds: (i)nterval | (l)imit | (c)pu cutoff | (n)ame filter:";
        commands += config.hasNameRegex() ? "+" : "-";
        commands += " | (f)rame filter:";
        commands += config.hasFrameFilter() ? "+" : "-";
        commands += " | (s)wap mode : ";
        return commands;
    }

    private void setWaiting() {
        List<String> output = new ArrayList<>();
        output.add("Waiting for first sample interval after " + config.getInterval() + " ms...");
        this.output = output;
    }

    @Override
    protected void runLoop() throws IOException, InterruptedException {
        Map<String, ThreadInfo> last = idToInfo(Executor.sampleJvm(config.getPid()));
        stacks.addSample(last.values());
        setWaiting();
        while (true) {
            Thread.sleep(config.getInterval());

            NavigableSet<Stat> stats = new TreeSet<>();

            Map<String, ThreadInfo> current = idToInfo(Executor.sampleJvm(config.getPid()));
            if (active) {
                stacks.addSample(current.values());
            }
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
                if (stat.getCurrentTime() < config.getCpuTimeCutoff()) {
                    continue;
                }

                stats.add(stat);
                if (stats.size() > config.getTopLimit()) {
                    stats.pollFirst();
                }
            }

            stats = stats.descendingSet();

            int curTimeLen = 4;
            int totTimeLen = 5;
            for (Stat stat : stats) {
                int thisCurTimeLen = labelTime(stat.getCurrentTime()).length();
                int thisTotTimeLen = labelTime(stat.getTotalTime()).length();
                
                if (thisCurTimeLen > curTimeLen) {
                    curTimeLen = thisCurTimeLen;
                }
                if (thisTotTimeLen > totTimeLen) {
                    totTimeLen = thisTotTimeLen;
                }
            }

            curTimeLen += 2;
            totTimeLen += 2;

            StringBuilder builder = new StringBuilder();
            pad(builder, "LAST", curTimeLen);
            pad(builder, "TOTAL", totTimeLen);
            builder.append("THREAD NAME - PID: " + config.getPid());

            List<String> output = new ArrayList<>(stats.size() + 1);
            output.add(builder.toString());

            if (stats.size() == 0) {
                output.add("<no active threads>");
            }

            for (Stat stat : stats) {
                builder.setLength(0);

                String timeStr = labelTime(stat.getCurrentTime());
                pad(builder, timeStr, curTimeLen);

                timeStr = labelTime(stat.getTotalTime());
                pad(builder, timeStr, totTimeLen);

                builder.append(stat.getName());
                output.add(builder.toString());
            }

            this.output = output;

            last = current;
        }
    }

    private String labelTime(double time) {
        int label = 0;
        for (int i = 0; i < AMOUNTS.length && time > AMOUNTS[i]; i++) {
            time /= AMOUNTS[i];
            label++;
        }
        return format.format(time) + "" + LABELS[label];
    }

    private void pad(StringBuilder builder, String str, int to) {
        builder.append(str);
        for (int i = str.length(); i < to; i++) {
            builder.append(' ');
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
        private final double currentTime;
        private final double totalTime;
        
        public Stat(ThreadInfo current) {
            id = current.getId();
            name = current.getName();
            currentTime = current.getCpuTime();
            totalTime = currentTime;
        }

        public Stat(ThreadInfo last, ThreadInfo current) {
            this.id = last.getId();
            this.name = current.getName();
            this.currentTime = current.getCpuTime() - last.getCpuTime();
            this.totalTime = current.getCpuTime();
        }

        public String getName() {
            return name;
        }
        public double getCurrentTime() {
            return currentTime;
        }
        public double getTotalTime() {
            return totalTime;
        }

        @Override
        public int compareTo(Stat o) {
            int diff = Double.compare(currentTime, o.currentTime);
            if (diff != 0) {
                return diff;
            }
            diff = Double.compare(totalTime, o.totalTime);
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