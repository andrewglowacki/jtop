package jtop;

import static org.jline.keymap.KeyMap.key;
import static org.jline.keymap.KeyMap.del;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.cli.Option;
import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.terminal.Terminal;
import org.jline.utils.InfoCmp.Capability;
import org.jline.utils.NonBlockingReader;

public class DisplayLoop {

    public static void run(Config config, Terminal terminal) throws IOException {

        terminal.enterRawMode();

        terminal.puts(Capability.enter_ca_mode);
        terminal.puts(Capability.keypad_xmit);
        
        BindingReader bindingReader = new BindingReader(terminal.reader());

        KeyMap<Operation> keyMap = new KeyMap<>();
        keyMap.bind(Operation.UP, key(terminal, Capability.key_up));
        keyMap.bind(Operation.DOWN, key(terminal, Capability.key_down));
        keyMap.bind(Operation.LEFT, key(terminal, Capability.key_left));
        keyMap.bind(Operation.RIGHT, key(terminal, Capability.key_right));
        keyMap.bind(Operation.BACKSPACE, key(terminal, Capability.key_backspace), del());
        keyMap.bind(Operation.NEWLINE, "\r");
        keyMap.bind(Operation.COMMAND, "r"); // reset command
        keyMap.bind(Operation.COMMAND, Runner.options.getOptions()
            .stream()
            .map(Option::getOpt)
            .collect(Collectors.toList()));
        for (int i = 32; i < 127; i++) {
            keyMap.bindIfNotBound(Operation.CHAR, Character.toString((char)i));
        }

        terminal.writer().flush();

        clearTerminal();
        
        TerminalLineReader lineReader = new TerminalLineReader(keyMap, bindingReader);
        
        int y = 0;
        int x = 0;

        int yPrev = 0;
        int xPrev = 0;

        Printer lastPrinter = config.getActive();
        while (true) {
            Printer printer = config.getActive();
            List<String> output = printer.getOutput();

            // when switching modes, start at the top
            if (lastPrinter != printer) {
                lastPrinter = printer;

                int swapX = x;
                int swapY = y;

                x = xPrev;
                y = yPrev;

                xPrev = swapX;
                yPrev = swapY;
            }

            int from = y;

            int height = terminal.getHeight() - 2;
            int width = terminal.getWidth();
            int to = -1;
            if (from < output.size()) {
                to = from + height;
                if (to > output.size()) {
                    to = output.size();
                }

                for (String line : output.subList(from, to)) {
                    int lineLen = line.length() - x;
                    if (lineLen < 0) {
                        System.out.println();
                        continue;
                    }
                    if (lineLen > width) {
                        lineLen = width;
                    }
                    int lineEnd = x + lineLen;
                    line = line.substring(x, lineEnd);

                    System.out.println(line);
                }
            }

            // position cursor at the bottom of the screen
            System.out.print("\033[" + (height + 1) + ";0H" );

            String commands = printer.getCommandsString();
            if (commands.length() > width) {
                if (x > 0) {
                    commands = commands.substring(x);
                }
                if (commands.length() > width) {
                    commands = commands.substring(0, width);
                }
            }
            System.out.println(commands);
            
            long interval = config.getInterval();
            long started = System.currentTimeMillis();
            boolean update = false;
            while (!update && System.currentTimeMillis() - started < interval) {
                int c = bindingReader.peekCharacter(interval - (System.currentTimeMillis() - started));
                if (c == NonBlockingReader.READ_EXPIRED) {
                    continue;
                } else {
                    Operation op = bindingReader.readBinding(keyMap, null, false);
                    if (op == null) {
                        op = Operation.OTHER;
                    }
                    switch (op) {
                        case UP:
                            y--;
                            if (y < 0) {
                                y = 0;
                            }
                            update = true;
                            break;
                        case DOWN:
                            y++;
                            update = true;
                            break;
                        case RIGHT:
                            x++;
                            update = true;
                            break;
                        case LEFT:
                            x--;
                            if (x < 0) {
                                x = 0;
                            }
                            update = true;
                            break;
                        case COMMAND:
                            PrinterCommandHandler handler = printer.handlers.get((char)c);
                            if (handler != null) {
                                handler.handle(lineReader);
                                update = true;
                            }
                            break;
                        default:
                            break;
                    }
                }
            }

            clearTerminal();
        }
    }

    protected static boolean clearTerminal() throws IOException {
        System.out.print("\033[H"); // Move to the top left corner of the terminal
        System.out.print("\033[J"); // Clear screen from cursor down
        System.out.flush();
        return false;
    }

    public enum Operation {
        UP,
        DOWN,
        RIGHT,
        LEFT,
        COMMAND,
        OTHER,
        BACKSPACE,
        NEWLINE,
        CHAR
    }

}