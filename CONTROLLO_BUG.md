# Controllo bug — 2.2-beta1, build 4

| Problema riscontrato | Intervento |
|---|---|
| Home lunga, mappa nascosta dietro un comando | Mappa incorporata nella prima schermata; metriche e azioni principali compatte. |
| PNG «Access blocked» con HTTP 200 scambiato per cartografia | Controllo X-Blocked e HTTP 403/429, messaggio visibile, sospensione richieste dopo blocco. |
| Una sola fonte online | OpenTopoMap iniziale, selettore OpenStreetMap; attribuzioni, zoom massimo e cache distinti. |
| Cache corrotta/scaduta poteva lasciare tessere vuote | Validazione, eliminazione e nuova richiesta; cache v2 non riusa le vecchie tessere. |
| Errori di caricamento nascosti | Stato, diagnostica della stessa fonte e messaggi HTTP/DNS/TLS/timeout. |
| Richieste vecchie interferivano col cambio modalità | Generazione per richiesta, reset cache memoria, annullamento connessioni obsolete. |
| MBTiles aperto lontano dall'area e a zoom non presente | Centro da metadata o tessera, zoom disponibili con salto dei livelli assenti; Offline attivato all'importazione. |
| Archivio distribuibile includeva chiave di prova e APK | Nuovo ZIP solo sorgenti, firma esterna, nessuna password letterale nel build script. |

## Esito

Compilazione Java Android API 35 e test GPX/Core/UpdateRules/TilePolicy/TileTransport/SQLite superati. Test risposta HTTP 200 con X-Blocked, 403/429, errore server e HTTP non cifrato. OpenTopoMap: richiesta HTTPS live e verifica visiva di cartografia reale. OSM: riprodotta immagine di blocco con HTTP 200, ora rifiutata dal client condiviso.

Nessun APK, DEX o firma prodotti in questa revisione. Nessun test su dispositivo/emulatore. L'anteprima grafica illustra il layout e non certifica la UI Android. Il blocco OSM riprodotto nell'ambiente di verifica non prova la causa precisa sul telefono.

## Verifica pubblicazione

Il pubblica.html esaminato rifiuta `.env*`, `*.env` e percorsi non sicuri. Il ZIP finale viene verificato contro queste regole e controllato con check-public.py; non sono state rilevate API key. La vecchia chiave di firma di prova è esclusa, ma non è stata identificata come causa certa del messaggio. Se il problema continua serve il testo esatto dell'errore. Nessun aggiramento delle protezioni.

## Da provare nella futura release Android

1. Prima apertura, mappa senza permesso GPS, cambio fonte/tema, trascinamento e zoom.
2. Rete assente, ripristino rete, diagnostica, ricarica manuale dopo un blocco.
3. Importazione MBTiles reale, centro e zoom; modalità aereo; importazione invalida conservando la precedente.
4. Posizione ◎ con autorizzazione precisa, rifiuto permesso e timeout.
5. Traccia a schermo spento, foto con e senza fix, rotazione, export GPX/ZIP.
6. Aggiornamento firmato con la stessa chiave beta e conferma dell'installer Android.

Limiti: una mappa offline raster, nessuna importazione GPX/ZIP, nessuna migrazione dati dall'originale 1.0, foto geolocalizzate dopo lo scatto, statistiche su tutte le sessioni, nessuna eliminazione per singola escursione. Le letture SQLite restano sul thread UI: archivi molto grandi possono rallentare.
