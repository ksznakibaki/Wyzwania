# Administracja i konfiguracja

Szczegółowe zasady działania pluginu Wyzwania. [Powrót do README](../README.md).

## Import nowych zadań po aktualizacji

Istniejący `config.yml` nie jest nadpisywany przy wymianie JAR-a. Po aktualizacji do 1.3.0 wykonaj **`/wyzwanie admin uzupelnij`**. Komenda dopisuje tylko brakujące identyfikatory zadań z domyślnej puli, zachowuje istniejące szablony, punktację i inne ustawienia, zapisuje plik oraz przeładowuje konfigurację. Jeśli nowe zadanie ma ten sam identyfikator co Twoje własne, zachowywane jest Twoje. Import przywraca także wcześniej usunięte domyślne szablony, więc jest operacją świadomie wywoływaną przez administratora, a nie automatyczną migracją.

Import nie zmienia aktualnych wyzwań i nie zeruje postępu. Nowa pula zostanie użyta przy następnym resecie; aby użyć jej od razu, wykonaj `/wyzwanie admin losuj wszystkie`. Ta druga komenda resetuje postęp wymienianych zadań. Zapis przez Bukkit może uporządkować formatowanie YAML i komentarze, dlatego przed importem zachowaj kopię configu.

## Przyznawanie punktów

Przykład: `/wyzwanie admin punkty Steve 150` dodaje 150 punktów do rankingu ogólnego i bieżącego miesiąca. Działa także z konsoli (bez `/`) i dla gracza offline, który wcześniej wszedł na serwer z pluginem. Nick jest rozpoznawany bez rozróżniania wielkości liter; przy niejednoznacznych zapisach podaj UUID. Nieznani gracze są odrzucani bez tworzenia pustych kont.

Liczba musi być dodatnią liczbą całkowitą. Wynik jest natychmiast zapisywany w SQLite, a operacja odnotowywana w logu z administratorem, graczem i liczbą punktów. Gracz online dostaje powiadomienie, odświeżenie prefixu i TAB. Wybrany ręcznie niższy prefix pozostaje wybrany; opcja `auto` pokaże najwyższy odblokowany. Komenda nie zalicza wyzwań i nie wydaje przedmiotów ani XP.

## Resety i zachowanie postępu

### Losowanie przez administratora

`/wyzwanie admin losuj [dzienne|tygodniowe|wszystkie]` działa także z konsoli. Bez ostatniego argumentu wymienia obie grupy. Zmiana obejmuje wszystkich graczy: **zeruje postęp wyłącznie wymienionych zadań**, a pozostałe zadania, zdobyte punkty i zaległe nagrody zachowuje. Nowe zadania otrzymują nowe identyfikatory, więc ponowne losowanie tego samego szablonu w późniejszym losowaniu nie odziedziczy starego postępu ani stanu odbioru.

Dzienne losowanie wyklucza wszystkie trzy obecne szablony; tygodniowe wyklucza aktualny szablon tygodniowy. Aby ręcznie wymienić tygodniowe, potrzebne są co najmniej dwa szablony WEEKLY. Jeżeli brak alternatyw, komenda odmawia całej zmiany i pozostawia wszystkie zadania oraz postęp bez zmian. Sukces natychmiast zapisuje dane, aktualizuje TAB, informuje graczy i zapisuje administratora w logu. Godziny kolejnych resetów pozostają zgodne z konfiguracją. Ręczne losowania umożliwiają zdobywanie punktów z dodatkowych zadań w tym samym dniu.

### Automatyczny reset

Domyślny reset dzienny następuje o **04:00 w strefie `Europe/Warsaw`**. Tygodniowy: **poniedziałek o 04:00**. Uwzględniana jest zmiana czasu letniego/zimowego.

- Restart w tym samym okresie przywraca dokładnie te same zadania i nagrody.
- Przy resecie dziennym **żaden z trzech szablonów z poprzedniego zestawu nie może wystąpić w nowym**. Wykluczenie korzysta z zadań zapisanych w SQLite, więc działa również po restarcie lub przerwie serwera. Ten sam typ aktywności może wrócić z innym zadaniem, np. innym mobem lub celem wydobycia.
- Po przerwie obejmującej reset losowany jest zestaw dla bieżącego okresu. Nie powstają zadania za pominięte dni.
- Reset dzienny nie zmienia tygodniowego zadania ani jego postępu.
- Nieukończone zadanie wygasa przy swoim resecie. Ukończona, nieodebrana nagroda pozostaje zapisana bez limitu czasu.
- Punkty przyznawane są **raz, przy ukończeniu**; przedmioty i XP odbiera się ręcznie.
- Jedna czynność może jednocześnie zwiększać postęp w zadaniu dziennym i tygodniowym.
- Miesiąc rankingowy także używa granicy dnia o 04:00. Ranking miesięczny nie odbiera punktów ogólnych ani tytułów. Historia poprzednich rankingów miesięcznych nie jest przechowywana.
- Zmiana puli zadań, przedmiotów i XP dotyczy **kolejnego losowania**. Wyjątkiem jest `points`: nowa punktacja stosowana jest do aktywnych zadań po reloadzie lub starcie pluginu. Nie przelicza wcześniej zdobytych punktów.

## Zasady naliczania

Postęp i odbiór nagród działają wyłącznie w **Survival/Adventure**, przy uprawnieniu `wyzwania.use` i w dozwolonym świecie.

| Typ | Co jest liczone |
| --- | --- |
| `KILL` | Zabicia przypisane graczowi, również moby ze spawnerów. `ANY` oznacza wrogie moby (`Enemy`), np. zombie; konkretny typ może wskazać inną istotę. PvP ma osobny typ. |
| `MINE` | Zniszczone bloki, po 1 za blok, bez mnożnika Fortune. Odmiany deepslate wpisuje się jako osobne materiały w tym samym celu. |
| `CRAFT` | Faktycznie wytworzone i odebrane sztuki, w tym Shift+klik. Automatyczne craftery nie naliczają postępu, bo nie mają przypisanego odbierającego gracza. |
| `EAT` | Faktycznie spożyty jadalny przedmiot. Wypicie mikstury lub mleka nie jest jedzeniem. |
| `EAT_UNIQUE` | Różne materiały faktycznie zjedzone w czasie zadania. Kolejny stek nie zwiększa licznika różnorodności. Lista jedzenia jest osobna dla każdego celu i zachowuje się po restarcie. |
| `BIOME` | Różne typy biomów odwiedzone w czasie zadania. Sprawdzanie co sekundę, także dla pasażerów. Liczy się również biom, w którym gracz zaczyna zadanie; teleport do biomu jest wizytą, ale nie zwiększa dystansu `MOVE`. Ten sam biom w innym miejscu lub świecie nie liczy się ponownie. |
| `HARVEST` | Dojrzałe bloki pszenicy, marchwi, ziemniaków, buraków, nether wart i kakao zebrane przez zniszczenie, oraz krzewy słodkich jagód zebrane prawym przyciskiem. Jeden zbiór bloku = 1, niezależnie od liczby przedmiotów. Jagody liczą się przy wieku co najmniej 2; pozostałe uprawy wymagają maksymalnego wieku. Zbiory automatyczne, niedojrzałe rośliny, trzcina, dynie i melony nie są liczone. |
| `BREW` | Liczba butelek z efektem zmienionych przez ukończony proces warzenia. Woda, niezręczna, gęsta i zwyczajna mikstura nie liczą się. Ulepszanie czasu/siły oraz zamiana na miksturę miotaną lub trwałą także liczą się jako kolejny proces. |
| `ANVIL_REPAIR` | Odebrane naprawy uszkodzonego przedmiotu na kowadle, które zmniejszyły zużycie i zużyły wejściowy przedmiot. Sama zmiana nazwy, podgląd wyniku lub dodanie enchantu bez naprawy nie liczą się. Jedna naprawa = 1. |
| `TRADE` | Zrealizowane transakcje z osadnikiem lub wędrownym handlarzem, po 1 za wymianę, niezależnie od liczby przedmiotów w wyniku. Sam wybór oferty nie liczy się. |
| `MOVE` | Poziomy dystans pieszo, sprintem, łódką i na zwierzętach; liczą się też pasażerowie. Bez elytry, teleportów, lotu, pływania, riptide, wagoników i przeskoków między światami. |
| `FISH` | Jeden punkt za udany połów. `FISH`: dorsz, łosoś, rozdymka lub ryba tropikalna. `TREASURE`: łuk, książka zaklęć, wędka, name tag, muszla łodzika lub siodło. `ANY`: każdy udany połów, także śmieci. |
| `BREED` | Jedno udane rozmnożenie przypisane graczowi, niezależnie od liczby rodziców. |
| `SHEAR` | Jedno udane strzyżenie przypisane graczowi, nie liczba uzyskanej wełny. |
| `PVP_KILL` | Zabicie innego gracza z cooldownem dla pary atakujący–ofiara. |
| `PVP_DAMAGE` | Obrażenia po redukcjach, maksymalnie do pozostałego zdrowia ofiary; 1 punkt obrażeń = pół serduszka. Cooldown dla pary ogranicza naliczanie przy ustawianych walkach. |

Zdarzenia anulowane przez ochronę terenu lub inne pluginy nie zwiększają postępu. Zabicia środowiskowe bez przypisanego gracza nie są zaliczane.

### Przypisywanie warzenia graczowi

Graczem warzącym jest ostatnia osoba, która **ręcznie dołożyła składnik do górnego slotu statywu**. Przypisanie UUID i materiału składnika jest zapisywane na statywie, więc może przetrwać restart. Zasilanie składnikiem przez hopper usuwa ręczne przypisanie, jeśli zwiększy lub zmieni zawartość tego slotu; sam hopper z butelkami lub paliwem nie przypisuje nowego gracza. Zmiany slotów są odczytywane w następnym ticku.

W chwili zakończenia procesu gracz musi być online, w tym samym świecie i spełniać zwykłe warunki naliczania. Włożenie gotowych mikstur ani ich wyjęcie nie daje punktów. Trzy przygotowane butelki mogą dać trzy jednostki postępu. Zawartość statywu wstawiona przed instalacją tej funkcji wymaga ponownego ręcznego dołożenia składnika. Nie jest to pełna ochrona przed półautomatycznymi farmami: świadomie liczymy dalsze procesy z ręcznie dostarczonego stosu składnika.

### Zabezpieczenie rud

Plugin śledzi lokalizacje rud (`*_ORE`) oraz ancient debris postawionych przez graczy, także w Creative i wyłączonych światach. Wykopanie takiego bloku nie daje postępu. Oznaczenia przesuwane są z blokami przez tłoki i usuwane przy zwykłym zniszczeniu lub eksplozji. Zapis pozostaje po restarcie.

Ochrona działa od instalacji pluginu: nie rozpozna rud postawionych wcześniej. Masowe zmiany przez WorldEdit, komendy i narzędzia edytujące świat bez standardowych zdarzeń Bukkit nie są objęte pełną ochroną. To proste zabezpieczenie survivalowe, nie kompletny system antycheat. Dystans nie wykrywa automatycznych tras AFK.

## Konfiguracja

Plik: `plugins/Wyzwania/config.yml`. Domyślna konfiguracja znajduje się także w `src/main/resources/config.yml`.

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

- `worlds: []`: wszystkie światy. Aby ograniczyć naliczanie i odbiór: `worlds: [world, world_nether, world_the_end]`.
- `autosave-seconds`: 5–3600 sekund. Zwykły postęp zapisywany jest okresowo i podczas poprawnego wyłączenia. Ukończenia, punkty i etapy odbioru mają dodatkowe natychmiastowe zapisy.
- `pvp.enabled`: włącza naliczanie PvP oraz dopuszcza szablony PvP do kolejnych losowań. Wyłączenie w trakcie trwania już wylosowanego zadania PvP zatrzymuje jego naliczanie do wygaśnięcia.
- Cooldowny PvP: 0–86400 sekund, osobno zabicia i obrażenia. Przy domyślnych 30 sekundach zadanie obrażeń wymaga wielu oddzielnych wymian ciosów.
- `movement.max-step`: maksymalny dystans poziomy jednej próbki (co tick). Zbyt niski próg może pomijać szybkie łódki; większe przeskoki są odrzucane w całości.
- `chat.enabled`: ustaw `false`, jeżeli własny plugin czatu formatuje tytuły. Nie ma bezpośredniej integracji z LuckPerms ani PlaceholderAPI. Włączona obsługa dodaje prefix do nazwy i przekazuje ją istniejącemu rendererowi; końcowy efekt zależy od innych pluginów czatu.
- `announcements.completion`: ogłasza wszystkim online nazwę gracza i ukończonego zadania oraz informację, czy było dzienne czy tygodniowe. Ogłoszenie następuje raz, przy zapisanym ukończeniu, a nie przy odbiorze nagrody. Prywatna wiadomość o punktach i odbiorze pozostaje dostępna także po wyłączeniu ogłoszeń.
- `tab.enabled`: pokazuje własny postęp gracza w stopce TAB; nie wymaga modów klienta. Każdy cel tygodniowy ma osobny licznik. Wyświetla też punkty i liczbę nagród gotowych do odbioru. Nagłówek oraz lista graczy pozostają bez zmian, a istniejąca stopka jest zachowywana nad wyzwaniami.
- `tab.refresh-seconds`: odświeżanie TAB co 1–60 sekund, domyślnie co 2. Ukończenie zadania aktualizuje widok od razu. Jeśli inny plugin stale zarządza stopką TAB, wyłącz tę funkcję przez `tab.enabled: false` i `/wyzwanie reload`, żeby uniknąć wzajemnego nadpisywania. Wyłączenie przywraca poprzednią stopkę, o ile nadal jest wyświetlana stopka tego pluginu.

Przy aktualizacji ze starego `config.yml` brak nowych sekcji oznacza domyślne włączenie ogłoszeń i TAB (co 2 sekundy). Możesz dopisać sekcje ręcznie; plugin nie nadpisuje istniejącego pliku.

Nieprawidłowa konfiguracja podczas startu zatrzyma uruchomienie pluginu i poda przyczynę w logu. Podczas `/wyzwanie reload` błędny plik zostaje odrzucony, a poprzednie ustawienia nadal działają. Plik nie zostanie automatycznie zastąpiony ustawieniami domyślnymi.

### Dodawanie zadań

```yaml
quests:
  moje_diamenty:
    tier: HARD
    title: 'Diamentowa wyprawa'
    objectives:
      - type: MINE
        target: 'DIAMOND_ORE,DEEPSLATE_DIAMOND_ORE'
        goal: 16
        label: 'Wykop rudy diamentu'
```

Ten wpis dodaj do istniejącej sekcji `quests` — nie twórz drugiej sekcji o tej samej nazwie.

- `tier`: `EASY`, `MEDIUM`, `HARD`, `WEEKLY`.
- Dzienne szablony mają dokładnie jeden cel. Tygodniowe mają 1–10 celów; trzeba wykonać wszystkie.
- `type`: jeden z typów z tabeli naliczania.
- `target`: identyfikator materiału/istoty, lista rozdzielona przecinkami albo `ANY`. Przy rybactwie: `FISH`, `TREASURE`, `ANY`. Przy dystansie i PvP: `ANY`.
- Przy `EAT_UNIQUE`: `ANY` lub materiały jadalne. Przy `BIOME`: `ANY`, nazwa taka jak `DESERT` lub klucz `minecraft:desert` (może być lista). Przy `HARVEST`: nazwy **bloków**, np. `WHEAT,CARROTS,POTATOES`, a nie `WHEAT_SEEDS` czy `CARROT`. Przy `BREW`: `ANY` lub typ mikstury, np. `FIRE_RESISTANCE` albo `LONG_FIRE_RESISTANCE`. Przy `TRADE`: `ANY`, `VILLAGER`, `WANDERING_TRADER`. Przy `ANVIL_REPAIR`: `ANY`.
- `goal`: dodatni próg, maksymalnie 10 000 000. Progi w szablonach są stałe; losowany jest szablon, nie wartość progu.
- Materiały i istoty wpisuj identyfikatorami API, np. `IRON_ORE`, `ZOMBIE`, `SHEEP`.
- Pula musi umożliwiać **dwa rozłączne zestawy** EASY/MEDIUM/HARD, każdy z trzema różnymi typami aktywności. Oznacza to co najmniej dwa różne szablony dla każdego poziomu dziennego; sama liczba nie wystarczy, jeżeli kolidują typy. Losowanie wyklucza bieżące trzy szablony oraz nie wybiera zestawu, dla którego zabrakłoby alternatywy następnego dnia. Domyślna pula spełnia te warunki. Wymagany jest też co najmniej jeden szablon tygodniowy; dwa, jeśli chcesz ręcznie wymieniać tygodniowe. Szablony PvP są pomijane, gdy PvP jest wyłączone.
- Za mała pula jest odrzucana z opisem błędu przy starcie lub reloadzie. Błędny reload zachowuje poprzednią konfigurację. Reload dodatkowo sprawdza dostępność alternatywy dla aktualnych zadań. Nie zastępuj różnych zadań kopiami o innych identyfikatorach — wykluczenie działa według identyfikatora szablonu.
- Nie zmieniaj identyfikatorów szablonów bez potrzeby. Aktywne zadania przechowywane są jako pełne kopie w bazie.

### Nagrody i szanse

Każdy poziom ma osobną sekcję `rewards.EASY`, `MEDIUM`, `HARD`, `WEEKLY`, zawierającą `xp-min`, `xp-max`, `points` oraz `pool`.

Przykład: zmień `rewards.HARD.points` na `35` w **serwerowym** `plugins/Wyzwania/config.yml` i użyj `/wyzwanie reload`. Aktywne trudne zadanie pokaże 35 pkt i tyle przyzna za nowe ukończenie. Nie trzeba usuwać bazy, resetować zadań ani czekać do następnego dnia. `points` może wynosić 0–10000. Edycja pliku źródłowego projektu `src/main/resources/config.yml` nie modyfikuje konfiguracji już uruchomionego serwera.

Przykładowy wpis w `rewards.WEEKLY.pool`:

```yaml
star:
  label: 'Gwiazda Netheru'
  weight: 1
  items:
    - material: NETHER_STAR
      amount: 1
```

Szansa = waga / suma wag w danej puli. Domyślna pula tygodniowa ma sumę wag 13, więc gwiazda ma szansę **1/13**. Jeden pakiet jest losowany wraz z zadaniem, wspólny dla wszystkich graczy, i widoczny przed jego ukończeniem.

W `items` można umieścić 1–36 stosów. `amount` nie może przekroczyć maksymalnego stosu danego przedmiotu. Większą ilość rozbij na osobne wpisy. Książka: `material: ENCHANTED_BOOK`, `enchantment: 'minecraft:mending'`, `level: 1`. Mikstura: `material: POTION`, `potion: LONG_FIRE_RESISTANCE`.

Domyślne punkty: **10 za każde dzienne**, **50 za tygodniowe**. XP jest dodatkiem do każdego pakietu. Maksymalnie daje to **260 punktów tygodniowo**. Nagrody nie zawierają waluty.

### Prefixy

```yaml
prefixes:
  nowicjusz: {points: 0, text: '<gray>[Nowicjusz]</gray>'}
  lowca: {points: 100, text: '<green>[Łowca]</green>'}
  weteran: {points: 500, text: '<aqua>[Weteran]</aqua>'}
  mistrz: {points: 1000, text: '<light_purple>[Mistrz]</light_purple>'}
  legenda: {points: 2500, text: '<gold>[Legenda]</gold>'}
```

Pierwszy próg musi wynosić 0. Tekst obsługuje MiniMessage. Gracz może wybrać wcześniej odblokowany tytuł albo `auto`. Prefixy nie zmieniają uprawnień ani nazwy konta.

## Zapis, kopie i przerwany odbiór

Baza znajduje się w `plugins/Wyzwania/wyzwania.db`. Przy pracy mogą występować pliki `wyzwania.db-wal` i `wyzwania.db-shm`. **Najprostsza poprawna kopia: wyłącz serwer przez `stop`, a następnie skopiuj cały katalog `plugins/Wyzwania/`.** Przywracanie również wykonuj przy zatrzymanym serwerze. Zachowaj zgodną kopię świata i danych graczy.

Zwykły postęp jest zapisywany co 30 sekund oraz przy wyjściu gracza i poprawnym wyłączeniu. Przy nagłym zabiciu procesu może zginąć ostatni niezapisany postęp. Zapis rud jest kolejkowany natychmiast po zmianie. Błąd bazy wstrzymuje plugin zamiast kontynuować wydawanie nagród bez zapisu.

Przy odbiorze wymagane jest miejsce na **cały pakiet**. Przy braku miejsca nic z niego nie jest wydawane, a nagroda czeka. Komenda próbuje wydawać pakiety po kolei; brak miejsca zatrzymuje dalszy odbiór.

SQLite i ekwipunek Minecrafta nie mają wspólnej transakcji. Aby nie duplikować nagród po awarii, odbiór przebiega przez zapisane stany `READY` → `DELIVERING` → `CLAIMED`. Przedmioty i XP są wydawane po utrwaleniu `DELIVERING`, następnie zapisywane są dane gracza i potwierdzenie odbioru.

Jeśli proces przerwie się w niepewnym momencie, plugin **nie ponawia odbioru automatycznie**. Administrator:

1. Wykonuje `/wyzwanie admin niepewne` i kopiuje UUID oraz ID nagrody.
2. Sprawdza dane gracza, logi i czy przedmioty/XP zostały wydane.
3. Jeśli odbiór się powiódł: `/wyzwanie admin rozwiaz <UUID> <ID> potwierdz`.
4. Jeśli pakiet nie został wydany: `/wyzwanie admin rozwiaz <UUID> <ID> ponow`; gracz ponownie używa `/wyzwanie odbierz`.

Decyzja jest zapisywana w logu. Ponowienie nie dodaje ponownie punktów rankingowych, ale może powielić przedmioty/XP, jeżeli administrator błędnie oceni poprzedni odbiór. Ewentualny częściowy odbiór trzeba najpierw rozliczyć ręcznie.


