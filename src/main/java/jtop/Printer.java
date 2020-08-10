package jtop;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class Printer implements Runnable {

    protected final Config config;
    protected final NumberFormat format;
    protected final Map<Character, PrinterCommandHandler> handlers;
    protected volatile boolean active;
    protected volatile List<String> output = new ArrayList<>(Arrays.asList("Collecting first sample..."));

    public Printer(Config config) throws IOException {
        this.config = config;
        
        NumberFormat format = NumberFormat.getInstance();
        format.setMaximumFractionDigits(2);
        format.setMinimumFractionDigits(2);
        format.setMinimumIntegerDigits(1);
        format.setGroupingUsed(false);
        this.format = format;
        this.handlers = createHandlers();
    }

    protected Map<Character, PrinterCommandHandler> createHandlers() {
        Map<Character, PrinterCommandHandler> handlers = new HashMap<>();
        handlers.put('i', this::setInterval);
        handlers.put('n', this::setThreadNameRegex);
        handlers.put('f', this::setFrameFilter);
        handlers.put('s', this::swapActive);
        return handlers;
    }

    public List<String> getOutput() {
        return output;
    }

    private void swapActive(TerminalLineReader reader) {
        config.swapActive();
    }

    private void setInterval(TerminalLineReader reader) {
        try {
            config.setInterval(Long.parseLong(reader.readLine("Enter new interval in (seconds): ")) * 1000);
            System.out.print("\rNew interval now set to: " + (config.getInterval() / 1000) + " seconds.");
        } catch (Exception ex) { }
    }

    private void setThreadNameRegex(TerminalLineReader reader) throws IOException {
        config.parseNameRegex(reader.readLine("Enter thread name regex (empty to remove): "));
        String message = "\rThread name filter is now: ";
        if (config.getNameRegex() == null) {
            message += "<not set>"; 
        } else {
            message += config.getNameRegex().pattern();
        }
        if (config.isNegateNameRegex()) {
            message += " (negated)";
        }
        
        System.out.print(message);
    }

    private void setFrameFilter(TerminalLineReader reader) throws IOException {
        config.parseFrameFilter(reader.readLine("Enter stack frame string (empty to remove): "));
        String message = "\rFrame filter is now: ";
        if (config.getFrameFilter() == null) {
            message += "<not set>";
        } else {
            message += config.getFrameFilter();
        }
        if (config.isNegateFrameFilter()) {
            message += " (negated)";
        }
        
        System.out.print(message);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    protected abstract void runLoop() throws IOException, InterruptedException;

    @Override
    public void run() {
        while (true) {
            try {
                runLoop();
            } catch (InterruptedException ex) {
                return;
            } catch (Throwable ex) {
                ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
                try (PrintWriter writer = new PrintWriter(bytesOut)) {
                    ex.printStackTrace(writer);
                }
                output = Arrays.stream(bytesOut.toString().split("\n"))
                    .map(String::new)
                    .collect(Collectors.toList());
                try {
                    Thread.sleep(config.getInterval());
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    protected abstract String getCommandsString();

}