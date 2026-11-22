# Incognito

Plugin na **Paper 1.20+**: `/incognito` daje losowy nick (np. `ukryty16245`, `macius1246pl`)
i prefix incognito zamiast rangi. **Uprawnienia zostaja bez zmian** - plugin rusza tylko nick i prefix.

## Instalacja

1. Wejdz w zakladke **Releases** -> **Incognito (najnowszy build)** i pobierz `Incognito.jar`
   (albo w **Actions** -> ostatni build -> artefakt `Incognito`).
2. Wrzuc plik do folderu `plugins/` i zrestartuj serwer.
3. Daj sobie uprawnienie: `/lp user TwojNick permission set incognito.use true` (albo op).

## Komendy

| Komenda | Opis |
|---|---|
| `/incognito` | wlacza (losowy nick) / wylacza |
| `/incognito gui` | menu z historia nickow, losowaniem i przyciskiem wlacz/wylacz |
| `/incognito on` / `off` | wlacz / wylacz |
| `/incognito nick <nick>` | wlasny nick (`incognito.nick`) |
| `/incognito reload` | przeladowanie configu (`incognito.admin`) |

Alias: `/inc`. W menu klikniecie starego nicku z historii wlacza go ponownie.

## Uprawnienia

`incognito.use`, `incognito.nick`, `incognito.admin` (domyslnie tylko op).

## Co sie zmienia dla innych graczy

- czat: `prefix + nick: wiadomosc`
- tab: prefix + nick
- nick nad glowa i w liscie graczy (Paper, `change-real-name: true`; skin zostaje)
- komunikaty wejscia/wyjscia z serwera

Prefix, lista slow do nickow, szansa na koncowke `pl` i komunikaty sa w `plugins/Incognito/config.yml`.

## Ograniczenia

- Plugin czatu (EssentialsChat, LPC itp.) moze nadpisywac format czatu - wtedy zmien jego priorytet/format.
- Prawdziwy nick moze byc widoczny w komendach typu `/msg` oraz w logach konsoli.
- Zbudowany pod Paper API 1.20.4; zmiana nicku nad glowa korzysta z API Papera (nie dziala na Spigocie).

## Budowanie lokalnie

```
mvn package
```

Wynik: `target/Incognito.jar`.
