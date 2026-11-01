package tr.muro.pazar;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;


final class VaultBridge {
    private final Object provider;
    private final Method withdraw;
    private final Method deposit;
    private final Method fractionalDigits;
    private final Method success;
    private final java.lang.reflect.Field error;

    @SuppressWarnings({"rawtypes", "unchecked"})
    VaultBridge() throws ReflectiveOperationException {
        Class api = Class.forName("net.milkbowl.vault.economy.Economy");
        RegisteredServiceProvider registration = Bukkit.getServicesManager().getRegistration(api);
        if (registration == null) throw new IllegalStateException("Vault ekonomi sağlayıcısı bulunamadı.");
        provider = registration.getProvider();
        withdraw = api.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
        deposit = api.getMethod("depositPlayer", OfflinePlayer.class, double.class);
        fractionalDigits = api.getMethod("fractionalDigits");
        Class<?> response = Class.forName("net.milkbowl.vault.economy.EconomyResponse");
        success = response.getMethod("transactionSuccess");
        error = response.getField("errorMessage");
    }

    void validate(long cents) throws ReflectiveOperationException {
        int digits = (int) fractionalDigits.invoke(provider);
        if (digits == 0 && cents % 100 != 0)
            throw new IllegalArgumentException("Sunucu ekonomisi yalnız tam sayı kabul ediyor.");
        if (digits == 1 && cents % 10 != 0)
            throw new IllegalArgumentException("Sunucu ekonomisi yalnız bir ondalık basamak kabul ediyor.");
    }
    String transfer(OfflinePlayer player, long cents, boolean intoWallet) throws ReflectiveOperationException {
        Object result = (intoWallet ? withdraw : deposit).invoke(provider, player, cents / 100.0);
        if ((boolean) success.invoke(result)) return null;
        Object message = error.get(result);
        return message == null ? "Ekonomi sağlayıcısı işlemi reddetti." : message.toString();
    }
}
