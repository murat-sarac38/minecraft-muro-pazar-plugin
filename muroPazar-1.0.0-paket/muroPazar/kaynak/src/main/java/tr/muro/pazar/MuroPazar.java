package tr.muro.pazar;

import static tr.muro.pazar.Models.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class MuroPazar extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    Store store;
    Menus menus;
    Displays displays;
    private VaultBridge vault;
    private final Map<UUID, Prompt> prompts=new ConcurrentHashMap<>();
    private final Map<UUID,Long> clickTimes=new HashMap<>();
    private record Prompt(long expires, Input action) {}
    @FunctionalInterface interface Action { void run() throws Exception; }
    @FunctionalInterface interface Input { void accept(String value) throws Exception; }

    @Override public void onEnable() {
        try {
            saveDefaultConfig();validateConfig();
            if(!getDataFolder().exists() && !getDataFolder().mkdirs())throw new IllegalStateException("Veri klasörü açılamadı.");
            store=new Store(getDataFolder().toPath().resolve("pazar.db"));
            if(getConfig().getBoolean("vault-transfers")){
                if(!Bukkit.getPluginManager().isPluginEnabled("Vault"))throw new IllegalStateException("vault-transfers açık, Vault bulunamadı.");
                vault=new VaultBridge();
            }
            menus=new Menus(this);displays=new Displays(this);
            Bukkit.getPluginManager().registerEvents(this,this);
            Bukkit.getPluginManager().registerEvents(menus,this);
            Bukkit.getPluginManager().registerEvents(displays,this);
            Objects.requireNonNull(getCommand("muropazar")).setExecutor(this);
            getCommand("muropazar").setTabCompleter(this);
            for(Player p:Bukkit.getOnlinePlayers())joinWallet(p);
            expire();displays.refresh();
            Bukkit.getScheduler().runTaskTimer(this,()->{
                try {expire();displays.particles();}catch(Exception e){getLogger().log(Level.SEVERE,"Pazar bakımı başarısız.",e);}
            },100,100);
            List<String> pending=store.pending();
            if(!pending.isEmpty())getLogger().warning("İncelenmesi gereken "+pending.size()+" işlem var. /muropazar incele");
            getLogger().info("muroPazar etkin. Spigot 26.2 API; SQLite cüzdan ve eşya emanet sistemi.");
        }catch(Exception e){
            getLogger().log(Level.SEVERE,"muroPazar başlatılamadı; veri korunarak kapatılıyor.",e);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }
    @Override public void onDisable() {
        if(menus!=null)for(Player p:Bukkit.getOnlinePlayers())if(p.getOpenInventory().getTopInventory().getHolder() instanceof Menus.Menu)p.closeInventory();
        if(displays!=null)displays.clear();
        if(store!=null)try{store.close();}catch(Exception e){getLogger().log(Level.SEVERE,"Veritabanı kapanamadı.",e);}
        prompts.clear();
    }
    private void validateConfig() {
        long hours=getConfig().getLong("shop.duration-hours");
        int max=getConfig().getInt("shop.max-listings");
        double distance=getConfig().getDouble("shop.minimum-distance");
        if(hours<1||hours>8760||max<1||max>450||!Double.isFinite(distance)||distance<2||distance>128)
            throw new IllegalArgumentException("shop ayarları geçersiz: süre 1-8760 saat, ilan 1-450, mesafe 2-128.");
        amount(getConfig().getString("shop.maximum-price"),false);
        amount(getConfig().getString("starting-balance"),true);
        ConfigurationSection styles=getConfig().getConfigurationSection("styles");
        if(styles==null||styles.getKeys(false).isEmpty()||!styles.contains(defaultStyle()))
            throw new IllegalArgumentException("En az bir görünüm ve geçerli varsayılan görünüm gerekli.");
        for(String key:styles.getKeys(false)){
            if(!key.matches("[a-z0-9_-]{1,24}"))throw new IllegalArgumentException("Görünüm anahtarı geçersiz: "+key);
            ConfigurationSection s=styles.getConfigurationSection(key);
            for(String field:List.of("canopy","base","helmet")){
                Material m=Material.matchMaterial(s.getString(field,""));
                if(m==null || m.isAir() || (field.equals("helmet")?!m.isItem():!m.isBlock()))
                    throw new IllegalArgumentException("Geçersiz materyal: "+key+"."+field);
            }
            if(!s.getString("color","").matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Renk #RRGGBB olmalı: "+key);
        }
    }
    String defaultStyle(){return getConfig().getString("shop.default-style","yesil");}
    String money(long cents){return String.format(Locale.forLanguageTag("tr-TR"),"%,.2f",cents/100.0)+" "+getConfig().getString("currency-name","Pazar Parası");}
    static long amount(String text,boolean zero) {
        try{
            BigDecimal n=new BigDecimal(text.replace(',','.'));
            long value=n.setScale(2,RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
            if(value<(zero?0:1)||value>Store.MAX_MONEY)throw new ArithmeticException();
            return value;
        }catch(Exception e){throw new IllegalArgumentException("Pozitif ve en fazla 2 ondalıklı tutar yaz. Örnek: 125.50");}
    }
    void tell(CommandSender p,String message){p.sendMessage("§a§lм §r§8| §7"+message);}
    void requireUse(Player p){if(!p.hasPermission("muropazar.use"))throw new IllegalArgumentException("Bu işlem için yetkin yok.");}
    void run(CommandSender sender,Action action){
        try{
            if(sender instanceof Player p)requireUse(p);
            action.run();
        }catch(IllegalArgumentException e){tell(sender,"§c"+e.getMessage());}
        catch(Exception e){getLogger().log(Level.SEVERE,"İşlem başarısız: "+sender.getName(),e);tell(sender,"§cİşlem tamamlanamadı. Tekrar denemeden önce /pazar kutu ve bakiyeni kontrol et. Yönetici günlükleri incelemeli.");}
    }
    boolean acceptClick(Player p){
        long now=System.currentTimeMillis(),before=clickTimes.getOrDefault(p.getUniqueId(),0L);
        if(now-before<250)return false;
        clickTimes.put(p.getUniqueId(),now);return true;
    }
    void prompt(Player p,String message,Input input){
        p.closeInventory();prompts.put(p.getUniqueId(),new Prompt(System.currentTimeMillis()+60000,input));
        tell(p,message+" §8(60 saniye; vazgeçmek için iptal)");
    }
    @EventHandler(priority=EventPriority.LOWEST) public void chat(AsyncPlayerChatEvent e){
        Prompt prompt=prompts.remove(e.getPlayer().getUniqueId());
        if(prompt==null)return;
        e.setCancelled(true);
        String message=e.getMessage().trim();
        Bukkit.getScheduler().runTask(this,()->{
            Player p=e.getPlayer();if(!p.isOnline())return;
            if(prompt.expires()<System.currentTimeMillis()){tell(p,"§cGiriş süresi doldu.");return;}
            if(message.equalsIgnoreCase("iptal")){tell(p,"İşlem iptal edildi.");return;}
            run(p,()->prompt.action().accept(message));
        });
    }
    @EventHandler public void join(PlayerJoinEvent e){run(e.getPlayer(),()->joinWallet(e.getPlayer()));}
    @EventHandler public void quit(PlayerQuitEvent e){prompts.remove(e.getPlayer().getUniqueId());clickTimes.remove(e.getPlayer().getUniqueId());}
    private void joinWallet(Player p)throws Exception{
        store.wallet(p.getUniqueId(),p.getName(),amount(getConfig().getString("starting-balance"),true));
        if(!store.mailbox(p.getUniqueId()).isEmpty())tell(p,"Teslimat kutunda eşya var: §e/pazar kutu");
    }
    Shop mine(Player p)throws Exception{
        Shop s=store.owned(p.getUniqueId());
        if(s==null||s.expired())throw new IllegalArgumentException("Aktif pazarın yok. /pazar kur");
        return s;
    }
    String cleanName(String name){
        String clean=ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',name)).strip();
        if(clean.length()<2||clean.length()>28||clean.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Pazar adı 2-28 karakter olmalı.");
        return clean;
    }
    void create(Player p,String name)throws Exception{
        requireUse(p);
        if(!p.hasPermission("muropazar.create"))throw new IllegalArgumentException("Pazar kurma yetkin yok.");
        expire();
        if(store.owned(p.getUniqueId())!=null)throw new IllegalArgumentException("Zaten bir pazarın var.");
        Location l=p.getLocation().clone(); l.setX(l.getBlockX()+.5);l.setY(l.getBlockY());l.setZ(l.getBlockZ()+.5);
        if(l.getY()<l.getWorld().getMinHeight()+1||l.getY()>l.getWorld().getMaxHeight()-4)throw new IllegalArgumentException("Bu yükseklikte pazar kuramazsın.");
        if(!l.clone().subtract(0,1,0).getBlock().getType().isSolid())throw new IllegalArgumentException("Sağlam bir zeminde durmalısın.");
        for(int y=0;y<3;y++)if(!l.clone().add(0,y,0).getBlock().isPassable())throw new IllegalArgumentException("Tezgâh için en az 3 blok boşluk gerekli.");
        if(!l.getWorld().getWorldBorder().isInside(l))throw new IllegalArgumentException("Dünya sınırı dışında pazar kurulamaz.");
        if(!p.hasPermission("muropazar.bypass")){
            List<String> worlds=getConfig().getStringList("shop.allowed-worlds");
            if(!worlds.isEmpty()&&!worlds.contains(l.getWorld().getName()))throw new IllegalArgumentException("Bu dünyada pazar kurmak yasak.");
            Location spawn=l.getWorld().getSpawnLocation();
            int protect=Bukkit.getSpawnRadius();
            if(protect>0&&Math.abs(l.getX()-spawn.getX())<=protect&&Math.abs(l.getZ()-spawn.getZ())<=protect)throw new IllegalArgumentException("Spawn koruması içinde pazar kurulamaz.");
            double minimum=getConfig().getDouble("shop.minimum-distance");
            for(Shop s:store.shops()){
                Location other=s.location();
                if(other!=null&&other.getWorld().equals(l.getWorld())&&other.distanceSquared(l)<minimum*minimum)
                    throw new IllegalArgumentException("Başka bir pazara çok yakınsın.");
            }
        }
        Shop s=new Shop(UUID.randomUUID().toString(),p.getUniqueId(),p.getName(),cleanName(name),
            l.getWorld().getName(),l.getX(),l.getY(),l.getZ(),l.getYaw(),defaultStyle(),System.currentTimeMillis()+getConfig().getLong("shop.duration-hours")*3600000);
        store.create(s);displays.update(s);
        tell(p,"§aPazarın kuruldu. Elindeki eşya yığınını satmak için /pazar sat <fiyat>");
        p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_PLING,1f,1.4f);
        menus.shop(p,s.id(),0);
    }
    void sell(Player p,String raw)throws Exception{
        Shop s=mine(p);
        long price=amount(raw,false);
        if(price>amount(getConfig().getString("shop.maximum-price"),false))throw new IllegalArgumentException("Fiyat yapılandırılmış sınırın üzerinde.");
        ItemStack item=p.getInventory().getItemInMainHand().clone();
        if(item.getType().isAir()||item.getAmount()<1)throw new IllegalArgumentException("Satacağın eşyayı elinde tut.");
        String name=item.hasItemMeta()&&item.getItemMeta().hasDisplayName()?ChatColor.stripColor(item.getItemMeta().getDisplayName()):item.getType().name();
        long id=store.reserve(s,name,Models.encode(item),price,getConfig().getInt("shop.max-listings"));
       
        p.getInventory().setItemInMainHand(null);
        p.saveData();
        store.activate(id);
        tell(p,"§a"+item.getAmount()+"x "+name+" satışa çıktı: "+money(price)+" §7(yığının tamamı).");
        menus.shop(p,s.id(),0);
    }
    void claim(Player p,long id)throws Exception{

        if(p.getInventory().firstEmpty()<0)throw new IllegalArgumentException("Envanterinde en az bir boş alan aç.");
        Mail m=store.beginClaim(p.getUniqueId(),id);
        ItemStack item;
        try{item=m.item();}catch(Exception e){store.resetClaim(p.getUniqueId(),id);throw e;}
        Map<Integer,ItemStack> left=p.getInventory().addItem(item);
        if(!left.isEmpty())throw new IllegalStateException("Teslimat kısmi kaldı. Kayıt CLAIMING; yönetici incelemeli.");
        p.saveData();
        store.finishClaim(p.getUniqueId(),id);
        p.playSound(p.getLocation(),Sound.ENTITY_ITEM_PICKUP,1f,1f);
    }
    void reprice(Player p,long id,String value)throws Exception{
        long price=amount(value,false);
        if(price>amount(getConfig().getString("shop.maximum-price"),false))throw new IllegalArgumentException("Fiyat sınırın üzerinde.");
        store.reprice(p.getUniqueId(),id,price);tell(p,"§aFiyat güncellendi.");
        menus.shop(p,mine(p).id(),0);
    }
    void buy(Player p,long id,long expectedPrice)throws Exception{
        store.buy(p.getUniqueId(),id,expectedPrice);
        tell(p,"§aSatın alındı. Eşyanı §e/pazar kutu §aile teslim al.");
        p.playSound(p.getLocation(),Sound.ENTITY_EXPERIENCE_ORB_PICKUP,1f,1f);
    }
    void remove(Player p)throws Exception{
        Shop s=mine(p);
        store.remove(s,"Pazar kaldırıldı",p.getUniqueId());displays.remove(s.id());
        tell(p,"Pazar kaldırıldı. Satılmayan ürünler §e/pazar kutu §7içinde.");
    }
    void rename(Player p,String name)throws Exception{
        Shop s=mine(p);store.rename(s,cleanName(name));displays.update(store.shop(s.id()));
        tell(p,"§aPazar adı değiştirildi.");menus.shop(p,s.id(),0);
    }
    void changeStyle(Player p,String style)throws Exception{
        if(!getConfig().isConfigurationSection("styles."+style))throw new IllegalArgumentException("Böyle bir görünüm yok.");
        Shop s=mine(p);store.style(s,style);displays.update(store.shop(s.id()));
        tell(p,"§aPazar görünümü değiştirildi.");menus.shop(p,s.id(),0);
    }
    private void expire()throws Exception{
        for(Shop s:store.shops())if(s.expired()){store.remove(s,"Pazar süresi doldu",s.owner());if(displays!=null)displays.remove(s.id());}
    }
    boolean bankEnabled(){return vault!=null;}
    void bank(Player p,String raw,boolean intoWallet)throws Exception{
        if(vault==null)throw new IllegalArgumentException("Vault transferi kapalı. Yönetici config.yml üzerinden etkinleştirebilir.");
        long value=amount(raw,false);vault.validate(value);
        long id=store.beginBank(p.getUniqueId(),value,intoWallet?"IN":"OUT");

        String error=vault.transfer(p,value,intoWallet);
        store.finishBank(id,error==null);
        if(error!=null)throw new IllegalArgumentException(error);
        tell(p,"§a"+money(value)+(intoWallet?" pazar cüzdanına yatırıldı.":" sunucu hesabına çekildi."));
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        run(sender,()->dispatch(sender,args));return true;
    }
    private void dispatch(CommandSender sender,String[] args)throws Exception{
        String cmd=args.length==0?"menu":args[0].toLowerCase(Locale.ROOT);
        if(List.of("para","incele","kurtar","yoneticikaldir").contains(cmd)){
            if(!sender.hasPermission("muropazar.admin"))throw new IllegalArgumentException("Yönetici yetkisi gerekli.");
            UUID actor=sender instanceof Player p?p.getUniqueId():new UUID(0,0);
            switch(cmd){
                case "para" -> {
                    if(args.length!=3)throw new IllegalArgumentException("/pazar para <çevrimiçi oyuncu> <tutar>");
                    Player target=Bukkit.getPlayerExact(args[1]);
                    if(target==null)throw new IllegalArgumentException("Oyuncu çevrimiçi olmalı.");
                    store.adminCredit(actor,target.getUniqueId(),amount(args[2],false));
                    tell(sender,"§aBakiye eklendi.");tell(target,"§aPazar bakiyene "+money(amount(args[2],false))+" eklendi.");
                }
                case "incele" -> {
                    List<String> pending=store.pending();tell(sender,pending.isEmpty()?"Bekleyen belirsiz işlem yok.":"Bekleyen işlemler (kurtarma kılavuzunu oku):");
                    for(String line:pending)tell(sender,line);
                }
                case "kurtar" -> {
                    if(args.length!=4||!List.of("tamam","iptal").contains(args[3]))throw new IllegalArgumentException("/pazar kurtar <ilan|kutu|banka> <id> <tamam|iptal>");
                    long id=Long.parseLong(args[2]);boolean complete=args[3].equals("tamam");
                    if(args[1].equals("banka")){store.finishBank(id,complete);store.audit(actor,"ADMIN_BANK_RECOVER",id+":"+complete);}
                    else store.recover(actor,args[1],id,complete);
                    tell(sender,"§aİşlem çözümlendi.");
                }
                case "yoneticikaldir" -> {
                    if(args.length!=2)throw new IllegalArgumentException("/pazar yoneticikaldir <pazar-id>");
                    Shop s=store.shop(args[1]);if(s==null)throw new IllegalArgumentException("Pazar bulunamadı.");
                    store.remove(s,"Yönetici pazarı kaldırdı",actor);displays.remove(s.id());tell(sender,"§aPazar kaldırıldı; eşyalar sahibinin kutusunda.");
                }
            }return;
        }
        if(!(sender instanceof Player p))throw new IllegalArgumentException("Oyuncu komutları oyun içinden kullanılmalı. Yönetici: para, incele, kurtar, yoneticikaldir.");
        switch(cmd){
            case "menu" -> menus.home(p);
            case "kur" -> create(p,args.length>1?String.join(" ",Arrays.copyOfRange(args,1,args.length)):p.getName()+" Pazarı");
            case "sat" -> {if(args.length!=2)throw new IllegalArgumentException("/pazar sat <yığının toplam fiyatı>");sell(p,args[1]);}
            case "pazarim" -> menus.shop(p,mine(p).id(),0);
            case "liste" -> menus.shops(p,0);
            case "ara" -> {if(args.length<2)prompt(p,"Aradığın eşyanın adını yaz:",q->menus.search(p,q,0));else menus.search(p,String.join(" ",Arrays.copyOfRange(args,1,args.length)),0);}
            case "isim" -> {if(args.length<2)prompt(p,"Yeni pazar adını yaz:",n->rename(p,n));else rename(p,String.join(" ",Arrays.copyOfRange(args,1,args.length)));}
            case "gorunum" -> {if(args.length<2)menus.styles(p,0);else changeStyle(p,args[1]);}
            case "kaldir" -> menus.confirmRemove(p);
            case "kutu" -> menus.mailbox(p,0);
            case "bakiye" -> menus.wallet(p);
            case "yatir","cek" -> {if(args.length!=2)throw new IllegalArgumentException("/pazar "+cmd+" <tutar>");bank(p,args[1],cmd.equals("yatir"));}
            case "yardim" -> {
                tell(p,"§a/muro §7ve §a/pazar §7aynı menüyü açar.");
                tell(p,"§e/pazar kur [isim] §7• bulunduğun yerde pazar kur.");
                tell(p,"§e/pazar sat <fiyat> §7• elindeki yığının tamamını satışa koy.");
                tell(p,"§e/pazar pazarim | liste | ara <eşya> | gorunum | isim <ad>");
                tell(p,"§e/pazar kaldir | kutu | bakiye | yatir <tutar> | cek <tutar>");
                tell(p,"Satın alınan ve iade edilen ürünler §e/pazar kutu §7içindedir.");
            }
            default -> throw new IllegalArgumentException("Bilinmeyen komut. /pazar yardim");
        }
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command c,String alias,String[] args){
        if(args.length==1){
            List<String> words=new ArrayList<>(List.of("kur","sat","pazarim","liste","ara","isim","gorunum","kaldir","kutu","bakiye","yatir","cek","yardim"));
            if(sender.hasPermission("muropazar.admin"))words.addAll(List.of("para","incele","kurtar","yoneticikaldir"));
            return words.stream().filter(x->x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if(args.length==2&&args[0].equalsIgnoreCase("gorunum"))return getConfig().getConfigurationSection("styles").getKeys(false).stream().filter(x->x.startsWith(args[1])).toList();
        return List.of();
    }
}
