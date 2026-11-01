package tr.muro.pazar;

import java.nio.file.*;
import java.util.UUID;
import static tr.muro.pazar.Models.*;

/** Standalone persistence/ledger checks; not a live Bukkit/server test. */
public final class StoreChecks {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private interface Attempt{void run()throws Exception;}
    private static void refused(Attempt action)throws Exception{
        try{action.run();}catch(IllegalArgumentException expected){return;}
        throw new AssertionError("Expected rejection");
    }
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("muro-ledger-");
        Path file=dir.resolve("pazar.db");
        UUID seller=UUID.randomUUID(),buyer=UUID.randomUUID();
        try{
            try(Store store=new Store(file)){
                store.wallet(seller,"Seller",0);store.wallet(buyer,"Buyer",10000);
                store.wallet(buyer,"Buyer",50000);
                check(store.balance(buyer)==10000,"Starting funds cannot be claimed twice");
                Shop shop=new Shop("shop",seller,"Seller","Shop","world",0,64,0,0,"yesil",System.currentTimeMillis()+60000);
                store.create(shop);
                long id=store.reserve(shop,"DIAMOND","test fixture; not deserialized",2500,45);
                check(store.listings("shop").isEmpty(),"Reserved items are quarantined");
                store.activate(id);
                refused(()->store.buy(seller,id,2500));
                refused(()->store.buy(buyer,id,9999));
                check(store.balance(buyer)==10000,"Stale prices cannot charge");
                store.buy(buyer,id,2500);
                check(store.balance(buyer)==7500&&store.balance(seller)==2500,"Balanced transfer");
                check(store.mailbox(buyer).size()==1,"Purchased item in mailbox");
                refused(()->store.buy(buyer,id,2500));
                check(store.balance(buyer)==7500,"Double buy cannot charge");
                long expensive=store.reserve(shop,"GOLD","fixture",9000,45);store.activate(expensive);
                refused(()->store.buy(buyer,expensive,9000));
                check(store.listing(expensive).state().equals("ACTIVE"),"Insufficient funds rollback");
                store.reprice(seller,expensive,7000);
                refused(()->store.buy(buyer,expensive,9000));
                store.remove(shop,"Expired",seller);
                check(store.mailbox(seller).size()==1,"Expiry returns unsold items only");
                check(store.shop("shop")==null,"Shop removed");
                Mail mail=store.mailbox(buyer).get(0);
                store.beginClaim(buyer,mail.id());
                refused(()->store.beginClaim(buyer,mail.id()));
                check(store.pending().size()==1,"Interrupted delivery visible");
                store.recover(seller,"kutu",mail.id(),false);
                check(store.mailbox(buyer).size()==1,"Uncompleted delivery can be reset");
                store.beginClaim(buyer,mail.id());store.finishClaim(buyer,mail.id());
                check(store.mailbox(buyer).isEmpty(),"Delivered once");
                long bank=store.beginBank(buyer,1000,"OUT");
                check(store.balance(buyer)==6500,"Withdrawal reserves balance");
                refused(()->store.beginBank(buyer,1000,"OUT"));
                store.finishBank(bank,false);
                check(store.balance(buyer)==7500,"Rejected withdrawal restored");
                long deposit=store.beginBank(buyer,1000,"IN");
                check(store.balance(buyer)==7500,"Unconfirmed deposit not credited");
                store.finishBank(deposit,true);
                check(store.balance(buyer)==8500,"Successful deposit credited");
                refused(()->store.finishBank(deposit,true));
                store.credit(seller,Store.MAX_MONEY-store.balance(seller));
                refused(()->store.adminCredit(buyer,seller,1));
                check(store.balance(seller)==Store.MAX_MONEY,"Overflow prevented");
                Shop newShop=new Shop("new",seller,"Seller","New","world",0,64,0,0,"yesil",System.currentTimeMillis()+60000);
                store.create(newShop);
                long pending=store.reserve(newShop,"IRON","fixture",100,1);
                refused(()->store.reserve(newShop,"IRON","fixture",100,1));
                store.remove(newShop,"Closed",seller);
                check(store.pending().size()==1,"Uncertain escrow not automatically refunded");
                store.recover(seller,"ilan",pending,true);
                check(store.mailbox(seller).size()==2,"Escrow recovery returns confirmed removed item");
                check(store.pending().isEmpty(),"All uncertain operations resolved");
            }
            try(Store store=new Store(file)){
                check(store.balance(buyer)==8500,"Wallet survives reopening");
                check(store.mailbox(seller).size()==2,"Items survive reopening");
            }
            check(MuroPazar.amount("125,50",false)==12550,"Comma price parsed");
            refused(()->MuroPazar.amount("NaN",false));
            refused(()->MuroPazar.amount("-1",false));
            refused(()->MuroPazar.amount("1.001",false));
            System.out.println("Store checks passed: atomic purchase, stale price, duplicates, expiry, recovery, banking, bounds, persistence.");
        }finally{
            try(var files=Files.walk(dir)){for(Path p:files.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
        }
    }
}
