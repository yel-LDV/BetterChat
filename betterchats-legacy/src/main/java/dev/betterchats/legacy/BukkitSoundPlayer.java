package dev.betterchats.legacy;

import dev.betterchats.api.SoundSettings;
import dev.betterchats.core.platform.ChatPlayer;
import dev.betterchats.core.platform.SoundPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Sonidos por clave String ({@code playSound(Location, String, ...)}), nunca
 * {@code Sound.valueOf}. En 1.8 las claves modernas (1.9+) se traducen a las
 * antiguas; en servidores modernos se envian tal cual.
 */
final class BukkitSoundPlayer implements SoundPlayer {

    /** Clave moderna -> clave 1.8. */
    private static final Map<String, String> MODERN_TO_LEGACY = Map.ofEntries(
            Map.entry("entity.experience_orb.pickup", "random.orb"),
            Map.entry("entity.player.levelup", "random.levelup"),
            Map.entry("block.note_block.pling", "note.pling"),
            Map.entry("block.note_block.harp", "note.harp"),
            Map.entry("block.note_block.bass", "note.bass"),
            Map.entry("block.note_block.basedrum", "note.bd"),
            Map.entry("block.note_block.snare", "note.snare"),
            Map.entry("block.note_block.hat", "note.hat"),
            Map.entry("block.note_block.bell", "note.pling"),
            Map.entry("block.note_block.chime", "note.pling"),
            Map.entry("ui.button.click", "random.click"),
            Map.entry("entity.item.pickup", "random.pop"),
            Map.entry("entity.chicken.egg", "mob.chicken.plop"),
            Map.entry("entity.arrow.hit_player", "random.successful_hit"),
            Map.entry("entity.villager.yes", "mob.villager.yes"),
            Map.entry("entity.villager.no", "mob.villager.no"),
            Map.entry("entity.villager.ambient", "mob.villager.idle"),
            Map.entry("entity.cat.ambient", "mob.cat.meow"),
            Map.entry("entity.enderman.teleport", "mob.endermen.portal"),
            Map.entry("entity.blaze.hurt", "mob.blaze.hit"),
            Map.entry("block.anvil.land", "random.anvil_land"),
            Map.entry("block.anvil.use", "random.anvil_use"),
            Map.entry("block.chest.open", "random.chestopen"),
            Map.entry("block.chest.close", "random.chestclosed"),
            Map.entry("entity.generic.explode", "random.explode"),
            Map.entry("entity.firework_rocket.launch", "fireworks.launch"),
            Map.entry("entity.firework_rocket.blast", "fireworks.blast"),
            Map.entry("entity.firework_rocket.twinkle", "fireworks.twinkle"),
            Map.entry("block.lever.click", "random.click"),
            Map.entry("entity.ender_dragon.growl", "mob.enderdragon.growl"),
            Map.entry("entity.wither.spawn", "mob.wither.spawn"),
            Map.entry("block.portal.trigger", "portal.trigger"),
            Map.entry("entity.player.burp", "random.burp"),
            Map.entry("entity.item.break", "random.break"),
            Map.entry("block.wooden_door.open", "random.door_open"),
            Map.entry("block.wooden_door.close", "random.door_close"));

    /** Claves 1.8 aceptadas sin aviso (las mas usadas en chats). */
    private static final Set<String> LEGACY_KEYS = Set.copyOf(MODERN_TO_LEGACY.values());

    private final boolean legacyServer;

    BukkitSoundPlayer() {
        String v = Bukkit.getBukkitVersion();
        this.legacyServer = v.startsWith("1.7") || v.startsWith("1.8");
    }

    private String resolve(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        if (k.startsWith("minecraft:")) k = k.substring("minecraft:".length());
        if (!legacyServer) return k;
        return MODERN_TO_LEGACY.getOrDefault(k, k);
    }

    @Override
    public void play(ChatPlayer player, SoundSettings sound) {
        if (!(player instanceof BukkitChatPlayer bcp)) return;
        Player p = bcp.bukkit();
        if (!p.isOnline()) return;
        p.playSound(p.getLocation(), resolve(sound.getSound()), sound.getVolume(), sound.getPitch());
    }

    @Override
    public String validate(String key) {
        if (!key.matches("(minecraft:)?[a-z0-9_.]+")) {
            return "clave '" + key + "' invalida: usa minusculas y puntos (p. ej. entity.experience_orb.pickup)";
        }
        if (!legacyServer) return null;
        String k = key.startsWith("minecraft:") ? key.substring(10) : key;
        if (MODERN_TO_LEGACY.containsKey(k) || LEGACY_KEYS.contains(k)) return null;
        return "clave '" + key + "' sin traduccion a 1.8; se enviara tal cual (si no suena usa una de 1.8, "
                + "p. ej. random.orb o note.pling)";
    }
}
