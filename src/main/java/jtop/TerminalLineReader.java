package jtop;

import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.utils.NonBlockingReader;

import jtop.DisplayLoop.Operation;

public class TerminalLineReader {
    private final KeyMap<Operation> keyMap;
    private final BindingReader reader;

    public TerminalLineReader(KeyMap<Operation> keyMap, BindingReader reader) {
        this.keyMap = keyMap;
        this.reader = reader;
    }

    public String readLine(String prompt) {
        System.out.print("\r" + prompt);
        
        StringBuilder builder = new StringBuilder();
        while (true) {
            if (reader.peekCharacter(1000) == NonBlockingReader.READ_EXPIRED) {
                continue;
            }
            Operation op = reader.readBinding(keyMap, null, false);
            if (op == Operation.NEWLINE) {
                break;
            } else if (op == Operation.BACKSPACE) {
                if (builder.length() > 0) {
                    builder.setLength(builder.length() - 1);
                    System.out.print("\r" + prompt + builder.toString() + " ");
                    continue;
                } else {
                    return "";
                }
            } else if (op == Operation.CHAR || op == Operation.COMMAND) {
                builder.append(reader.getLastBinding());
                System.out.print("\r" + prompt + builder.toString() + "    ");
            }
        }
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) { }
        return builder.toString();
    }
}