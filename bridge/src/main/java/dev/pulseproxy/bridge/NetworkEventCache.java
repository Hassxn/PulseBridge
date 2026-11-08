package dev.pulseproxy.bridge;

import java.util.*;

/** Immutable proxy snapshots; timers are calculated locally and expire without a heartbeat. */
public final class NetworkEventCache {
    public static final class Event {
        public final String server, type, stage;
        public final long deadline;
        public Event(String server, String type, String stage, long deadline) {
            if (server == null || !server.matches("[A-Za-z0-9_.-]{1,64}")) throw new IllegalArgumentException("Invalid server");
            if (!Arrays.asList("SOTW", "EOTW", "RESTART").contains(type)) throw new IllegalArgumentException("Invalid event type");
            if (!Arrays.asList("COUNTDOWN", "PROTECTION", "WAITING", "LIVE", "READY").contains(stage)) throw new IllegalArgumentException("Invalid event stage");
            if (deadline < 0) throw new IllegalArgumentException("Invalid deadline");
            this.server=server; this.type=type; this.stage=stage; this.deadline=deadline;
        }
        public boolean visible(long now, boolean waiting) {
            return ((stage.equals("COUNTDOWN") || stage.equals("PROTECTION")) && deadline > now)
                    || (waiting && type.equals("RESTART") && stage.equals("WAITING"));
        }
        public String remaining(long now) {
            long s=deadline<=now?0:((deadline-now-1)/1000)+1;
            return (s>=86400?s/86400+"d ":"")+String.format(Locale.ROOT,"%02d:%02d:%02d",s/3600%24,s/60%60,s%60);
        }
    }
    private List<Event> events=Collections.emptyList();
    private String origin="", localServer="";
    private long sentAt=-1, receivedAt=-1;
    public synchronized boolean accept(String origin, String localServer, long sentAt, List<Event> incoming, long now) {
        if(origin==null||incoming==null||incoming.size()>64) return false;
        if(this.origin.equals(origin)&&sentAt<this.sentAt) return false;
        this.origin=origin;this.localServer=localServer;this.sentAt=sentAt;this.receivedAt=now;
        List<Event> copy=new ArrayList<Event>(incoming);
        Collections.sort(copy,new Comparator<Event>(){public int compare(Event a,Event b){int priority=Boolean.compare(!a.type.equals("RESTART"),!b.type.equals("RESTART"));return priority!=0?priority:Long.compare(a.deadline,b.deadline);}});
        events=Collections.unmodifiableList(copy);return true;
    }
    public synchronized List<Event> active(long now, long ttl, boolean waiting, boolean localOnly) {
        if(receivedAt<0||now-receivedAt>=ttl) return Collections.emptyList();
        List<Event> out=new ArrayList<Event>();
        for(Event e:events) if(e.visible(now,waiting)&&(!localOnly||e.server.equalsIgnoreCase(localServer)))out.add(e);
        return out;
    }
    public synchronized String localServer(){return localServer;}
    public synchronized void clear(){events=Collections.emptyList();origin="";sentAt=-1;receivedAt=-1;}
}
