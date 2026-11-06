package dev.betterchats.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.betterchats.api.TeamDefinition;

/** TeamDefinition -> JSON con el mismo esquema que chats/*.json. */
final class TeamJson {

    private TeamJson() {
    }

    static JsonObject toJson(TeamDefinition d) {
        JsonObject o = new JsonObject();
        o.addProperty("id", d.getId());
        o.addProperty("displayName", d.getDisplayName());
        o.addProperty("isDefault", d.isDefault());
        o.addProperty("isStaffOnly", d.isStaffOnly());
        if (d.getPermission() != null) o.addProperty("permission", d.getPermission());
        o.addProperty("implicitMembers", d.hasImplicitMembers());
        o.addProperty("priority", d.getPriority());
        o.addProperty("format", d.getFormat());
        JsonArray tags = new JsonArray();
        d.getAllowedPlayerTags().forEach(tags::add);
        o.add("allowedPlayerTags", tags);
        JsonObject sound = new JsonObject();
        sound.addProperty("enabled", d.getSound().isEnabled());
        sound.addProperty("sound", d.getSound().getSound());
        sound.addProperty("volume", d.getSound().getVolume());
        sound.addProperty("pitch", d.getSound().getPitch());
        o.add("sound", sound);
        if (d.getRateLimit() != null) {
            JsonObject rl = new JsonObject();
            rl.addProperty("messages", d.getRateLimit().getMessages());
            rl.addProperty("perSeconds", d.getRateLimit().getPerSeconds());
            o.add("rateLimit", rl);
        }
        return o;
    }
}
