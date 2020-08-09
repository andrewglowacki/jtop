package jtop;

import java.util.List;

public class ThreadInfo {
    private final String id;
    private final String name;
    private final double cpuTime;
    private final List<String> frames;

    public ThreadInfo(String id, String name, double cpuTime, List<String> frames) {
        this.id = id;
        this.name = name;
        this.cpuTime = cpuTime;
        this.frames = frames;
    }

    public String getId() {
        return id;
    }
    public String getName() {
        return name;
    }
    public double getCpuTime() {
        return cpuTime;
    }
    public List<String> getFrames() {
        return frames;
    }
}