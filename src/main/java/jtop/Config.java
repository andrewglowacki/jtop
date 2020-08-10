package jtop;

import java.util.regex.Pattern;

public class Config {
    
    private volatile String frameFilter;
    private volatile boolean negateFrameFilter;
    private volatile Pattern nameRegex;
    private volatile boolean negateNameRegex;
    private volatile long interval = 5000;
    private volatile String pid;
    private volatile long cpuTimeCutoff = 1;
    private volatile int topLimit = 20;
    private volatile double stacksLimitPercent = 0.05;
    private volatile Printer active;
    private volatile Printer other;

    public void swapActive() {
        Printer swap = active;
        active = other;
        other = swap;

        active.setActive(true);
        other.setActive(false);
    }

    public void setActive(Printer active, Printer other) {
        this.active = active;
        this.other = other;
    }

    public Printer getActive() {
        return active;
    }

    public double getStacksLimitPercent() {
        return stacksLimitPercent;
    }
    public long getCpuTimeCutoff() {
        return cpuTimeCutoff;
    }
    public int getTopLimit() {
        return topLimit;
    }
    public void setPid(String pid) {
        this.pid = pid;
    }
    public String getPid() {
        return pid;
    }
    public void setInterval(long interval) {
        this.interval = interval;
    }
    public long getInterval() {
        return interval;
    }
    public Pattern getNameRegex() {
        return nameRegex;
    }
    public boolean isNegateNameRegex() {
        return negateNameRegex;
    }
    public String getFrameFilter() {
        return frameFilter;
    }
    public boolean isNegateFrameFilter() {
        return negateFrameFilter;
    }

    public void parseNameRegex(String nameRegex) {
        try {
            nameRegex = nameRegex.trim();
            negateNameRegex = nameRegex.startsWith("~");
            if (negateNameRegex) {
                nameRegex = nameRegex.substring(1);
            }
            this.nameRegex = nameRegex.isEmpty() ? null : Pattern.compile(nameRegex);
        } catch (Exception ex) {
            System.out.println("Invalid regex: " + ex.getMessage());
        }
    }
    public void setNameRegex(Pattern nameRegex, boolean negate) {
        this.nameRegex = nameRegex;
        this.negateNameRegex = negate;
    }

    public void parseCpuTimeCutoff(String cpuTime) {
        try {
            cpuTimeCutoff = Long.parseLong(cpuTime);
        } catch (Exception ex) {
            System.out.println("Invalid cpu time cutoff: " + ex.getMessage());
        }
    }
    
    public void parseFrameFilter(String frameFilter) {
        frameFilter = frameFilter.trim();
        negateFrameFilter = frameFilter.startsWith("~");
        if (negateFrameFilter) {
            frameFilter = frameFilter.substring(1);
        }
        this.frameFilter = frameFilter.isEmpty() ? null : frameFilter;
    }

    public void setFrameFilter(String frameFilter, boolean negate) {
        this.frameFilter = frameFilter;
        this.negateFrameFilter = negate;
    }
    
    public boolean hasNameRegex() {
        return nameRegex != null;
    }
    public boolean hasFrameFilter() {
        return frameFilter != null;
    }

    public boolean matchesNameRegex(ThreadInfo info) {
        if (!hasNameRegex()) {
            return true;
        }
        if (nameRegex.matcher(info.getName()).matches()) {
            return !negateNameRegex;
        } else {
            return negateNameRegex;
        }
    }

    public int findMatchingFrame(ThreadInfo info) {
        if (!hasFrameFilter()) {
            return info.getFrames().size() - 1;
        } else {
            for (int i = 0; i < info.getFrames().size(); i++) {
                if (info.getFrames().indexOf(frameFilter) > 0) {
                    return i;
                }
            }
            return -1;
        }
    }

    public boolean matchesFrameFilter(ThreadInfo info) {
        if (!hasFrameFilter()) {
            return true;
        }
        if (findMatchingFrame(info) >= 0) {
            return !negateFrameFilter;
        } else {
            return negateFrameFilter;
        }
    }

    public boolean keepThread(ThreadInfo info) {
        return matchesNameRegex(info) && matchesFrameFilter(info);
    }
	public void parseTopLimit(String input) {
        try {
            topLimit = Integer.parseInt(input);
            if (topLimit <= 0) {
                topLimit = Integer.MAX_VALUE;
            }
        } catch (Exception ex) {
            System.out.println("Invalid top limit: " + ex.getMessage());
        }
	}
	public void parseStacksLimitPercent(String input) {
        try {
            stacksLimitPercent = (double)Integer.parseInt(input) / 100.0;
        } catch (Exception ex) {
            System.out.println("Invalid limit percentage: " + ex.getMessage());
        }
	}

}