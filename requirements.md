# Zaktualizowany plan aplikacji PC dla Codex

## 1. Główny cel aplikacji PC

Aplikacja PC ma:

* wczytać dokument dostawy z pliku PDF,
* wyciągnąć z niego listę pozycji dostawy,
* zbudować aktywną dostawę w systemie,
* udostępnić ją telefonom Android w tej samej sieci WiFi,
* zbierać od nich stany produktów,
* prezentować aktualny status dostawy,
* wygenerować raport PDF z:

    * brakującymi produktami,
    * nadmiarowymi produktami,
    * produktami niezamówionymi.

---

# 2. Kluczowa zmiana architektoniczna

W poprzednim planie import opierał się na CSV/XLSX.
Teraz **podstawowym wejściem jest PDF**.

To oznacza, że aplikacja PC musi mieć dodatkowy moduł:

## Moduł ekstrakcji danych z PDF

który:

* odczytuje tekst z PDF,
* znajduje tabelę lub wiersze pozycji,
* mapuje kolumny:

    * barcode
    * name
    * expectedQty
* pokazuje wynik do weryfikacji użytkownikowi przed zatwierdzeniem importu.

---

# 3. Najważniejsze założenie projektowe dla PDF

Nie należy zakładać, że każdy PDF będzie idealnie parsowalny.

Dlatego import PDF powinien działać w **2 etapach**:

## Etap 1 — automatyczne odczytanie danych

System próbuje sam wyciągnąć pozycje.

## Etap 2 — ekran weryfikacji importu

Użytkownik widzi tabelę rozpoznanych danych i może:

* zaakceptować import,
* poprawić pojedyncze pola,
* usunąć błędny wiersz,
* dodać brakujący wiersz ręcznie.

To jest bardzo ważne, bo bez tego aplikacja będzie zawodna przy realnych PDF-ach.

---

# 4. Zakres aplikacji PC po zmianie

Aplikacja PC odpowiada za:

* import PDF dostawy,
* ekstrakcję danych z PDF,
* walidację rozpoznanych rekordów,
* ręczną korektę danych po ekstrakcji,
* zapis aktywnej dostawy,
* udostępnienie dostawy telefonom,
* odbiór stanów z telefonów,
* agregację stanów per urządzenie,
* dashboard bieżącej dostawy,
* generowanie raportu PDF,
* reset aktywnej dostawy.

---

# 5. Wymagania funkcjonalne — import PDF

## 5.1. Wczytanie pliku PDF

Aplikacja musi umożliwiać wybór lokalnego pliku PDF z dokumentem dostawy.

Po wyborze pliku system:

* zapisuje nazwę pliku,
* uruchamia ekstrakcję danych,
* pokazuje postęp importu.

## 5.2. Obsługa PDF tekstowego

W pierwszej kolejności aplikacja powinna wspierać **PDF tekstowy**, czyli taki, w którym tekst da się zaznaczyć i odczytać.

To powinien być podstawowy scenariusz implementacyjny MVP.

## 5.3. Obsługa PDF skanowanego

Jeśli PDF jest skanem obrazu, aplikacja może:

* wykryć brak warstwy tekstowej,
* zgłosić komunikat:
  „Ten PDF wygląda jak skan i nie może zostać automatycznie odczytany w tej wersji”

albo w wersji rozszerzonej:

* użyć OCR.

## Rekomendacja MVP

Na start dla Codex:

* obsłużyć dobrze **PDF tekstowy**
* nie robić OCR w pierwszej wersji

To znacząco upraszcza projekt.

---

# 6. Dane wymagane z PDF

Z dokumentu dostawy trzeba wyciągnąć co najmniej:

* wartość liczbowa kodu kreskowego,
* nazwę przedmiotu,
* liczbę oczekiwanych przedmiotów w dostawie.

## Minimalny model po imporcie

```text id="m50rj5"
barcode: String
name: String
expectedQty: Int
```

## Dodatkowe dane opcjonalne

Jeśli są obecne w PDF, można zapisać też:

* numer dokumentu,
* data dokumentu,
* dostawca,
* numer pozycji.

Ale nie są wymagane do działania systemu.

---

# 7. Reguły ekstrakcji danych z PDF

## 7.1. Barcode

Pole barcode:

* ma być traktowane jako string, nie integer,
* może zawierać długi numer,
* nie wolno obcinać zer wiodących, jeśli występują,
* powinno być walidowane jako ciąg cyfr.

## 7.2. Name

Pole name:

* tekst produktu,
* może zawierać spacje, myślniki, skróty,
* może być dłuższe niż jedna kolumna na ekranie.

## 7.3. Expected quantity

Pole expectedQty:

* liczba całkowita,
* większa lub równa zero.

---

# 8. Ekran weryfikacji importu PDF

Po automatycznej ekstrakcji aplikacja musi pokazać użytkownikowi ekran weryfikacyjny.

Tabela powinna zawierać:

* barcode
* name
* expectedQty
* status walidacji

## Użytkownik musi móc:

* edytować barcode,
* edytować name,
* edytować expectedQty,
* usunąć wiersz,
* dodać nowy wiersz ręcznie,
* zatwierdzić import.

## Statusy walidacji wiersza:

* OK
* brak barcode
* niepoprawna ilość
* duplikat barcode

Import nie powinien zostać zatwierdzony, jeśli istnieją błędy krytyczne.

---

# 9. Logika walidacji po imporcie PDF

Aplikacja musi sprawdzać:

* czy barcode nie jest pusty,
* czy barcode zawiera tylko cyfry,
* czy expectedQty jest liczbą całkowitą,
* czy expectedQty >= 0,
* czy ten sam barcode nie występuje wielokrotnie.

## Przy duplikacie barcode:

Są dwa możliwe podejścia.

### Podejście rekomendowane

Pokazać błąd i wymagać decyzji użytkownika.

### Podejście alternatywne

Scalić rekordy o tym samym barcode przez sumowanie expectedQty.

## Rekomendacja dla MVP

Pokazać błąd i pozwolić użytkownikowi poprawić ręcznie.
To jest bezpieczniejsze.

---

# 10. Zmiana w UX aplikacji PC

Nowy flow na PC:

1. Uruchom aplikację
2. Kliknij „Wczytaj PDF dostawy”
3. Wybierz plik PDF
4. Poczekaj na ekstrakcję
5. Zweryfikuj rozpoznaną tabelę
6. Kliknij „Zatwierdź dostawę”
7. Telefony mogą pobrać aktywną dostawę
8. Obserwuj dashboard
9. Kliknij „Generuj raport PDF”

To nadal pozostaje proste, ale import jest bezpieczniejszy.

---

# 11. Zmiana w architekturze technicznej

Do stacku PC trzeba dodać bibliotekę do odczytu PDF.

## Rekomendowany stack po zmianie

* Java 21
* Spring Boot
* SQLite
* Thymeleaf lub prosty HTML/JS
* Apache PDFBox
* OpenPDF lub iText do generowania raportu PDF

## Rola PDFBox

PDFBox ma służyć do:

* odczytu tekstu z PDF,
* ewentualnego odczytu pozycji tekstu,
* przygotowania ekstrakcji tabelarycznej.

---

# 12. Strategia parsowania PDF

To jest kluczowa część dla Codex.

## Wersja MVP

Przyjąć, że dokument PDF:

* zawiera powtarzalny układ tabeli,
* kolumny są czytelne w warstwie tekstowej,
* można wydobyć linie tekstowe i przemapować je do:

    * barcode
    * name
    * expectedQty

## Strategia implementacyjna

Codex powinien zbudować parser w 3 krokach:

### Krok 1

Wyciągnąć tekst lub linie tekstowe z PDF.

### Krok 2

Zidentyfikować sekcję tabeli dostawy.

### Krok 3

Dla każdej linii spróbować zmapować:

* pierwszy pasujący numer jako barcode,
* ostatnią liczbę jako expectedQty,
* środek jako name.

## Ważne

Parser powinien być napisany tak, żeby dało się go później dostroić pod konkretny format PDF.

---

# 13. Obsługa błędów importu PDF

Aplikacja musi mieć czytelne komunikaty:

## Brak możliwości odczytu PDF

„Nie udało się odczytać danych z pliku PDF.”

## PDF bez rozpoznawalnych pozycji

„Nie znaleziono tabeli dostawy w pliku PDF.”

## PDF wygląda jak skan

„Dokument nie zawiera warstwy tekstowej. W tej wersji import działa tylko dla tekstowych PDF.”

## Niepełne dane

„Część wierszy wymaga poprawy przed zatwierdzeniem importu.”

---

# 14. Zmienione wymagania funkcjonalne dla Codex

## 14.1. Import PDF jako podstawowe wejście

Aplikacja ma umożliwiać import dokumentu dostawy z PDF i nie wymagać CSV/XLSX w pierwszej wersji.

## 14.2. Podgląd i korekta danych po imporcie

Po ekstrakcji aplikacja ma pokazać edytowalną tabelę przed zatwierdzeniem.

## 14.3. Zatwierdzenie aktywnej dostawy

Dopiero po zatwierdzeniu przez użytkownika dostawa staje się aktywna i dostępna dla telefonów.

## 14.4. Zachowanie źródłowego pliku

Aplikacja może zapisać ścieżkę lub nazwę źródłowego PDF do celów informacyjnych i raportowych.

---

# 15. Zmodyfikowany prompt dla Codex

Możesz wkleić do Codex taką wersję:

„Zbuduj prostą aplikację PC na Windows w Java 21 + Spring Boot do obsługi jednej bieżącej dostawy. Aplikacja ma uruchamiać się lokalnie i być intuicyjna dla użytkownika biurowego. Głównym wejściem ma być dokument dostawy w formacie PDF. PDF zawiera co najmniej kolumny: liczbowy kod kreskowy produktu, nazwę przedmiotu i oczekiwaną liczbę sztuk. Aplikacja ma odczytać tekstowy PDF, wyekstrahować te dane, pokazać użytkownikowi ekran weryfikacji i ręcznej korekty rozpoznanych wierszy, a następnie po zatwierdzeniu utworzyć aktywną dostawę. Aplikacja ma wystawiać REST API dla telefonów Android w tej samej sieci WiFi, przyjmować od nich aktualny stan produktu dla pary deviceId + barcode, agregować dane ze wszystkich urządzeń i pokazywać dashboard expected vs scanned. Ma poprawnie obsługiwać retry i duplikaty przez przyjmowanie tylko nowszego stanu na podstawie revision i updatedAt. Ma obsługiwać także produkty spoza listy dostawy, widok per urządzenie oraz generowanie raportu PDF z brakami, nadmiarami i produktami niezamówionymi. Użyj SQLite, Apache PDFBox do odczytu PDF, prostego frontendu HTML/JS lub Thymeleaf oraz biblioteki do generowania PDF. W pierwszej wersji wspieraj tylko PDF tekstowy, bez OCR.”

---

# 16. Końcowa weryfikacja spójności

Sprawdziłem, czy ta zmiana nie psuje wcześniejszego planu Android.

## Zgadza się:

* Android nadal pobiera aktywną dostawę z PC
* Android nadal wysyła stan końcowy per `deviceId + barcode`
* PC nadal agreguje dane po urządzeniach
* raport PDF nadal jest generowany po stronie PC

## Jedyna istotna zmiana:

po stronie PC dochodzi etap:

* ekstrakcja z PDF
* ręczna weryfikacja
* dopiero potem aktywacja dostawy

To jest spójne i nawet bezpieczniejsze operacyjnie.

## Finalna rekomendacja

Dla pierwszej wersji MVP:

* import tylko z **tekstowego PDF**
* bez OCR
* z ekranem korekty danych
* bez historii dostaw
* z lokalnym SQLite
* z prostym webowym UI
