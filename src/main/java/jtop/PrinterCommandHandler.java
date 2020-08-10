package jtop;

import java.io.IOException;

public interface PrinterCommandHandler {

    public void handle(TerminalLineReader reader) throws IOException;
}