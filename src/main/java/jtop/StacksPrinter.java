package jtop;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

import org.jline.terminal.Terminal;

public class StacksPrinter extends Printer {

    private int samples = 0;
    private Node root = new Node("root");

    public StacksPrinter(Terminal terminal, Config config) throws IOException {
        super(terminal, config);
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
                System.out.println("Exiting stacks mode, entering top mode");
                return false;
            case 'l':
                System.out.print("Enter stack occurrence percent threshold (%): ");
                config.parseStacksLimitPercent(reader.readLine());
                return true;
            case 'r':
                System.out.println("Stats reset.");
                samples = 0;
                root = new Node("root");
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean run() throws IOException {
        int lastSize = 2;
        while (true) {
            addSample(Executor.sampleJvm(config.getPid()));

            int size = root.getTotalFrames() + 1;
            if (size < lastSize) {
                System.out.println();
            }
            lastSize = size;
            System.out.println("current: " + size + " last: " + lastSize);
            root.print("");

            System.out.println();
            
            String commands = "Commands: (i) set interval  |  (l) set stacks percent limit  | (r) reset stats  |  (n) filter by name ";
            commands += config.hasNameRegex() ? "[+]" : "[-]";
            commands += "  |  (f) filter by frame ";
            commands += config.hasFrameFilter() ? "[+]" : "[-]";
            commands += "  | (c) top cpu mode : ";
            System.out.print(commands);

            if (waitForInterval(size)) {
                return true;
            }
        }
    }

    public void addSample(Collection<ThreadInfo> threads) {
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

        public void print(String indent) {
            double rate = (double)count / (double)samples;

            System.out.print(indent);
            System.out.print(frame);
            System.out.print(" - ");
            System.out.print(count);
            System.out.print(" (");
            System.out.print(format.format(rate * 100));
            System.out.println(" %)");

            if (rate > config.getStacksLimitPercent()) {
                for (Node child : sorted) {
                    child.print(indent + "|  ");
                }
            }
        }
        
        public int getTotalFrames() {
            int frames = 1;
            double rate = (double)count / (double)samples;

            if (rate > config.getStacksLimitPercent()) {
                for (Node child : sorted) {
                    frames += child.getTotalFrames();
                }
            }
            return frames;
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