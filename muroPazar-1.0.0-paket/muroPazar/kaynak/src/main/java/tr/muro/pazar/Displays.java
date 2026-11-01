package tr.muro.pazar;

import static tr.muro.pazar.Models.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class Displays implements Listener {
    private final MuroPazar plugin;
    private final NamespacedKey key;
    private final Map<String,List<Entity>> entities = new HashMap<>();
    private final Map<String, Shop> shops = new HashMap<>();

    Displays(MuroPazar plugin) { this.plugin=plugin; key=new NamespacedKey(plugin,"shop"); }
    void refresh() throws Exception {
        clear();
        shops.clear();
        for(Shop s:plugin.store.shops()) {
            shops.put(s.id(),s);
            renderIfLoaded(s);
        }
    }
    void update(Shop s) {
        remove(s.id());
        shops.put(s.id(),s);
        renderIfLoaded(s);
    }
    void remove(String id) {
        List<Entity> list=entities.remove(id);
        if(list!=null) list.forEach(Entity::remove);
        shops.remove(id);
    }
    void clear() {
        entities.values().forEach(list->list.forEach(Entity::remove));
        entities.clear();
        for(World w:Bukkit.getWorlds()) for(Entity e:w.getEntities()) if(tag(e)!=null)e.remove();
    }
    private String tag(Entity entity) { return entity.getPersistentDataContainer().get(key,PersistentDataType.STRING); }
    private <T extends Entity> T add(Shop s,Location l,Class<T> type,java.util.function.Consumer<T> setup) {
        T entity=l.getWorld().spawn(l,type,e->{
            e.setPersistent(false); e.setInvulnerable(true); e.setSilent(true);
            e.getPersistentDataContainer().set(key,PersistentDataType.STRING,s.id());
            setup.accept(e);
        });
        entities.computeIfAbsent(s.id(),ignored->new ArrayList<>()).add(entity);
        return entity;
    }
    private void block(Shop s,Location l,Material material,float x,float y,float z) {
        add(s,l,BlockDisplay.class,e->{
            e.setBlock(material.createBlockData());
            e.setTransformation(new Transformation(new Vector3f(-x/2,0,-z/2),new Quaternionf(),new Vector3f(x,y,z),new Quaternionf()));
        });
    }
    private void renderIfLoaded(Shop s) {
        Location l=s.location();
        if(l==null || s.expired() || !l.getWorld().isChunkLoaded(l.getBlockX()>>4,l.getBlockZ()>>4)) return;
        if(entities.containsKey(s.id())) return;
        ConfigurationSection style=plugin.getConfig().getConfigurationSection("styles."+s.style());
        if(style==null) style=plugin.getConfig().getConfigurationSection("styles."+plugin.defaultStyle());
        final ConfigurationSection skin=style;
        Material canopy=Material.valueOf(skin.getString("canopy"));
        Material base=Material.valueOf(skin.getString("base"));
        block(s,l.clone().add(0,0.1,0),base,1.6f,.65f,1.1f);
        block(s,l.clone().add(0,2.05,0),canopy,2f,.25f,1.5f);
        block(s,l.clone().add(-.78,.65,0),Material.OAK_FENCE,.12f,1.4f,.12f);
        block(s,l.clone().add(.78,.65,0),Material.OAK_FENCE,.12f,1.4f,.12f);
        add(s,l.clone().add(0,.7,0),ArmorStand.class,e->{
            e.setGravity(false);e.setSmall(true);e.setArms(true);e.setBasePlate(false);e.setMarker(true);
            ItemStack helmet=new ItemStack(Material.valueOf(skin.getString("helmet")));
            if(helmet.getItemMeta() instanceof SkullMeta meta){
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(s.owner()));helmet.setItemMeta(meta);
            }
            ItemStack chest=new ItemStack(Material.LEATHER_CHESTPLATE);
            LeatherArmorMeta cm=(LeatherArmorMeta)chest.getItemMeta();
            cm.setColor(Color.fromRGB(Integer.parseInt(skin.getString("color").substring(1),16)));chest.setItemMeta(cm);
            e.getEquipment().setHelmet(helmet);e.getEquipment().setChestplate(chest);
        });
        add(s,l.clone().add(0,2.65,0),TextDisplay.class,e->{
            e.setText("§a§l"+s.name()+"\n§f"+s.ownerName()+" §8• §eSağ tıkla");
            e.setBillboard(Display.Billboard.CENTER);e.setSeeThrough(false);e.setShadowed(true);
            e.setLineWidth(250);
        });
        add(s,l.clone(),Interaction.class,e->{
            e.setInteractionWidth(2f);e.setInteractionHeight(2.5f);e.setResponsive(true);
        });
    }
    void particles() {
        if(!plugin.getConfig().getBoolean("shop.particle-effects"))return;
        for(Shop s:shops.values()){
            Location l=s.location();
            if(l!=null && entities.containsKey(s.id()) && !s.expired())
                l.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,l.clone().add(0,2.5,0),2,.65,.1,.4,0);
        }
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=false)
    public void interact(PlayerInteractEntityEvent e) {
        String id=tag(e.getRightClicked());
        if(id==null)return;
        e.setCancelled(true);
        if(e.getHand()!=org.bukkit.inventory.EquipmentSlot.HAND)return;
        plugin.run(e.getPlayer(),()->{
            plugin.requireUse(e.getPlayer());
            Shop s=plugin.store.shop(id);
            if(s==null || s.expired())throw new IllegalArgumentException("Pazar artık açık değil.");
            plugin.menus.shop(e.getPlayer(),s.id(),0);
        });
    }
    @EventHandler public void protect(EntityDamageEvent e) { if(tag(e.getEntity())!=null)e.setCancelled(true); }
    @EventHandler public void manipulate(PlayerArmorStandManipulateEvent e) { if(tag(e.getRightClicked())!=null)e.setCancelled(true); }
    @EventHandler public void load(ChunkLoadEvent e) {
        for(Entity entity:e.getChunk().getEntities())if(tag(entity)!=null)entity.remove();
        for(Shop s:shops.values()){
            Location l=s.location();
            if(l!=null && l.getWorld().equals(e.getWorld()) && (l.getBlockX()>>4)==e.getChunk().getX() && (l.getBlockZ()>>4)==e.getChunk().getZ()){
                entities.remove(s.id());renderIfLoaded(s);
            }
        }
    }
    @EventHandler(ignoreCancelled=true) public void unload(ChunkUnloadEvent e) {
        Iterator<Map.Entry<String,List<Entity>>> it=entities.entrySet().iterator();
        while(it.hasNext()){
            Map.Entry<String,List<Entity>> entry=it.next();
            if(!entry.getValue().isEmpty()){
                Location l=entry.getValue().get(0).getLocation();
                if(l.getWorld().equals(e.getWorld()) && (l.getBlockX()>>4)==e.getChunk().getX() && (l.getBlockZ()>>4)==e.getChunk().getZ()){
                    entry.getValue().forEach(Entity::remove);it.remove();
                }
            }
        }
    }
    @EventHandler public void worldLoad(WorldLoadEvent e) { for(Shop s:shops.values())if(s.world().equals(e.getWorld().getName()))renderIfLoaded(s); }
}
