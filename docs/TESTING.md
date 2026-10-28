# Testy na serwerze Paper

Lista kontrolna do weryfikacji pluginu na kopii serwera z docelowymi pluginami. Testy automatyczne opisano w [README](../README.md#testy).

## Test odbiorczy na serwerze Paper

Przed użyciem na właściwym świecie sprawdź na kopii serwera:

1. `/wyzwanie` pokazuje obu graczom te same zadania i nagrody, a liczniki postępu pozostają indywidualne.
2. Restart zachowuje zadania, progi, nagrody, punkty i częściowy postęp.
3. Przy testowych progach 1–3 ukończ zadanie i sprawdź, że punkty oraz nagroda nie przyznają się drugi raz.
4. Sprawdź pełny ekwipunek, odbiór po resecie i kolejny restart po odebraniu.
5. Sprawdź naturalną rudę, Fortune, rudę postawioną, restart oraz przesunięcie postawionej rudy tłokiem w obie strony.
6. Zabij moby naturalne i ze spawnera; anulowane kopanie/obrażenia nie powinny zwiększać postępu.
7. Sprawdź zwykły crafting, Shift+klik i brak miejsca na wynik. Automatyczny crafter nie powinien naliczać postępu.
8. Sprawdź spacer, sprint, łódkę, konia, teleport o kilka bloków, lot elytrą i zmianę świata.
9. Na osobnej bazie testowej ustaw reset na najbliższą minutę i sprawdź odświeżanie. Tygodniowe nie powinno zmieniać się przy resecie dziennym poza poniedziałkiem.
10. Sprawdź współpracę prefixów z używanym pluginem czatu, a jeżeli włączasz PvP — także cooldowny tej samej ofiary.
11. Ukończ zadanie przy dwóch graczach online: obaj powinni dostać jedno publiczne ogłoszenie, a wykonawca dodatkowo prywatną informację. Kolejne czynności i odbiór nie powtarzają ogłoszenia.
12. Sprawdź pod TAB różne postępy obu graczy, wszystkie cele tygodniowe, odświeżanie oraz wyłączenie widoku przez config i `/wyzwanie reload`.
13. Zmień ustawienie ogłoszeń i częstotliwość TAB, wykonaj reload jako admin i sprawdź zachowanie częściowego postępu oraz nagród. Gracz bez `wyzwania.admin` nie może przeładowywać konfiguracji.
14. Wprowadź błąd w konfiguracji i wykonaj reload: ma pokazać błąd i zachować poprzednie działające ustawienia. Potem popraw plik.
15. Zmień `rewards.EASY.points`, wykonaj reload i sprawdź wartość w `/wyzwanie`. Ukończ częściowo wykonane zadanie: ma przyznać nową liczbę punktów bez zmiany przedmiotów/XP. Sprawdź też zmianę `points` przy wyłączonym serwerze i ponowny start. Wcześniej ukończone zadanie nie może ponownie przyznać punktów.
16. Przyznaj punkty komendą administratora graczowi online i offline. Sprawdź oba rankingi, powiadomienie, prefix, TAB i zachowanie wyniku po restarcie. Nieznany nick, zero, liczba ujemna i brak uprawnień muszą zostać odrzucone.
17. Sprawdź reset dzienny: nowe trzy szablony mają różnić się od poprzednich. Powtórz próbę z restartem przed resetem; tygodniowe powinno zachować postęp poza terminem jego resetu.
18. Sprawdź `/wyzwanie admin losuj dzienne`, `tygodniowe` i `wszystkie`: resetowana jest tylko wskazana grupa, zaległe nagrody i punkty pozostają. Kilka losowań tego samego dnia nie może przywracać dawnych postępów. Gracz bez uprawnień nie może użyć komendy.
19. Dla `EAT_UNIQUE` i `BIOME` sprawdź, że powtórzenie tej samej potrawy/biomu nie zwiększa licznika, również po restarcie; nowe zadanie zaczyna osobną listę.
20. Sprawdź dojrzałą i niedojrzałą uprawę, zbiór jagód, pełną transakcję oraz samo oglądanie oferty osadnika.
21. Uwarz trzy mikstury po ręcznym włożeniu składnika, porównaj z wodą/niezręczną miksturą i hopperem dokładającym składnik. Wyjmowanie gotowej mikstury nie może zwiększać postępu.
22. Napraw uszkodzone narzędzie na kowadle, porównaj z samą zmianą nazwy, podglądem i brakiem XP/miejsca w ekwipunku. Zweryfikuj także anulowanie przez używane pluginy ochronne.


