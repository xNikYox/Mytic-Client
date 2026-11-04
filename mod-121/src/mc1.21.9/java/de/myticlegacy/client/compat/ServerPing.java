package de.myticlegacy.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Server anpingen (Spielerzahl, Ping) wie die Mehrspieler-Liste – im Hintergrund. Fassung bis 1.21.10 (ohne EventLoopGroupHolder). */
public final class ServerPing {
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "Mytic Server-Ping");
        t.setDaemon(true);
        return t;
    });
    private final ServerStatusPinger pinger = new ServerStatusPinger();

    public void ping(ServerData data) {
        data.ping = -1;
        POOL.submit(() -> {
            try {
                pinger.pingServer(data, () -> {
                }, () -> {
                });
            } catch (Exception e) {
                data.ping = -2;
            }
        });
    }

    public void tick() {
        pinger.tick();
    }

    public void close() {
        pinger.removeAll();
    }
}
