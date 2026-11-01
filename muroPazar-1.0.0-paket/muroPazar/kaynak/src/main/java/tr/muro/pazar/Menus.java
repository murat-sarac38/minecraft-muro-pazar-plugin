package tr.muro.pazar;

import static tr.muro.pazar.Models.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

final class Menus implements Listener {
    private final MuroPazar plugin;
    @FunctionalInterface interface Click { void apply(Player p) throws Exception; }
    static final class Menu implements InventoryHolder {
        final UUID viewer;
        final Inventory inventory;
        final Map<Integer,Click> actions=new HashMap<>();
        Menu(Player p,String title) { viewer=p.getUniqueId();inventory=Bukkit.createInventory(this,54,title); }
        @Override public Inventory getInventory(){return inventory;}
    }
    Menus(MuroPazar plugin){this.plugin=plugin;}
    private Menu menu(Player p,String title){
        Menu m=new Menu(p,"§2§lмuro §8• §r"+title);
        for(int i=45;i<54;i++)put(m,i,icon(Material.GRAY_STAINED_GLASS_PANE," "),null);
        put(m,48,icon(Material.COMPASS,"§aAna menü"),this::home);
        put(m,53,icon(Material.BARRIER,"§cKapat"),Player::closeInventory);
        return m;
    }
    static ItemStack icon(Material material,String name,String... lore){
        ItemStack item=new ItemStack(material);ItemMeta meta=item.getItemMeta();
        meta.setDisplayName(name);meta.setLore(Arrays.asList(lore));item.setItemMeta(meta);return item;
    }
    private ItemStack description(ItemStack original,String... lines){
        ItemStack item=original.clone();ItemMeta meta=item.getItemMeta();
        List<String> lore=meta.hasLore()?new ArrayList<>(meta.getLore()):new ArrayList<>();
        lore.add(" ");lore.addAll(Arrays.asList(lines));meta.setLore(lore);item.setItemMeta(meta);return item;
    }
    private void put(Menu m,int slot,ItemStack item,Click action){m.inventory.setItem(slot,item);if(action!=null)m.actions.put(slot,action);}
    private void open(Player p,Menu m){p.openInventory(m.inventory);}
    private int clamp(int page,int size){return Math.max(0,Math.min(page,Math.max(0,(size-1)/45)));}
    private void pages(Menu m,int page,int count,java.util.function.IntFunction<Click> action){
        if(page>0)put(m,45,icon(Material.ARROW,"§eÖnceki sayfa"),action.apply(page-1));
        if((page+1)*45<count)put(m,46,icon(Material.ARROW,"§eSonraki sayfa"),action.apply(page+1));
        put(m,47,icon(Material.PAPER,"§7Sayfa "+(page+1)+" / "+Math.max(1,(count+44)/45)),null);
    }
    void home(Player p)throws Exception{
        Menu m=menu(p,"Oyuncu Pazarı");
        Shop own=plugin.store.owned(p.getUniqueId());
        put(m,10,icon(Material.CHEST,"§a§lTüm pazarlar","§7Oyuncuların tezgâhlarını gez.","§eTıkla ve görüntüle."),q->shops(q,0));
        put(m,12,icon(Material.SPYGLASS,"§b§lÜrün ara","§7Eşya adı veya materyal ile ara.","§7Örnek: diamond, elmas, kılıç"),q->plugin.prompt(q,"Aradığın eşyanın adını yaz:",word->search(q,word,0)));
        put(m,14,icon(Material.BARREL,"§6§lTeslimat kutusu","§7Satın aldıkların ve iade edilenler.","§e"+plugin.store.mailbox(p.getUniqueId()).size()+" ürün bekliyor."),q->mailbox(q,0));
        put(m,16,icon(Material.GOLD_INGOT,"§e§lCüzdan","§f"+plugin.money(plugin.store.balance(p.getUniqueId())),"§7Pazar bakiyeni görüntüle."),this::wallet);
        if(own!=null&&!own.expired()){
            put(m,30,icon(Material.CRAFTING_TABLE,"§a§lPazarım","§f"+own.name(),"§7İlanlar, fiyatlar ve görünüm."),q->shop(q,own.id(),0));
        }else put(m,30,icon(Material.EMERALD_BLOCK,"§a§lPazar kur","§7Bulunduğun yerde tezgâh aç.","§7Süre: "+plugin.getConfig().getLong("shop.duration-hours")+" saat"),q->plugin.prompt(q,"Pazarına bir ad ver:",name->plugin.create(q,name)));
        put(m,32,icon(Material.BOOK,"§fNasıl kullanılır?","§7Eşyayı elinde tut ve fiyat belirle.","§e/pazar sat 250","§7Fiyat, yığının tamamı içindir.","§7Alıcı eşyayı teslimat kutusundan alır."),q->{q.closeInventory();q.performCommand("muropazar yardim");});
        open(p,m);
    }
    void shops(Player p,int requested)throws Exception{
        List<Shop> list=plugin.store.shops().stream().filter(s->!s.expired()).toList();int page=clamp(requested,list.size());
        Menu m=menu(p,"Tüm Pazarlar");
        for(int i=page*45;i<Math.min((page+1)*45,list.size());i++){
            Shop s=list.get(i);
            List<String> lore=new ArrayList<>(List.of("§7Satıcı: §f"+s.ownerName(),"§7Dünya: §f"+s.world(),
                "§7Konum: §f"+(int)s.x()+", "+(int)s.y()+", "+(int)s.z(),"§7Kalan süre: §e"+remaining(s),"§aÜrünleri görüntülemek için tıkla."));
            if(p.hasPermission("muropazar.admin"))lore.add("§8ID: "+s.id());
            put(m,i-page*45,icon(Material.CHEST,"§a"+s.name(),lore.toArray(String[]::new)),q->shop(q,s.id(),0));
        }
        if(list.isEmpty())put(m,22,icon(Material.CHEST,"§7Henüz pazar yok.","§eİlk pazarı sen kurabilirsin."),null);
        pages(m,page,list.size(),n->q->shops(q,n));open(p,m);
    }
    private String remaining(Shop s){
        long minutes=Math.max(0,(s.expires()-System.currentTimeMillis())/60000);
        return minutes/60+" sa "+minutes%60+" dk";
    }
    void shop(Player p,String id,int requested)throws Exception{
        Shop s=plugin.store.shop(id);
        if(s==null||s.expired())throw new IllegalArgumentException("Pazar artık açık değil.");
        List<Listing> listings=plugin.store.listings(id);int page=clamp(requested,listings.size());
        Menu m=menu(p,s.name());
        boolean owner=s.owner().equals(p.getUniqueId());
        for(int i=page*45;i<Math.min((page+1)*45,listings.size());i++){
            Listing l=listings.get(i);
            put(m,i-page*45,description(l.item(),"§eToplam: "+plugin.money(l.price()),"§7İlan #"+l.id(),owner?"§aFiyatı değiştir / ilanı kaldır":"§aSatın alma ekranını aç"),q->listing(q,l.id()));
        }
        if(listings.isEmpty())put(m,22,icon(Material.HOPPER,"§7Pazar şu an boş.",owner?"§7Elinde eşya tutarak satışa ekle.":"§7Satıcı henüz ürün eklememiş."),null);
        pages(m,page,listings.size(),n->q->shop(q,id,n));
        if(owner){
            put(m,49,icon(Material.EMERALD,"§aEşya sat","§7Elindeki yığının tamamı satışa çıkar.","§7Fiyatı sohbete yazacaksın."),q->plugin.prompt(q,"Elindeki yığının toplam fiyatını yaz:",price->plugin.sell(q,price)));
            put(m,50,icon(Material.NAME_TAG,"§eAdı değiştir","§7Kalan süre: "+remaining(s)),q->plugin.prompt(q,"Yeni pazar adını yaz:",name->plugin.rename(q,name)));
            put(m,51,icon(Material.LEATHER_CHESTPLATE,"§dGörünüm değiştir"),q->styles(q,0));
            put(m,52,icon(Material.TNT,"§cPazarı kaldır","§7Satılmayan eşyalar kutuna iade edilir."),this::confirmRemove);
        }else put(m,49,icon(Material.BARREL,"§6Teslimat kutum"),q->mailbox(q,0));
        open(p,m);
    }
    void listing(Player p,long id)throws Exception{
        Listing l=plugin.store.listing(id);
        if(l==null||!l.state().equals("ACTIVE"))throw new IllegalArgumentException("Bu ilan artık aktif değil.");
        Shop s=plugin.store.shop(l.shop());
        if(s==null||s.expired())throw new IllegalArgumentException("Pazarın süresi dolmuş.");
        Menu m=menu(p,l.owner().equals(p.getUniqueId())?"İlanı Yönet":"Satın Almayı Onayla");
        put(m,13,description(l.item(),"§eYığının tamamı: "+plugin.money(l.price()),"§7Satıcı: "+s.ownerName(),"§7İlan #"+id),null);
        if(l.owner().equals(p.getUniqueId())){
            put(m,29,icon(Material.GOLD_INGOT,"§eFiyatı değiştir"),q->plugin.prompt(q,"Yeni toplam fiyatı yaz:",value->plugin.reprice(q,id,value)));
            put(m,33,icon(Material.RED_DYE,"§cİlanı kaldır","§7Ürün teslimat kutuna iade edilir."),q->{
                plugin.store.withdraw(q.getUniqueId(),id);plugin.tell(q,"İlan kaldırıldı. Ürün §e/pazar kutu §7içinde.");shop(q,l.shop(),0);
            });
        }else{
            put(m,29,icon(Material.LIME_DYE,"§a§lSatın al","§e"+plugin.money(l.price())+" öde.","§7Eşya teslimat kutuna gidecek."),q->{
                plugin.buy(q,id,l.price());mailbox(q,0);
            });
            put(m,33,icon(Material.RED_DYE,"§cVazgeç"),q->shop(q,l.shop(),0));
        }
        put(m,49,icon(Material.ARROW,"§7Pazara dön"),q->shop(q,l.shop(),0));open(p,m);
    }
    void search(Player p,String query,int requested)throws Exception{
        if(query.length()>64||query.isBlank())throw new IllegalArgumentException("Arama 1-64 karakter olmalı.");
        String term=normalize(query);
        List<Listing> matches=plugin.store.listings(null).stream().filter(l->normalize(l.name()+" "+l.item().getType().name()).contains(term)).toList();
        int page=clamp(requested,matches.size());Menu m=menu(p,"Arama: "+query.substring(0,Math.min(20,query.length())));
        for(int i=page*45;i<Math.min((page+1)*45,matches.size());i++){
            Listing l=matches.get(i);
            put(m,i-page*45,description(l.item(),"§e"+plugin.money(l.price()),"§7İlan #"+l.id(),"§aİncelemek için tıkla."),q->listing(q,l.id()));
        }
        if(matches.isEmpty())put(m,22,icon(Material.SPYGLASS,"§7Sonuç bulunamadı.","§7Materyal adıyla da arayabilirsin.","§7Örnek: diamond, stone, sword"),null);
        pages(m,page,matches.size(),n->q->search(q,query,n));
        put(m,49,icon(Material.SPYGLASS,"§bYeni arama"),q->plugin.prompt(q,"Aradığın eşyanın adını yaz:",word->search(q,word,0)));
        open(p,m);
    }
    private String normalize(String raw){
        String text=raw.toLowerCase(Locale.forLanguageTag("tr-TR")).replace('ı','i').replace('ş','s').replace('ğ','g').replace('ü','u').replace('ö','o').replace('ç','c');
        for(var e:Map.ofEntries(Map.entry("elmas","diamond"),Map.entry("kilic","sword"),Map.entry("kazma","pickaxe"),Map.entry("zumrut","emerald"),
            Map.entry("demir","iron"),Map.entry("altin","gold"),Map.entry("tas","stone"),Map.entry("balta","axe"),Map.entry("yay","bow"),
            Map.entry("zirh","chestplate"),Map.entry("netherit","netherite"),Map.entry("komur","coal")).entrySet())text=text.replace(e.getKey(),e.getValue());
        return text.replace(' ','_');
    }
    void mailbox(Player p,int requested)throws Exception{
        List<Mail> list=plugin.store.mailbox(p.getUniqueId());int page=clamp(requested,list.size());
        Menu m=menu(p,"Teslimat Kutusu");
        for(int i=page*45;i<Math.min((page+1)*45,list.size());i++){
            Mail mail=list.get(i);
            put(m,i-page*45,description(mail.item(),"§7"+mail.reason(),"§aTeslim almak için tıkla.","§8Kutu #"+mail.id()),q->{plugin.claim(q,mail.id());mailbox(q,page);});
        }
        if(list.isEmpty())put(m,22,icon(Material.BARREL,"§7Kutun boş.","§7Satın aldıkların ve iadelerin burada görünür."),null);
        pages(m,page,list.size(),n->q->mailbox(q,n));
        put(m,49,icon(Material.CLOCK,"§eYenile"),q->mailbox(q,page));open(p,m);
    }
    void styles(Player p,int requested)throws Exception{
        plugin.mine(p);
        List<String> styles=new ArrayList<>(plugin.getConfig().getConfigurationSection("styles").getKeys(false));int page=clamp(requested,styles.size());
        Menu m=menu(p,"Tezgâh Görünümü");
        for(int i=page*45;i<Math.min((page+1)*45,styles.size());i++){
            String key=styles.get(i);ConfigurationSection style=plugin.getConfig().getConfigurationSection("styles."+key);
            put(m,i-page*45,icon(Material.valueOf(style.getString("canopy")),"§a"+style.getString("name"),"§7Tezgâhını bu görünüme geçir.","§8"+key),q->plugin.changeStyle(q,key));
        }
        pages(m,page,styles.size(),n->q->styles(q,n));open(p,m);
    }
    void confirmRemove(Player p)throws Exception{
        Shop s=plugin.mine(p);Menu m=menu(p,"Pazarı Kaldır?");
        put(m,13,icon(Material.CHEST,"§e"+s.name(),"§7Bu pazar kaldırılacak.","§7Satılmayan ürünler kutuna iade edilir.","§7İşlem sonrası yeni pazar kurabilirsin."),null);
        put(m,29,icon(Material.RED_DYE,"§cEvet, pazarı kaldır"),q->{
            // Do not let a stale confirmation remove a newly created replacement shop.
            if(!plugin.mine(q).id().equals(s.id()))throw new IllegalArgumentException("Pazar değişti; ekranı yeniden aç.");
            plugin.remove(q);mailbox(q,0);
        });
        put(m,33,icon(Material.LIME_DYE,"§aHayır, geri dön"),q->shop(q,s.id(),0));open(p,m);
    }
    void wallet(Player p)throws Exception{
        Menu m=menu(p,"Pazar Cüzdanı");
        put(m,13,icon(Material.GOLD_BLOCK,"§e"+plugin.money(plugin.store.balance(p.getUniqueId())),"§7Satış kazançların anında buraya eklenir.","§7Bu bakiye sunucu bakiyesinden ayrıdır."),null);
        if(plugin.bankEnabled()){
            put(m,29,icon(Material.LIME_DYE,"§aPara yatır","§7Sunucu bakiyenden pazar cüzdanına."),q->plugin.prompt(q,"Yatırılacak tutarı yaz:",value->{plugin.bank(q,value,true);wallet(q);}));
            put(m,33,icon(Material.ORANGE_DYE,"§6Para çek","§7Pazar cüzdanından sunucu bakiyene."),q->plugin.prompt(q,"Çekilecek tutarı yaz:",value->{plugin.bank(q,value,false);wallet(q);}));
        }else put(m,31,icon(Material.BOOK,"§7Yerel pazar ekonomisi","§7Vault transferleri kapalı.","§7Yönetici /pazar para ile bakiye ekler.","§7Site kredisi bağlantısı kurulu değil."),null);
        open(p,m);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e){
        if(!(e.getView().getTopInventory().getHolder() instanceof Menu menu))return;
        e.setCancelled(true); // Includes number keys, shift, double click, drops and offhand swaps.
        if(!(e.getWhoClicked() instanceof Player p)||!menu.viewer.equals(p.getUniqueId()))return;
        if(e.getRawSlot()<0||e.getRawSlot()>=54||!(e.isLeftClick()||e.isRightClick())||!plugin.acceptClick(p))return;
        Click action=menu.actions.get(e.getRawSlot());
        if(action!=null)Bukkit.getScheduler().runTask(plugin,()->{
            if(!p.isOnline()||p.getOpenInventory().getTopInventory()!=menu.inventory)return;
            plugin.run(p,()->action.apply(p));
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent e){
        if(e.getView().getTopInventory().getHolder() instanceof Menu)e.setCancelled(true);
    }
}
