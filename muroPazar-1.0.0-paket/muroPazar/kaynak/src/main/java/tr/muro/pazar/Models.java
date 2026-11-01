package tr.muro.pazar;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

final class Models {
    private Models() {}
    record Shop(String id, UUID owner, String ownerName, String name, String world,
                double x, double y, double z, float yaw, String style, long expires) {
        Location location() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z, yaw, 0);
        }
        boolean expired() { return expires <= System.currentTimeMillis(); }
    }
    record Listing(long id, String shop, UUID owner, String name, String data, long price, String state) {
        ItemStack item() { return decode(data); }
    }
    record Mail(long id, UUID owner, String data, String reason, String state) {
        ItemStack item() { return decode(data); }
    }
    record Bank(long id, UUID owner, long amount, String direction, String state) {}
    static String encode(ItemStack item) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("item", item.clone());
        return yaml.saveToString();
    }
    static ItemStack decode(String text) {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(text);
            ItemStack item = yaml.getItemStack("item");
            if (item == null || item.getType().isAir()) throw new IllegalStateException("Boş eşya kaydı");
            return item;
        } catch (Exception e) {
            throw new IllegalStateException("Eşya kaydı okunamadı; kayıt korunuyor.", e);
        }
    }
}
