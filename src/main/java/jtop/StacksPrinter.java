package jtop;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

public class StacksPrinter extends Printer {

    private volatile int samples = 0;
    private volatile Node root;

    public StacksPrinter(Config config) throws IOException {
        super(config);
        newRoot();
    }

    private void newRoot() {
        samples = 0;
        root = new Node("root - PID: " + config.getPid());
    }

    @Override
    protected Map<Character, PrinterCommandHandler> createHandlers() {
        Map<Character, PrinterCommandHandler> handlers = super.createHandlers();
        handlers.put('l', this::setLimitPercentage);
        handlers.put('r', this::resetStats);
        return handlers;
    }

    private void setLimitPercentage(TerminalLineReader reader) throws IOException {
        config.parseStacksLimitPercent(reader.readLine("Enter stack occurrence percent threshold (%): "));
        System.out.println("\rStack occurrence threshold is now: " + Math.round(config.getStacksLimitPercent() * 100.0));
    }

    private synchronized void resetStats(TerminalLineReader reader) throws IOException {
        newRoot();
        System.out.println("\rStack stats successfully reset.");
    }

    @Override
    protected String getCommandsString() {
        String commands = "Cmds: (i)nterval | (l)imit | (r)eset | (n)ame filter:";
        commands += config.hasNameRegex() ? "+" : "-";
        commands += " | (f)rame filter:";
        commands += config.hasFrameFilter() ? "+" : "-";
        commands += " | (s)wap mode : ";
        return commands;
    }

    @Override
    protected void runLoop() throws IOException, InterruptedException {
        synchronized (this) {
            if (active) {
                addSample(Executor.sampleJvm(config.getPid()));
            }

            List<String> output = new ArrayList<>();
            if (samples > 0) {
                root.print("", new StringBuilder(), output);
            } else {
                output.add("root - PID: " + config.getPid() + " - no results");
            }
            this.output = output;
        }

        Thread.sleep(config.getInterval());
    }

    public synchronized void addSample(Collection<ThreadInfo> threads) {
        for (ThreadInfo thread : threads) {
            if (!config.matchesNameRegex(thread)) {
                continue;
            }
            int frameIndex = config.findMatchingFrame(thread);
            if (frameIndex < 0) {
                continue;
            }

            samples++;
            root.add(thread.getFrames(), frameIndex);
        }
    }

    public class Node implements Comparable<Node> {
        private final String frame;
        private final Map<String, Node> children = new HashMap<>();
        private NavigableSet<Node> sorted = new TreeSet<>();
        private int count;

        public Node(String frame) {
            this.frame = frame;
        }

        public int getCount() {
            return count;
        }

        public void print(String indent, StringBuilder builder, List<String> output) {
            double rate = (double)count / (double)samples;

            builder.setLength(0);
            builder.append(indent);
            builder.append(frame);
            builder.append(" - ");
            builder.append(count);
            builder.append(" (");
            builder.append(format.format(rate * 100));
            builder.append(" %)");
            output.add(builder.toString());

            if (rate > config.getStacksLimitPercent()) {
                for (Node child : sorted) {
                    child.print(indent + "|  ", builder, output);
                }
            }
        }
        
        public void add(List<String> frames, int index) {
            count++;
            if (index < 0) {
                return;
            }

            String frame = frames.get(index);

            Node node = children.get(frame);
            if (node == null) {
                node = new Node(frame);
                children.put(frame, node);
            } else {
                sorted.remove(node);
            }

            node.add(frames, index - 1);
            sorted.add(node);
        }

        @Override
        public int compareTo(Node o) {
            int diff = o.count - count;
            if (diff != 0) {
                return diff;
            }

            diff = children.size() - o.children.size();
            if(diff != 0) {
                return diff;
            }

            return frame.compareTo(o.frame);
        }

    }
}