package jtop;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Executor {

    public static List<String> execute(String ... args) throws IOException {
        return execute(line -> line, args);
    }

    public static List<String> execute(Function<String, String> filter, String ... args) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(args);
        Process process = builder.start();
        try {
            process.getOutputStream().close();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                return reader.lines()
                    .map(filter)
                    .filter(line -> line != null)
                    .collect(Collectors.toList());
            }
        } finally {
            process.destroyForcibly();
        }
    }

    public static List<ThreadInfo> sampleJvm(String pid) throws IOException {
        String jstack = System.getProperty("jstack", "jstack");

        List<ThreadInfo> threads = new ArrayList<>();
        List<String> frames = new ArrayList<>();
        String name = null;
        double cpuTime = 0;
        for (String line : execute(jstack, pid)) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.charAt(0) == '"' && line.indexOf("tid=") > 0) {
                frames = new ArrayList<>();
                
                int osPrio = line.lastIndexOf("os_prio=");
                int nameEnd = line.lastIndexOf('"', osPrio);
                name = line.substring(1, nameEnd);

                int cpuIndex = line.indexOf("cpu=", osPrio);
                if (cpuIndex > 0) {
                    cpuIndex += 4;
                    int end = line.indexOf("ms ", cpuIndex);
                    cpuTime = Double.parseDouble(line.substring(cpuIndex, end));
                }

                int tidIndex = line.indexOf("tid=", nameEnd) + 4;
                int tidEnd = line.indexOf(' ', tidIndex);
                String id = line.substring(tidIndex, tidEnd);

                threads.add(new ThreadInfo(id, name, cpuTime, frames));
            } else if (line.startsWith("java.lang.Thread.State:")) {
                continue;
            } else if (line.startsWith("at ")) {
                frames.add(line.substring(3));
            } else if (line.startsWith("- ")) {
                int refOpen = line.indexOf('<');
                int refClose = line.indexOf('>', refOpen);
                line = line.substring(0, refOpen + 1) + line.substring(refClose);
                frames.add(line);
            }
        }
        return threads;
    }

}