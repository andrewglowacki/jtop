package jtop;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.NumberFormat;

import org.jline.terminal.Terminal;

public abstract class Printer {

    protected final Terminal terminal;
    protected final Config config;
    protected final NumberFormat format;

    public Printer(Terminal terminal, Config config) throws IOException {
        this.terminal = terminal;
        this.config = config;
        
        NumberFormat format = NumberFormat.getInstance();
        format.setMaximumFractionDigits(2);
        format.setMinimumFractionDigits(2);
        format.setMinimumIntegerDigits(1);
        format.setGroupingUsed(false);
        this.format = format;
    }

    public abstract boolean handleCommand() throws IOException;

    public Character handleGeneralCommand() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String commandLine = reader.readLine().trim();
        if (commandLine.isEmpty()) {
            return null;
        }

        char c = commandLine.charAt(0);
        switch (c) {
            case 'i':
                System.out.print("Enter new interval in (seconds): ");
                try {
                    config.setInterval(Long.parseLong(reader.readLine()) * 1000);
                } catch (Exception ex) { }
                break;
            case 'n':
                System.out.print("Enter thread name regex (empty to remove): ");
                config.parseNameRegex(reader.readLine());
                break;
            case 'f':
                System.out.print("Enter stack frame string (empty to remove): ");
                config.parseFrameFilter(reader.readLine());
                break;
            default:
                return c;
        }

        return null;
    }

    protected void skipRemaining(BufferedReader reader) throws IOException {
        // skip extra input
        while (System.in.available() > 0) {
            reader.readLine();
        }
    }

    protected boolean waitForInterval(int clearBack) throws IOException {
        try {
            long duration = System.currentTimeMillis();
            while (System.currentTimeMillis() - duration < config.getInterval()) {
                Thread.sleep(250);
                if (System.in.available() > 0) {
                    break;
                }
            }
            
            System.out.print('\r');
            if (System.in.available() > 0) {
                if (!handleCommand()) {
                    return true;
                }
            }
        } catch (InterruptedException e) {
            System.out.println();
            return false;
        }
        try {
            if (clearBack > terminal.getHeight()) {
                System.out.print("\033[" + clearBack + "A"); // Move up
                Thread.sleep(1000);
                for (int i = terminal.getHeight(); i < clearBack; i++) {
                    System.out.print("\033M"); // Move/scroll up one line
                    Thread.sleep(1000);
                }
                System.out.print("\033[J"); // Clear screen from cursor down
                Thread.sleep(1000);
            } else {
                System.out.print("\033[" + clearBack + "A"); // Move up
                System.out.print("\033[J"); // Clear screen from cursor down
            }
        } catch (Throwable ex) { }
        System.out.flush();
        return false;
    }

    public abstract boolean run() throws IOException;
}