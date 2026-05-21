# Instrukcja instalacji BrakOffPC na nowym VPS

## 1. Założenia

- System: Ubuntu Server albo Debian.
- Publiczny adres aplikacji: `https://brakoff.website.com`.
- Backend: Spring Boot uruchamiany jako usługa `systemd`.
- Baza danych: SQLite w stałej ścieżce `/var/lib/brakoffpc/brakoffpc.db`.
- Import PDF/OCR: `tesseract` oraz `pdftoppm` z pakietu `poppler-utils`.
- Dostęp operatora: login i hasło.
- Dostęp telefonów: token API w nagłówku `Authorization: Bearer <token>`.

## 2. Instalacja pakietów systemowych

Zaloguj się na VPS przez SSH i wykonaj:

```bash
sudo apt update
sudo apt install -y openjdk-21-jre-headless tesseract-ocr tesseract-ocr-pol poppler-utils curl ca-certificates
```

Sprawdź wersję Javy:

```bash
java -version
```

Sprawdź narzędzia OCR:

```bash
tesseract --version
pdftoppm -v
```

## 3. Utworzenie użytkownika i katalogów aplikacji

Utwórz osobnego użytkownika systemowego:

```bash
sudo useradd --system --home /var/lib/brakoffpc --shell /usr/sbin/nologin brakoff
```

Utwórz katalogi:

```bash
sudo mkdir -p /opt/brakoffpc
sudo mkdir -p /etc/brakoffpc
sudo mkdir -p /var/lib/brakoffpc
sudo chown -R brakoff:brakoff /var/lib/brakoffpc
sudo chown -R root:root /opt/brakoffpc /etc/brakoffpc
```

## 4. Zbudowanie pliku JAR

Na komputerze deweloperskim, w katalogu projektu:

```bash
./mvnw clean package
```

Gotowy plik JAR będzie w katalogu:

```text
target/BrakOffPC-1.0.1.jar
```

Jeśli nazwa pliku JAR różni się po buildzie, użyj faktycznej nazwy z katalogu `target`.

## 5. Wgranie aplikacji na VPS

Przykład przez `scp`:

```bash
scp target/BrakOffPC-1.0.1.jar root@ADRES_VPS:/tmp/BrakOffPC.jar
```

Na VPS przenieś plik do katalogu aplikacji:

```bash
sudo mv /tmp/BrakOffPC.jar /opt/brakoffpc/BrakOffPC.jar
sudo chown root:root /opt/brakoffpc/BrakOffPC.jar
sudo chmod 644 /opt/brakoffpc/BrakOffPC.jar
```

## 6. Konfiguracja sekretów

Utwórz plik środowiskowy:

```bash
sudo nano /etc/brakoffpc/brakoffpc.env
```

Wklej:

```bash
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
BRAKOFF_OPERATOR_USERNAME=admin
BRAKOFF_OPERATOR_PASSWORD=TU_WPISZ_MOCNE_HASLO_OPERATORA
BRAKOFF_MOBILE_API_TOKEN=TU_WPISZ_DLUGI_LOSOWY_TOKEN_DLA_TELEFONOW
```

`SERVER_PORT` określa lokalny port aplikacji na VPS. Jeżeli zmienisz tę wartość, użyj tego samego portu w konfiguracji Cloudflare Tunnel i w lokalnych komendach diagnostycznych.

Zabezpiecz plik:

```bash
sudo chown root:root /etc/brakoffpc/brakoffpc.env
sudo chmod 600 /etc/brakoffpc/brakoffpc.env
```

Wygenerowanie przykładowego tokenu:

```bash
openssl rand -hex 32
```

## 7. Konfiguracja systemd

Utwórz plik usługi:

```bash
sudo nano /etc/systemd/system/brakoffpc.service
```

Wklej:

```ini
[Unit]
Description=BrakOffPC delivery control service
After=network-online.target
Wants=network-online.target

[Service]
User=brakoff
Group=brakoff
WorkingDirectory=/var/lib/brakoffpc
EnvironmentFile=/etc/brakoffpc/brakoffpc.env
ExecStart=/usr/bin/java -jar /opt/brakoffpc/BrakOffPC.jar
Restart=on-failure
RestartSec=10
SuccessExitStatus=143

NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/var/lib/brakoffpc

[Install]
WantedBy=multi-user.target
```

Włącz i uruchom usługę:

```bash
sudo systemctl daemon-reload
sudo systemctl enable brakoffpc
sudo systemctl start brakoffpc
```

Sprawdź status:

```bash
sudo systemctl status brakoffpc
```

Sprawdź logi:

```bash
sudo journalctl -u brakoffpc -f
```

Sprawdź lokalny endpoint:

```bash
curl http://127.0.0.1:8080/api/health
```

Oczekiwana odpowiedź:

```json
{"status":"ok"}
```

## 8. Cloudflare Tunnel

Zainstaluj `cloudflared` według aktualnej instrukcji Cloudflare dla używanego systemu. Po instalacji zaloguj tunel:

```bash
cloudflared tunnel login
```

Utwórz tunel:

```bash
cloudflared tunnel create brakoffpc
```

Utwórz konfigurację tunelu:

```bash
sudo mkdir -p /etc/cloudflared
sudo nano /etc/cloudflared/config.yml
```

Przykładowa konfiguracja:

```yaml
tunnel: brakoffpc
credentials-file: /root/.cloudflared/ID_TUNELU.json

ingress:
  - hostname: brakoff.mpdwodrol.com
    service: http://127.0.0.1:8080
  - service: http_status:404
```

Podmień `ID_TUNELU.json` na faktyczną nazwę pliku credentials utworzonego przez `cloudflared`.

Jeżeli w `/etc/brakoffpc/brakoffpc.env` ustawiono inny `SERVER_PORT` niż `8080`, zmień też port w linii:

```yaml
service: http://127.0.0.1:PORT_Z_ENV
```

Dodaj rekord DNS dla tunelu:

```bash
cloudflared tunnel route dns brakoffpc brakoff.mpdwodrol.com
```

Zainstaluj tunel jako usługę:

```bash
sudo cloudflared service install
sudo systemctl enable cloudflared
sudo systemctl restart cloudflared
```

Sprawdź status:

```bash
sudo systemctl status cloudflared
```

Po chwili sprawdź z zewnątrz:

```bash
curl https://brakoff.mpdwodrol.com/api/health
```

Oczekiwana odpowiedź:

```json
{"status":"ok"}
```

## 9. Firewall

Jeżeli używasz `ufw`, minimalna konfiguracja:

```bash
sudo ufw allow OpenSSH
sudo ufw enable
sudo ufw status
```

Nie otwieraj portu aplikacji publicznie. Aplikacja w profilu `prod` słucha tylko na `127.0.0.1`, więc jest dostępna lokalnie dla Cloudflare Tunnel.

## 10. Pierwsze uruchomienie panelu

Otwórz w przeglądarce:

```text
https://brakoff.mpdwodrol.com
```

Zaloguj się danymi z pliku `/etc/brakoffpc/brakoffpc.env`:

```text
Login: wartość BRAKOFF_OPERATOR_USERNAME
Hasło: wartość BRAKOFF_OPERATOR_PASSWORD
```

Po zalogowaniu sekcja `Połączenie` pokaże:

- adres serwera dla telefonów,
- token API dla telefonów,
- link i kod QR do pobrania aplikacji Android.

## 11. Pierwsze podłączenie telefonu

W aplikacji BrakOff na telefonie ustaw:

```text
Adres serwera: https://brakoff.mpdwodrol.com
Token API: wartość BRAKOFF_MOBILE_API_TOKEN
```

Test publiczny serwera:

```http
GET https://brakoff.mpdwodrol.com/api/health
```

Test tokenu:

```http
GET https://brakoff.mpdwodrol.com/api/delivery/current
Authorization: Bearer TOKEN
```

Interpretacja odpowiedzi:

- `200` oznacza poprawne połączenie i aktywną dostawę.
- `404` oznacza poprawne połączenie i poprawny token, ale brak aktywnej dostawy.
- `401` oznacza brak tokenu albo błędny token.

## 12. Import pierwszej dostawy

W panelu operatora:

1. Wczytaj PDF dostawy.
2. Sprawdź rozpoznane pozycje.
3. Popraw ewentualne błędy.
4. Zatwierdź import.
5. Po aktywacji dostawy telefony mogą pobierać dane i wysyłać skany.

## 13. Backup SQLite

Baza znajduje się tutaj:

```text
/var/lib/brakoffpc/brakoffpc.db
```

Prosty ręczny backup:

```bash
sudo systemctl stop brakoffpc
sudo cp /var/lib/brakoffpc/brakoffpc.db /var/lib/brakoffpc/brakoffpc-$(date +%F-%H%M).db
sudo systemctl start brakoffpc
```

Lepszy wariant z narzędziem `sqlite3`:

```bash
sudo apt install -y sqlite3
sudo -u brakoff sqlite3 /var/lib/brakoffpc/brakoffpc.db ".backup '/var/lib/brakoffpc/brakoffpc-$(date +%F-%H%M).db'"
```

Warto dodać automatyczny backup przez `cron` lub timer `systemd`.

## 14. Aktualizacja aplikacji

Na komputerze deweloperskim zbuduj nowy JAR:

```bash
./mvnw clean package
```

Wgraj na VPS:

```bash
scp target/BrakOffPC-1.0.1.jar root@ADRES_VPS:/tmp/BrakOffPC.jar
```

Na VPS:

```bash
sudo systemctl stop brakoffpc
sudo cp /var/lib/brakoffpc/brakoffpc.db /var/lib/brakoffpc/brakoffpc-before-update-$(date +%F-%H%M).db
sudo mv /tmp/BrakOffPC.jar /opt/brakoffpc/BrakOffPC.jar
sudo chown root:root /opt/brakoffpc/BrakOffPC.jar
sudo chmod 644 /opt/brakoffpc/BrakOffPC.jar
sudo systemctl start brakoffpc
sudo systemctl status brakoffpc
```

Sprawdź:

```bash
curl https://brakoff.mpdwodrol.com/api/health
```

## 15. Najczęstsze problemy

### Aplikacja nie startuje

Sprawdź logi:

```bash
sudo journalctl -u brakoffpc -n 200 --no-pager
```

Najczęstsze przyczyny:

- brak zmiennych `BRAKOFF_OPERATOR_USERNAME`, `BRAKOFF_OPERATOR_PASSWORD`, `BRAKOFF_MOBILE_API_TOKEN`,
- brak Javy 21,
- brak uprawnień do `/var/lib/brakoffpc`,
- port ustawiony w `SERVER_PORT` zajęty przez inny proces.

### Panel działa, ale telefon dostaje 401

Sprawdź, czy telefon wysyła:

```http
Authorization: Bearer TOKEN
```

Token musi być dokładnie taki sam jak `BRAKOFF_MOBILE_API_TOKEN`.

### Import PDF nie działa

Sprawdź:

```bash
tesseract --version
pdftoppm -v
```

Sprawdź, czy jest język polski:

```bash
tesseract --list-langs
```

Na liście powinno być `pol`.

### Domena nie działa

Sprawdź usługę tunelu:

```bash
sudo systemctl status cloudflared
sudo journalctl -u cloudflared -n 200 --no-pager
```

Sprawdź lokalnie aplikację:

```bash
curl http://127.0.0.1:8080/api/health
```

Jeżeli `SERVER_PORT` ma inną wartość niż `8080`, użyj tej wartości w adresie lokalnym. Jeżeli lokalnie działa, problem jest po stronie Cloudflare Tunnel albo DNS.
