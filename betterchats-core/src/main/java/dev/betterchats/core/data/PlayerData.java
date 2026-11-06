package dev.betterchats.core.data;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Datos persistentes de un jugador: chat activo, ignorados y canales de spy.
 * Thread-safe: el chat asincrono lo lee mientras los comandos lo modifican.
 */
public final class PlayerData {

    private final UUID uuid;
    private volatile String activeTeam;
    private final Set<UUID> ignored = ConcurrentHashMap.newKeySet();
    private final Set<String> spyChannels = ConcurrentHashMap.newKeySet();
    private volatile String chatColor;
    private final Set<String> chatFormats = ConcurrentHashMap.newKeySet();
    private volatile boolean dirty;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID uuid() { return uuid; }

    /** Puede ser null (= chat por defecto). */
    public String activeTeam() { return activeTeam; }

    public void activeTeam(String teamId) {
        this.activeTeam = teamId;
        this.dirty = true;
    }

    public Set<UUID> ignored() { return Set.copyOf(ignored); }

    public boolean isIgnoring(UUID other) { return ignored.contains(other); }

    public boolean ignore(UUID other) {
        boolean changed = ignored.add(other);
        if (changed) dirty = true;
        return changed;
    }

    public boolean unignore(UUID other) {
        boolean changed = ignored.remove(other);
        if (changed) dirty = true;
        return changed;
    }

    public Set<String> spyChannels() { return Set.copyOf(spyChannels); }

    public boolean isSpying(String channel) { return spyChannels.contains(channel); }

    public void setSpying(String channel, boolean on) {
        boolean changed = on ? spyChannels.add(channel) : spyChannels.remove(channel);
        if (changed) dirty = true;
    }

    /** Id del color/efecto elegido en /chatcolor, o null. */
    public String chatColor() { return chatColor; }

    public void chatColor(String id) {
        this.chatColor = id;
        this.dirty = true;
    }

    /** Ids de los formatos (negrita, cursiva...) elegidos en /chatcolor. */
    public Set<String> chatFormats() { return Set.copyOf(chatFormats); }

    public boolean hasChatFormat(String id) { return chatFormats.contains(id); }

    public void setChatFormat(String id, boolean on) {
        boolean changed = on ? chatFormats.add(id) : chatFormats.remove(id);
        if (changed) dirty = true;
    }

    public void clearChatStyle() {
        chatColor = null;
        chatFormats.clear();
        dirty = true;
    }

    public boolean isDirty() { return dirty; }

    public void markClean() { dirty = false; }

    public void markDirty() { dirty = true; }
}
