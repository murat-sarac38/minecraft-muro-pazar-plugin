# muroPazar 1.0.0

Spigot **26.2** için Türkçe, Metin2 esintili oyuncu pazarı eklentisi.
Bu çalışma nPlayerShops'ın yayımlanan özelliklerinden esinlenen **bağımsız bir uygulamadır**.
Orijinal eklentinin kaynak kodunu, özel modellerini veya kaynak paketini içermez.
Videonun tüm kareleri incelenmediğinden birebir görsel eşleşme iddiası yoktur.

## Kurulum

1. Sunucunun ve oyuncu verilerinin yedeğini al, sunucuyu düzgünce durdur.
2. **Spigot 26.2** ve bu sunucu sürümü için **Java 25** kullan.
3. `muroPazar-1.0.0.jar` dosyasını sunucunun `plugins/` klasörüne koy.
4. Sunucuyu başlat. `plugins/muroPazar/config.yml` ve `pazar.db` oluşur.
5. Oyun içinde `/muropazar`, `/pazar` veya `/muro` yaz.
6. Yerel ekonomi sıfır bakiyeyle başlar. OP olarak `/pazar para OyuncuAdi 1000` ile çevrimiçi bir oyuncuya para ekle.

**SQLite sürücüsü JAR'ın içindedir.** Ek bir veritabanı sunucusu, Replit bağlantısı,
web sitesi, kaynak paketi veya istemci modu gerekmez. Bu bir Minecraft Java sunucu
eklentisidir; Bedrock eklentisi değildir.

Geliştirme ortamındaki video önizlemesi bir Minecraft sunucusu değildir.
JAR, resmi Spigot 26.2 API'sine karşı derlenmiştir. Canlı Spigot sunucusunda oyuncuyla
menü/tezgâh doğrulaması henüz yapılmadı.

## Oyuncu özellikleri

- Bulunduğun yerde süreli, dünyada görünen tezgâh kurma.
- Sağ tıkla alışveriş; menüden tüm pazarları gezme.
- Pazar adı değiştirme, yapılandırmadan eklenebilen tezgâh görünümleri.
- Elindeki eşya yığınını **toplam fiyat** ile satışa koyma.
- Eşyanın Bukkit verisiyle büyü, isim, lore, konteyner içeriği gibi bilgilerini koruma.
- Ürünü satın almadan önce onay; satıcı sonradan fiyat değiştirmişse yeniden onay isteme.
- İlan fiyatı değiştirme, ilan kaldırma, pazarı kaldırma.
- Eşya ve materyal adına göre arama; sık kullanılan Türkçe eşya karşılıkları.
- Süresi dolan ve kaldırılan pazarlardaki satılmamış ürünleri teslimat kutusuna aktarma.
- Satın alınan eşyaları da aynı kutuda güvenli teslimata hazırlama.
- Çevrimdışı satıcıya yerel cüzdan üzerinden anında ödeme.
- Sayfalı menüler, kalıcı SQLite kayıtları, yönetici işlem günlüğü.

Pazar süresi sunucu kapalıyken de ilerler. Yeniden açıldığında süresi dolan
pazarlardaki aktif ilanlar kutuya iade edilir. Kutu ürünleri zaman aşımına uğramaz.
Fiziksel tezgâhlar yalnızca yüklenmiş chunk'larda oluşturulur; chunk'lar zorla
açık tutulmaz. Tam sunucu yeniden başlatması desteklenir; `/reload` önerilmez.

## Komutlar

| Komut | İşlev |
|---|---|
| `/pazar` | Ana menü |
| `/pazar kur [isim]` | Bulunduğun konumda pazar kur |
| `/pazar pazarim` | Kendi pazarını yönet |
| `/pazar sat <fiyat>` | Elindeki **tüm yığını** belirtilen toplam fiyata ekle |
| `/pazar liste` | Açık pazarları görüntüle |
| `/pazar ara <eşya>` | Ürün ara; boş bırakınca sohbete giriş |
| `/pazar isim <ad>` | Pazar adını değiştir |
| `/pazar gorunum [anahtar]` | Görünüm seç |
| `/pazar kaldir` | Pazar kaldırma onayı |
| `/pazar kutu` | Satın alınan/iade edilen ürünleri teslim al |
| `/pazar bakiye` | Cüzdanı aç |
| `/pazar yatir <tutar>` | Vault'tan pazar cüzdanına aktar |
| `/pazar cek <tutar>` | Pazar cüzdanından Vault'a aktar |
| `/pazar yardim` | Yardım |

Menülerden de pazar kurulur, fiyat girilir ve ürünler düzenlenir.
Sohbet girişinde `iptal` yazılabilir; girişin süresi 60 saniyedir.
Fiyatlarda nokta veya virgül kullanılabilir; en fazla iki ondalık kabul edilir.
İlanlarda görüntülenen fiyat birim fiyat değil yığının toplamıdır.

## Ekonomi ve Vault

Yerel pazar cüzdanı her zaman mevcuttur. **Vault bakiyesi ile pazar bakiyesi
aynı hesap değildir.** Varsayılan `vault-transfers: false` durumunda başka eklenti
gerekmez. Yönetici para ekleyebilir; başlangıç parasını config'den belirleyebilir.

Sunucu ekonomisiyle aktarım için:

1. Spigot 26.2 ile uyumlu Vault ve bir Vault ekonomi sağlayıcısı yükle.
2. `vault-transfers: true` yap; sunucuyu tamamen yeniden başlat.
3. `/pazar yatir 100` sunucu bakiyesinden 100 alıp pazar cüzdanına koyar.
4. `/pazar cek 100` ters yönde işlem yapar.

Vault etkinleştirilmiş ama yüklenmemişse veya sağlayıcısı yoksa eklenti açık
hata verip devre dışı kalır; sessizce farklı bir ekonomi kullanmaz.
Vault transferinde kesinti olursa işlem otomatik tekrar edilmez; yönetici
sağlayıcı kayıtlarını karşılaştırarak kurtarma yapar.

**Site kredisi entegrasyonu eklenmedi.** Hangi site altyapısının kullanıldığı
ve onun yetkili API sözleşmesi bilinmeden site bakiyesine erişilmez. Yerel
cüzdan, site kredisi gibi gösterilmez.

## Görünümler ve alan kuralları

Varsayılan görünümler: `yesil` (Zümrüt Tezgâh), `kizil` (Kızıl Tezgâh),
`mavi` (Lapis Tezgâh), `oyuncu` (oyuncunun kafası).
Görünüm menüsüne yeni bir seçenek eklemek için `styles` altında şu alanları ekle:

```yaml
  sari:
    name: 'Altın Tezgâh'
    canopy: YELLOW_WOOL
    base: BIRCH_PLANKS
    helmet: GOLD_BLOCK
    color: '#e5b749'
```

Bu görünümler vanilla block display ve zırh askılığıyla oluşturulur. Orijinal
videodaki özel çadır modeli veya kostüm paketinin kopyası değildir. Özel oyuncu
doku URL'leri desteklenmez; `PLAYER_HEAD` seçeneği pazar sahibinin kafasını kullanır.

- Varsayılan süre 24 saat, ilan sınırı 45, minimum pazar mesafesi 4 blok.
- `shop.allowed-worlds` boşsa tüm dünyalara izin verilir.
- Halka açık sunucuda yalnızca ayrılmış bir pazar dünyasına izin vermek önerilir.
- Vanilla spawn koruması, dünya sınırı, sağlam zemin ve yükseklik kontrol edilir.
- WorldGuard/PlotSquared gibi bölge eklentileriyle otomatik entegrasyon yoktur.
  Başkasının arsasına pazar kurmayı bu eklentilere güvenerek önlemiş saymayın.
  İzinleri ve dünya listesini buna göre kısıtlayın.
- `muropazar.bypass` sahibi mesafe/dünya/spawn sınırlarını atlar; varsayılan OP'dir.
- Tezgâhlar gerçek blok değiştirmez; kırma ve zırh alma olayları engellenir.

## Yetkiler

| Yetki | Varsayılan |
|---|---|
| `muropazar.use` | Herkes |
| `muropazar.create` | Herkes |
| `muropazar.admin` | OP |
| `muropazar.bypass` | OP |

## Yönetici ve kesinti kurtarma

- `/pazar para <çevrimiçi oyuncu> <tutar>`: pozitif tutarda yerel bakiye ekle.
- `/pazar yoneticikaldir <pazar-id>`: pazarı kapat, ürünleri sahibine iade et.
  Pazar ID'si yöneticilere pazar listesinde gösterilir.
- `/pazar incele`: belirsiz eşya veya banka işlemlerini listele.
- `/pazar kurtar <ilan|kutu|banka> <id> <tamam|iptal>`: **yalnızca kayıtları
  ve oyuncunun verisini kontrol ettikten sonra** belirsiz işlemi çöz.

| Tür | `tamam` ne demek? | `iptal` ne demek? |
|---|---|---|
| `ilan` | Eşyanın oyuncudan alındığı doğrulandı; kutusuna iade et | Eşya oyuncuda kalmış; bekleyen kopyayı iptal et |
| `kutu` | Eşya oyuncuya ulaşmış; bekleyen kaydı kapat | Eşya ulaşmamış; teslimatı tekrar alınabilir yap |
| `banka` | Vault işlemi başarılı olmuş; yerel muhasebeyi tamamla | Vault işlemi gerçekleşmemiş; varsa rezerve parayı geri ver |

Yanlış kurtarma kararı eşya/para çoğalmasına veya kaybına yol açabilir.
Yönetici işlemleri `audit` tablosuna yazılır. Belirsiz işlemler otomatik
yeniden denenmez; bu özellikle çift ödeme riskini azaltmak içindir.

Yerel satın alma tek SQLite işlemiyle alıcı parasını düşürür, satıcıya ekler,
ilanı kapatır ve eşyayı alıcının kutusuna koyar. Bukkit oyuncu envanteri ile
SQLite arasında dağıtık atomik işlem yoktur. Teslimat/ilan oluşturma sırasında
ani güç kesilirse kayıt karantinaya alınır ve manuel inceleme gerekebilir.
Bu yaklaşım "her koşulda sıfır kayıp" garantisi değildir.

## Yedekleme

Sunucuyu durdurup oyuncu dosyalarıyla birlikte `plugins/muroPazar/` klasörünün
tamamını yedekle. Canlı SQLite veritabanının sadece `pazar.db` dosyasını kopyalamak,
WAL kayıtlarını dışarıda bırakabilir. Geri yüklerken oyuncu verilerini ve pazar
veritabanını aynı yedek anından birlikte yükle.

## Derleme

Kaynak klasöründe Java 17+ ve Maven 3.9+ ile:

```sh
mvn -B -ntp clean package
```

Çıktı: `target/muroPazar-1.0.0.jar`. `original-...jar` dağıtım dosyası değildir.
Sunucunun Java gereksinimi, derlemenin alt sınırından farklıdır: Spigot 26.2
sunucusunu Java 25 ile çalıştır.

Depo kökünden `bash plugins/muroPazar/package-release.sh` JAR + kaynak kodu +
bu kılavuzu `generated-artifacts/muroPazar/` altında paketler.

## Tanıtım

Projede ayrı bir **31 saniyelik, 16:9 Türkçe animasyon** ve özgün arka plan müziği
bulunur. Bu animasyon eklentiyi anlatan temsili görselleştirmedir; canlı Minecraft
oyun kaydı değildir. Çadır/görünüm çizimleri kurulu varsayılanlarla birebir ekran
görüntüsü olarak değerlendirilmemelidir. Filmdeki örnek fiyatlar/bakiyeler
gösterim amaçlıdır. Site kredisi özelliği filmde de yer almaz.
