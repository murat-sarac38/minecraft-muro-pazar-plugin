package tr.muro.pazar;

import static tr.muro.pazar.Models.*;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;


final class Store implements AutoCloseable {
    static final long MAX_MONEY = 9_000_000_000_000L;
    private final Connection db;
    interface Work<T> { T run() throws SQLException; }

    Store(Path file) throws Exception {
        Class.forName("org.sqlite.JDBC");
        db = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
        try (Statement s = db.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=FULL");
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("PRAGMA busy_timeout=5000");
            s.execute("CREATE TABLE IF NOT EXISTS wallets(owner TEXT PRIMARY KEY, name TEXT NOT NULL, balance INTEGER NOT NULL CHECK(balance>=0))");
            s.execute("CREATE TABLE IF NOT EXISTS shops(id TEXT PRIMARY KEY,owner TEXT NOT NULL UNIQUE,owner_name TEXT NOT NULL,name TEXT NOT NULL,world TEXT NOT NULL,x REAL,y REAL,z REAL,yaw REAL,style TEXT NOT NULL,expires INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS listings(id INTEGER PRIMARY KEY AUTOINCREMENT,shop TEXT NOT NULL,owner TEXT NOT NULL,name TEXT NOT NULL,data TEXT NOT NULL,price INTEGER NOT NULL CHECK(price>0),state TEXT NOT NULL)");
            s.execute("CREATE INDEX IF NOT EXISTS listing_shop ON listings(shop,state)");
            s.execute("CREATE TABLE IF NOT EXISTS mail(id INTEGER PRIMARY KEY AUTOINCREMENT,owner TEXT NOT NULL,data TEXT NOT NULL,reason TEXT NOT NULL,state TEXT NOT NULL DEFAULT 'READY')");
            s.execute("CREATE INDEX IF NOT EXISTS mail_owner ON mail(owner,state)");
            s.execute("CREATE TABLE IF NOT EXISTS bank(id INTEGER PRIMARY KEY AUTOINCREMENT,owner TEXT NOT NULL,amount INTEGER NOT NULL,direction TEXT NOT NULL,state TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS audit(id INTEGER PRIMARY KEY AUTOINCREMENT,time INTEGER,actor TEXT,action TEXT,detail TEXT)");
        }
    }

    private <T> T tx(Work<T> work) throws SQLException {
        db.setAutoCommit(false);
        try {
            T result = work.run();
            db.commit();
            return result;
        } catch (SQLException | RuntimeException e) {
            db.rollback();
            throw e;
        } finally { db.setAutoCommit(true); }
    }
    private PreparedStatement query(String sql, Object... args) throws SQLException {
        PreparedStatement p = db.prepareStatement(sql);
        for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i] instanceof UUID ? args[i].toString() : args[i]);
        return p;
    }
    private int update(String sql, Object... args) throws SQLException {
        try (PreparedStatement p = query(sql, args)) { return p.executeUpdate(); }
    }
    private long insert(String sql, Object... args) throws SQLException {
        update(sql, args);
        try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT last_insert_rowid()")) {
            r.next();
            return r.getLong(1);
        }
    }
    void audit(UUID actor, String action, String detail) throws SQLException {
        update("INSERT INTO audit(time,actor,action,detail) VALUES(?,?,?,?)", System.currentTimeMillis(), actor, action, detail);
    }
    void wallet(UUID owner, String name, long initial) throws SQLException {
        update("INSERT INTO wallets(owner,name,balance) VALUES(?,?,?) ON CONFLICT(owner) DO UPDATE SET name=excluded.name", owner, name, initial);
    }
    long balance(UUID owner) throws SQLException {
        try (PreparedStatement p = query("SELECT balance FROM wallets WHERE owner=?", owner); ResultSet r = p.executeQuery()) {
            if (!r.next()) throw new IllegalArgumentException("Oyuncunun pazar cüzdanı yok.");
            return r.getLong(1);
        }
    }
    void credit(UUID owner, long amount) throws SQLException {
        if (amount < 0 || amount > MAX_MONEY) throw new IllegalArgumentException("Geçersiz para tutarı.");
        if (update("UPDATE wallets SET balance=balance+? WHERE owner=? AND balance<=?", amount, owner, MAX_MONEY - amount) != 1)
            throw new IllegalArgumentException("Cüzdan üst sınırına ulaşıldı.");
    }
    void debit(UUID owner, long amount) throws SQLException {
        if (amount <= 0 || amount > MAX_MONEY) throw new IllegalArgumentException("Geçersiz para tutarı.");
        if (update("UPDATE wallets SET balance=balance-? WHERE owner=? AND balance>=?", amount, owner, amount) != 1)
            throw new IllegalArgumentException("Pazar bakiyen yetersiz.");
    }
    void adminCredit(UUID actor, UUID owner, long amount) throws SQLException {
        tx(() -> { credit(owner, amount); audit(actor, "ADMIN_CREDIT", owner + ":" + amount); return null; });
    }
    List<Shop> shops() throws SQLException {
        List<Shop> list = new ArrayList<>();
        try (PreparedStatement p = query("SELECT * FROM shops ORDER BY expires"); ResultSet r = p.executeQuery()) {
            while (r.next()) list.add(readShop(r));
        }
        return list;
    }
    Shop shop(String id) throws SQLException {
        try (PreparedStatement p = query("SELECT * FROM shops WHERE id=?", id); ResultSet r = p.executeQuery()) { return r.next() ? readShop(r) : null; }
    }
    Shop owned(UUID owner) throws SQLException {
        try (PreparedStatement p = query("SELECT * FROM shops WHERE owner=?", owner); ResultSet r = p.executeQuery()) { return r.next() ? readShop(r) : null; }
    }
    private Shop readShop(ResultSet r) throws SQLException {
        return new Shop(r.getString("id"), UUID.fromString(r.getString("owner")), r.getString("owner_name"),
            r.getString("name"), r.getString("world"), r.getDouble("x"), r.getDouble("y"), r.getDouble("z"),
            r.getFloat("yaw"), r.getString("style"), r.getLong("expires"));
    }
    void create(Shop s) throws SQLException {
        tx(() -> {
            update("INSERT INTO shops VALUES(?,?,?,?,?,?,?,?,?,?,?)", s.id(), s.owner(), s.ownerName(), s.name(), s.world(), s.x(), s.y(), s.z(), s.yaw(), s.style(), s.expires());
            audit(s.owner(), "CREATE", s.id()); return null;
        });
    }
    void rename(Shop s, String name) throws SQLException { update("UPDATE shops SET name=? WHERE id=? AND owner=?", name, s.id(), s.owner()); }
    void style(Shop s, String style) throws SQLException { update("UPDATE shops SET style=? WHERE id=? AND owner=?", style, s.id(), s.owner()); }
    List<Listing> listings(String shop) throws SQLException {
        List<Listing> list = new ArrayList<>();
        String sql = shop == null
            ? "SELECT l.* FROM listings l JOIN shops s ON l.shop=s.id WHERE l.state='ACTIVE' AND s.expires>? ORDER BY l.id DESC"
            : "SELECT l.* FROM listings l JOIN shops s ON l.shop=s.id WHERE l.state='ACTIVE' AND s.expires>? AND l.shop=? ORDER BY l.id DESC";
        try (PreparedStatement p = shop == null ? query(sql, System.currentTimeMillis()) : query(sql, System.currentTimeMillis(), shop); ResultSet r = p.executeQuery()) {
            while (r.next()) list.add(readListing(r));
        }
        return list;
    }
    Listing listing(long id) throws SQLException {
        try (PreparedStatement p = query("SELECT * FROM listings WHERE id=?", id); ResultSet r = p.executeQuery()) { return r.next() ? readListing(r) : null; }
    }
    private Listing readListing(ResultSet r) throws SQLException {
        return new Listing(r.getLong("id"),r.getString("shop"),UUID.fromString(r.getString("owner")),r.getString("name"),r.getString("data"),r.getLong("price"),r.getString("state"));
    }
    long reserve(Shop s, String name, String data, long price, int max) throws SQLException {
        return tx(() -> {
            Shop current = shop(s.id());
            if (current == null || current.expired()) throw new IllegalArgumentException("Pazarın süresi dolmuş.");
            try (PreparedStatement p = query("SELECT count(*) FROM listings WHERE shop=? AND state IN ('ACTIVE','PENDING')", s.id()); ResultSet r = p.executeQuery()) {
                r.next();
                if (r.getInt(1) >= max) throw new IllegalArgumentException("Pazarındaki ilan sınırına ulaştın.");
            }
            return insert("INSERT INTO listings(shop,owner,name,data,price,state) VALUES(?,?,?,?,?,'PENDING')", s.id(),s.owner(),name,data,price);
        });
    }
    void activate(long id) throws SQLException { update("UPDATE listings SET state='ACTIVE' WHERE id=? AND state='PENDING'", id); }
    void reprice(UUID owner,long id,long price) throws SQLException {
        if(price<=0||price>MAX_MONEY)throw new IllegalArgumentException("Geçersiz fiyat.");
        if(update("UPDATE listings SET price=? WHERE id=? AND owner=? AND state='ACTIVE' AND shop IN (SELECT id FROM shops WHERE expires>?)",price,id,owner,System.currentTimeMillis())!=1)
            throw new IllegalArgumentException("Aktif ilan bulunamadı.");
    }
    void cancelReserve(long id) throws SQLException { update("DELETE FROM listings WHERE id=? AND state='PENDING'", id); }
    void buy(UUID buyer, long id, long expectedPrice) throws SQLException {
        tx(() -> {
            Listing l = listing(id);
            if (l == null || !l.state().equals("ACTIVE")) throw new IllegalArgumentException("Bu ürün artık satılık değil.");
            Shop s = shop(l.shop());
            if (s == null || s.expired()) throw new IllegalArgumentException("Pazarın süresi doldu.");
            if (buyer.equals(l.owner())) throw new IllegalArgumentException("Kendi ürününü satın alamazsın.");
            if(l.price()!=expectedPrice)throw new IllegalArgumentException("Satıcı fiyatı değiştirdi. Ürünü yeniden açıp fiyatı kontrol et.");
            debit(buyer,l.price());
            credit(l.owner(),l.price());
            update("UPDATE listings SET state='SOLD' WHERE id=?", id);
            insert("INSERT INTO mail(owner,data,reason) VALUES(?,?,?)",buyer,l.data(),"Satın alma #" + id);
            audit(buyer,"BUY",id + ":" + l.owner() + ":" + l.price());
            return null;
        });
    }
    void withdraw(UUID owner, long id) throws SQLException {
        tx(() -> {
            Listing l = listing(id);
            if (l == null || !l.state().equals("ACTIVE") || !l.owner().equals(owner)) throw new IllegalArgumentException("Bu ilan sana ait değil veya kapanmış.");
            update("UPDATE listings SET state='RETURNED' WHERE id=?",id);
            insert("INSERT INTO mail(owner,data,reason) VALUES(?,?,?)",owner,l.data(),"İlan kaldırıldı #" + id);
            audit(owner,"WITHDRAW",Long.toString(id)); return null;
        });
    }
    void remove(Shop s, String reason, UUID actor) throws SQLException {
        tx(() -> {
            update("INSERT INTO mail(owner,data,reason) SELECT owner,data,? FROM listings WHERE shop=? AND state='ACTIVE'",reason,s.id());
            update("UPDATE listings SET state='RETURNED' WHERE shop=? AND state='ACTIVE'",s.id());
            // PENDING items stay quarantined for manual recovery, never auto-refunded.
            update("DELETE FROM shops WHERE id=?",s.id());
            audit(actor,"REMOVE_SHOP",s.id() + ":" + reason); return null;
        });
    }
    List<Mail> mailbox(UUID owner) throws SQLException {
        List<Mail> list = new ArrayList<>();
        try (PreparedStatement p = query("SELECT * FROM mail WHERE owner=? AND state='READY' ORDER BY id",owner); ResultSet r = p.executeQuery()) {
            while (r.next()) list.add(readMail(r));
        }
        return list;
    }
    private Mail readMail(ResultSet r) throws SQLException { return new Mail(r.getLong("id"),UUID.fromString(r.getString("owner")),r.getString("data"),r.getString("reason"),r.getString("state")); }
    Mail beginClaim(UUID owner, long id) throws SQLException {
        return tx(() -> {
            Mail mail;
            try (PreparedStatement p=query("SELECT * FROM mail WHERE id=? AND owner=? AND state='READY'",id,owner);ResultSet r=p.executeQuery()) { mail=r.next()?readMail(r):null; }
            if (mail == null) throw new IllegalArgumentException("Ürün zaten alınmış veya sana ait değil.");
            update("UPDATE mail SET state='CLAIMING' WHERE id=?",id);
            return mail;
        });
    }
    void finishClaim(UUID owner, long id) throws SQLException {
        tx(() -> {
            update("DELETE FROM mail WHERE id=? AND owner=? AND state='CLAIMING'",id,owner);
            audit(owner,"CLAIM",Long.toString(id)); return null;
        });
    }
    void resetClaim(UUID owner, long id) throws SQLException { update("UPDATE mail SET state='READY' WHERE id=? AND owner=? AND state='CLAIMING'",id,owner); }
    long beginBank(UUID owner,long amount,String direction) throws SQLException {
        return tx(() -> {
            try (PreparedStatement p=query("SELECT 1 FROM bank WHERE owner=? AND state='PENDING'",owner);ResultSet r=p.executeQuery()) {
                if(r.next()) throw new IllegalArgumentException("Bekleyen banka işlemin var; yönetici incelemeli.");
            }
            if(direction.equals("OUT")) debit(owner,amount);
            else if (balance(owner) > MAX_MONEY - amount) throw new IllegalArgumentException("Cüzdan üst sınırı aşılıyor.");
            return insert("INSERT INTO bank(owner,amount,direction,state) VALUES(?,?,?,'PENDING')",owner,amount,direction);
        });
    }
    private Bank bank(long id) throws SQLException {
        try(PreparedStatement p=query("SELECT * FROM bank WHERE id=? AND state='PENDING'",id);ResultSet r=p.executeQuery()){
            return r.next()?new Bank(id,UUID.fromString(r.getString("owner")),r.getLong("amount"),r.getString("direction"),r.getString("state")):null;
        }
    }
    void finishBank(long id,boolean success) throws SQLException {
        tx(() -> {
            Bank b=bank(id);
            if(b==null) throw new IllegalArgumentException("Bekleyen banka işlemi yok.");
            if((success && b.direction().equals("IN"))||(!success && b.direction().equals("OUT"))) credit(b.owner(),b.amount());
            update("UPDATE bank SET state=? WHERE id=?",success?"DONE":"FAILED",id);
            audit(b.owner(),"BANK_"+(success?"DONE":"FAILED"),Long.toString(id));return null;
        });
    }
    List<String> pending() throws SQLException {
        List<String> out=new ArrayList<>();
        for(String table:List.of("listings","mail","bank")){
            String state=table.equals("mail")?"CLAIMING":"PENDING";
            try(PreparedStatement p=query("SELECT id,owner FROM "+table+" WHERE state=?",state);ResultSet r=p.executeQuery()){
                while(r.next()) out.add((table.equals("listings")?"ilan":table.equals("mail")?"kutu":"banka")+" #"+r.getLong("id")+" / "+r.getString("owner"));
            }
        }
        return out;
    }
    void recover(UUID actor,String type,long id,boolean complete) throws SQLException {
        tx(() -> {
            if(type.equals("ilan")){
                Listing l=listing(id);
                if(l==null || !l.state().equals("PENDING")) throw new IllegalArgumentException("Bekleyen ilan yok.");
                if(complete){

                    update("INSERT INTO mail(owner,data,reason) VALUES(?,?,?)",l.owner(),l.data(),"Yönetici kurtarma #" + id);
                }
                update("UPDATE listings SET state='RECOVERED' WHERE id=?",id);
            }else if(type.equals("kutu")){
                if(update(complete?"DELETE FROM mail WHERE id=? AND state='CLAIMING'":"UPDATE mail SET state='READY' WHERE id=? AND state='CLAIMING'",id)!=1)
                    throw new IllegalArgumentException("Bekleyen teslimat yok.");
            }else throw new IllegalArgumentException("Tür: ilan, kutu veya banka.");
            audit(actor,"RECOVER",type+":"+id+":"+complete);return null;
        });
    }
    @Override public void close() throws SQLException { db.close(); }
}
