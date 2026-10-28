# Wyzwania

**Codzienne i tygodniowe wyzwania dla serwerów Paper Minecraft.**

Wspólne zadania dla całego serwera, indywidualny postęp graczy i nagrody za aktywną grę. Plugin obsługuje rankingi, tytuły na czacie oraz podgląd postępu pod TAB. Działa bez Vault, LuckPerms i zewnętrznej bazy danych.

**Paper 26.3 · Java 25 · SQLite · Polski interfejs**

[Instalacja](#instalacja) · [Komendy](#komendy) · [Konfiguracja](#konfiguracja) · [Kompilacja](#kompilacja) · [Testy](#testy) · [Dokumentacja administratora](docs/ADMINISTRATION.md)

## Funkcje

- **3 wyzwania dzienne** — łatwe, średnie i trudne, każde z innym typem aktywności.
- **1 wyzwanie tygodniowe** — kilka celów, które należy wykonać łącznie.
- **57 szablonów zadań** — 12 łatwych, 18 średnich, 16 trudnych i 11 tygodniowych. Dwa szablony dzienne wymagają opcjonalnego PvP.
- **Brak powtórek dzień po dniu** — wszystkie trzy szablony poprzedniego zestawu dziennego są wykluczane z kolejnego losowania.
- **Nagrody** — przedmioty, XP i punkty rankingowe; ręczny odbiór także po wygaśnięciu zadania.
- **Rankingi i tytuły** — ranking ogólny, miesięczny oraz wybór odblokowanego prefixu.
- **Czat i TAB** — ogłoszenia o ukończeniu zadania i indywidualny podgląd postępu.
- **Trwały zapis** — zadania, postęp, punkty i nagrody zachowywane w SQLite między restartami.
- **Obsługa administracyjna** — reload konfiguracji, ręczne losowanie, import zadań i przyznawanie punktów.

### Rodzaje zadań

| Aktywność | Przykład |
| --- | --- |
| Walka | Zabij 25 zombie lub 30 guardianów |
| Wydobywanie | Wykop 32 rudy żelaza lub 6 ancient debris |
| Crafting | Stwórz 16 latarni lub 8 biblioteczek |
| Jedzenie | Zjedz 8 różnych rodzajów jedzenia |
| Eksploracja | Odwiedź 5 różnych biomów lub przebądź 5000 bloków |
| Rybactwo | Złów 25 połowów lub wyłów 5 skarbów |
| Hodowla | Rozmnóż 20 krów lub postrzyż 20 owiec |
| Rolnictwo | Zbierz 128 dojrzałych bloków pszenicy |
| Alchemia | Uwarz 12 butelek mikstur z efektem |
| Rzemiosło | Napraw 3 zużyte przedmioty na kowadle |
| Handel | Wykonaj 20 transakcji z osadnikami |
| PvP — opcjonalne | Pokonaj 3 graczy lub zadaj 200 obrażeń |

## Wymagania

| Element | Wersja |
| --- | --- |
| Serwer | Paper 26.3 |
| Java | 25 |
| JDK do kompilacji | 25 |
| System budowania | Gradle Wrapper — dołączony do repozytorium |

Projekt korzysta z Paper API `26.3.build.211-stable`. Nie deklaruje zgodności ze Spigotem, Folią ani starszymi wersjami Minecrafta. Gson i sterownik SQLite są dołączone do wynikowego JAR-a.

## Instalacja

1. [Skompiluj projekt](#kompilacja), aby otrzymać `build/libs/Wyzwania-1.3.0.jar`.
2. Zatrzymaj serwer komendą `stop`.
3. Skopiuj JAR do katalogu `plugins/` serwera.
4. Uruchom serwer. Plugin utworzy `plugins/Wyzwania/config.yml` oraz `plugins/Wyzwania/wyzwania.db`.
5. W grze wpisz `/wyzwanie`, aby zobaczyć aktywne zadania.

Domyślnie reset dzienny następuje o **04:00 w strefie `Europe/Warsaw`**, a tygodniowy w **poniedziałek o 04:00**. Uwzględniana jest zmiana czasu letniego i zimowego.

### Aktualizacja

Zatrzymaj serwer, wykonaj kopię katalogu `plugins/Wyzwania/`, usuń poprzedni JAR i zastąp go nowym. Zachowaj bazę i własny config. Nie używaj serwerowego `/reload` do wymiany pluginu.

Aby dopisać nowe szablony do istniejącej konfiguracji, po uruchomieniu wykonaj:

```text
/wyzwanie admin uzupelnij
```

Komenda dodaje tylko brakujące identyfikatory, zachowuje istniejące ustawienia i przeładowuje konfigurację. Nowa pula obowiązuje od następnego losowania. [Szczegóły aktualizacji i importu](docs/ADMINISTRATION.md).

## Komendy

Alias `/wyzwania` działa tak samo jak `/wyzwanie`. Komendy obsługują podpowiedzi TAB.

### Gracze

| Komenda | Opis |
| --- | --- |
| `/wyzwanie` | Aktywne zadania, postęp, nagrody i terminy resetów |
| `/wyzwanie postep` | Szczegółowy postęp i lista oczekujących nagród |
| `/wyzwanie odbierz` | Odbiór przedmiotów i XP |
| `/wyzwanie ranking` | Ranking ogólny — 10 najlepszych graczy |
| `/wyzwanie ranking miesiac` | Ranking bieżącego miesiąca |
| `/wyzwanie info` | Zasady i progi tytułów |
| `/wyzwanie prefix` | Lista dostępnych tytułów |
| `/wyzwanie prefix <id>` | Wybór odblokowanego tytułu, np. `lowca` |
| `/wyzwanie prefix auto` | Automatycznie najwyższy odblokowany tytuł |

### Administratorzy

| Komenda | Opis |
| --- | --- |
| `/wyzwanie reload` | Sprawdzenie i przeładowanie konfiguracji |
| `/wyzwanie admin uzupelnij` | Import brakujących domyślnych szablonów i reload |
| `/wyzwanie admin losuj [dzienne/tygodniowe/wszystkie]` | Nowe losowanie; domyślnie obie grupy |
| `/wyzwanie admin punkty <nick lub UUID> <liczba>` | Dodanie punktów graczowi online lub znanemu graczowi offline |
| `/wyzwanie admin zapisz` | Natychmiastowy zapis danych |
| `/wyzwanie admin niepewne` | Odbiory wymagające sprawdzenia po awarii |
| `/wyzwanie admin rozwiaz <UUID> <ID> <potwierdz/ponow>` | Rozstrzygnięcie przerwanego odbioru |

Nawiasy kwadratowe oznaczają argument opcjonalny, a ukośniki rozdzielają dostępne wartości — wpisz jedną z nich. Ranking, informacje i komendy administratora działają także z konsoli, bez początkowego `/`.

> **Ręczne losowanie zeruje postęp wymienianych zadań u wszystkich graczy.** Zdobyte punkty, zaległe nagrody i postęp pozostałej grupy są zachowywane. Brak alternatywnych szablonów powoduje odmowę zmiany.

### Uprawnienia

| Uprawnienie | Domyślnie | Dostęp |
| --- | --- | --- |
| `wyzwania.use` | Wszyscy gracze | Komendy gracza, naliczanie postępu i odbiór nagród |
| `wyzwania.admin` | Operatorzy | Operacje administracyjne |

## Konfiguracja

Edytuj **serwerowy** plik `plugins/Wyzwania/config.yml`, następnie wykonaj `/wyzwanie reload`. Pełny zestaw ustawień i szablonów znajduje się w [domyślnym configu](src/main/resources/config.yml).

```yaml
reset:
  timezone: Europe/Warsaw
  time: '04:00'
storage:
  autosave-seconds: 30
worlds: []
pvp:
  enabled: false
  kill-cooldown-seconds: 1800
  damage-cooldown-seconds: 30
movement:
  max-step: 12.0
chat:
  enabled: true
announcements:
  completion: true
tab:
  enabled: true
  refresh-seconds: 2
```

- `worlds: []` dopuszcza wszystkie światy; można podać ich listę.
- `pvp.enabled` dopuszcza zadania PvP do losowania i włącza ich naliczanie.
- `chat.enabled` włącza prefixy; wyłącz je, jeśli formatowaniem zarządza inny plugin.
- `announcements.completion` steruje ogłoszeniami o ukończeniu zadania.
- `tab.enabled` włącza postęp w stopce listy graczy. Przy konflikcie z innym pluginem TAB można go wyłączyć.

### Własne zadania

Dodaj wpis do istniejącej sekcji `quests`:

```yaml
quests:
  diamentowa_wyprawa:
    tier: HARD
    title: 'Diamentowa wyprawa'
    objectives:
      - type: MINE
        target: 'DIAMOND_ORE,DEEPSLATE_DIAMOND_ORE'
        goal: 16
        label: 'Wykop rudy diamentu'
```

Po reloadzie nowe szablony trafią do kolejnego losowania. Poziomy to `EASY`, `MEDIUM`, `HARD` i `WEEKLY`. Dzienne zadanie ma jeden cel, tygodniowe może mieć 1–10 celów wymaganych łącznie.

Pula dzienna musi umożliwiać dwa rozłączne zestawy EASY/MEDIUM/HARD, każdy z trzema różnymi typami aktywności. Domyślna konfiguracja spełnia ten warunek. [Typy celów i szczegółowe reguły](docs/ADMINISTRATION.md).

### Nagrody i punkty

Każdy poziom ma własne `rewards.<poziom>`: zakres XP, liczbę punktów i ważoną pulę pakietów przedmiotów. Pakiet i XP losowane są razem z zadaniem i widoczne przed jego ukończeniem.

Domyślnie zadanie dzienne daje **10 punktów**, a tygodniowe **50 punktów**. Punkty trafiają na konto przy ukończeniu; przedmioty i XP odbiera się przez `/wyzwanie odbierz`. Punkty nie są walutą.

Zmiana `rewards.HARD.points`, np. na `35`, działa na aktywne trudne zadanie po reloadzie lub restarcie. Nie przelicza wcześniej przyznanych punktów. Zmiany przedmiotów, XP i szablonów obowiązują od kolejnego losowania. Błędny reload pozostawia poprzednią konfigurację aktywną.

| Punkty ogólne | Tytuł |
| --- | --- |
| 0–99 | Nowicjusz |
| 100–499 | Łowca |
| 500–999 | Weteran |
| 1000–2499 | Mistrz |
| 2500+ | Legenda |

Progi i wygląd tytułów można zmienić w sekcji `prefixes`, używając MiniMessage.

## Kompilacja

Wymagany jest **JDK 25**. Gradle Wrapper pobierze Gradle i zależności przy pierwszym uruchomieniu; potrzebny jest wtedy internet.

**Windows / PowerShell:**

```powershell
java -version
.\gradlew.bat clean build
```

**Linux / macOS:**

```bash
java -version
bash ./gradlew clean build
```

Wynik: `build/libs/Wyzwania-1.3.0.jar`. Jeśli Java nie jest znajdowana, ustaw `JAVA_HOME` na katalog JDK 25 i dodaj jego `bin` do `PATH`.

## Testy

```powershell
# Windows
.\gradlew.bat test
```

```bash
# Linux / macOS
bash ./gradlew test
```

Raport HTML powstaje w `build/reports/tests/test/index.html`.

Testy obejmują resety i zmianę czasu, losowanie bez powtórek, postęp graczy, punktację, rzeczywisty zapis SQLite, import YAML oraz obsługę zdarzeń upraw, handlu, warzenia i napraw. Zdarzenia Paper są sprawdzane przy użyciu Mockito; te testy nie zastępują próby na działającym serwerze z klientem Minecraft i docelowymi pluginami.

[Lista kontrolna testów na serwerze](docs/TESTING.md).

## Dane i zgodność

- Dane są przechowywane w `plugins/Wyzwania/wyzwania.db`; nie jest wymagana zewnętrzna usługa.
- Kopię wykonuj po zatrzymaniu serwera, kopiując cały katalog `plugins/Wyzwania/`. Zachowaj zgodną kopię świata i danych graczy.
- Pełny ekwipunek nie usuwa nagrody — odbiór wymaga miejsca na cały pakiet.
- Przy przerwaniu odbioru w niepewnym momencie plugin wymaga decyzji administratora, aby nie wydać automatycznie pakietu drugi raz. [Procedura odzyskiwania](docs/ADMINISTRATION.md).
- Postęp jest naliczany w Survival/Adventure. Moby ze spawnerów są dozwolone; postawione rudy są śledzone, aby ograniczyć wielokrotne zaliczanie tego samego bloku.
- Brak bezpośredniej integracji z PlaceholderAPI i LuckPerms. Współpracę z pluginami czatu, TAB i ochrony terenu należy sprawdzić na docelowym serwerze.

## Zgłaszanie problemów

Do zgłoszenia dołącz wersję pluginu, build Paper, wersję Javy, kroki odtworzenia i odpowiedni fragment logu. Jeśli problem dotyczy konfiguracji, podaj jej istotną część oraz listę pluginów związanych z daną funkcją. Usuń dane prywatne przed opublikowaniem logów lub konfiguracji.

## Dokumentacja

- [Szczegółowa administracja, typy celów i konfiguracja](docs/ADMINISTRATION.md)
- [Testy odbiorcze na serwerze](docs/TESTING.md)
- [Domyślne zadania i nagrody](src/main/resources/config.yml)
- [Komendy i uprawnienia pluginu](src/main/resources/plugin.yml)
- [Konfiguracja budowania](build.gradle.kts)
