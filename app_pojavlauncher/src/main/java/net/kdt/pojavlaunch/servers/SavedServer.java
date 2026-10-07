package net.kdt.pojavlaunch.servers;

import androidx.annotation.Nullable;

/** A server saved in the game or played through the launcher. */
public class SavedServer {
    public String name;
    public String address;
    public long lastPlayed;
    @Nullable public String instanceName;

    public transient ServerPinger.Status status;
    public transient boolean pinging;
    public transient boolean pingFailed;

    public SavedServer() {}

    public SavedServer(String name, String address) {
        this.name = name;
        this.address = address;
    }

    public String key() {
        return address.trim().toLowerCase();
    }
}
