# Traccia GPS — revisione 2.2-beta1

Android nativo Java, Android 8+; applicationId `it.lorenzo.tracciagps.beta2`, build 4. **Questa consegna contiene soltanto sorgenti: nessun APK è stato prodotto.** L'app già installata non cambia finché non sarà compilata e installata una release successiva.

## La nuova home

La mappa si apre subito e occupa la parte principale della schermata. In alto: nome, versione effettiva dell'app installata e menu •••. In basso: distanza, tempo, punti GPS, Avvia/Ferma, Foto GPS, Salva punto e Galleria. Esportazioni, tema e aggiornamenti sono nel menu. Tema scuro iniziale, tema chiaro selezionabile; foto e aggiornamenti seguono la stessa grafica. Le tessere conservano i colori cartografici originali.

`docs/Anteprima-Grafica-v2.2.png` illustra l'impaginazione: non è uno screenshot Android. Mostra una tessera reale OpenTopoMap ingrandita a scopo dimostrativo.

## Cartografia online e problema di caricamento

La fonte iniziale è **OpenTopoMap**, senza chiavi incorporate; **Carte → Fonte online** permette anche OpenStreetMap. OpenTopoMap usa zoom 0–17, OpenStreetMap 0–19. La scelta e la posizione visualizzata vengono conservate. Trascina, usa +/− o il gesto di zoom. ◎ cerca la posizione GPS attuale; Carte consente di centrare sull'ultimo punto salvato.

Durante la verifica, il server OSM ha risposto con HTTP 200 e un PNG «Access blocked», accompagnato dall'header `X-Blocked`. La vecchia logica considerava qualunque PNG valido una mappa: il solo HTTP 200 non dimostrava che la cartografia fosse disponibile. Ora `X-Blocked`, HTTP 403 e 429 sono segnalati; dopo il blocco non vengono avviate altre richieste finché l'utente non ricarica o cambia fonte. Non vengono tentati aggiramenti del blocco. La causa precisa del mancato caricamento sul telefono non è confermata.

Le cache sono separate per fonte e aggiornate rispetto alla versione precedente, evitando di riutilizzare eventuali vecchie immagini di errore. Cache corrotta/scaduta viene riscaricata; gli errori di rete sono visibili. **Carte → Diagnostica connessione** prova la fonte scelta e mostra rete Android, risposta e problemi DNS/TLS/timeout. Soltanto le tessere visibili sono richieste, con tre connessioni al massimo e cache di sette giorni. Nessun download massivo di zone. Il fornitore riceve IP e area visualizzata, non tracce o foto. Disponibilità e copertura dei servizi pubblici non sono garantite.

Attribuzioni visibili e collegamenti alle licenze: dati © OpenStreetMap contributors e SRTM; stile © OpenTopoMap, CC-BY-SA. Fonti: https://opentopomap.org/about ; https://operations.osmfoundation.org/policies/tiles/ .

## Offline

**Carte → Importa MBTiles offline** accetta una mappa raster PNG/JPEG/WebP, Web Mercator, righe TMS, zoom 0–19, tessere fino a 1024 px. L'importazione verifica il file prima di sostituire quello precedente, attiva Offline e centra la zona importata. Lo zoom salta i livelli assenti nel file. Una sola mappa locale; aree senza tessere vengono segnalate, senza ripiego sulla rete. PBF, `.obf`, Mapsforge e GeoTIFF non sono leggibili direttamente.

Il file deve essere preparato/acquisito con diritto di uso offline; il server pubblico OSM non autorizza il prelievo di aree per offline. Verifica copertura e zoom in modalità aereo prima dell'uso.

## Traccia e foto GPS

Consenti posizione precisa e notifiche; per registrare a schermo spento controlla le restrizioni batteria Android e del produttore. Avvia usa un servizio in primo piano. Le tracce filtrano fix vecchi, precisione oltre 100 m e salti oltre 15 m/s; la distanza resta una stima. Le interruzioni oltre 60 secondi producono segmenti GPX distinti. Dopo un riavvio/terminazione forzata avvia una nuova traccia.

Foto GPS apre la fotocamera. La posizione viene acquisita **dopo il ritorno dalla fotocamera**, entro 30 secondi, precisione massima 50 m, e scritta in EXIF e JSON: non garantisce il luogo nell'istante esatto dello scatto. Le foto senza fix restano salvate. Una nuova acquisizione usa la posizione attuale. Galleria mostra le 12 foto più recenti; le foto sono private dell'app. Esporta il ZIP per conservarle.

## Esportazioni e conservazione

Il menu ••• esporta tracce/punti in GPX oppure tracce e tutte le foto in ZIP (GPX separato per le foto, collegamenti relativi, JPEG e JSON). Mantieni la struttura del ZIP. L'esportazione non è reimportabile nell'app e non include la cartografia locale. La mappa visualizza gli ultimi 20.000 punti; l'export conserva tutti i punti. La cancellazione delle tracce richiede conferma e conserva le foto. La disinstallazione elimina i dati privati: esporta prima.

## Versione, aggiornamenti e installazione

La versione visibile viene letta da Android, non da una stringa fissa dell'interfaccia. Tocca la versione o **••• → Versione e aggiornamenti**. Restano disponibili controllo aggiornamenti, download verificato e installazione con conferma Android, oppure selezione di un APK locale. Controllo automatico opzionale, massimo giornaliero.

La fonte è un URL HTTPS configurabile di `update.json`, inizialmente non configurato. Il download verifica SHA-256, package, versione, compatibilità Android e stessa firma; downgrade rifiutati, installazione bloccata durante una traccia. Non è stato pubblicato un server. Il pacchetto di sorgenti non include il vecchio update.json né APK obsoleti. Il pulsante di `downloads/index.html` rimane disabilitato finché non viene pubblicata una release reale con il relativo manifest.

La beta è separata dall'originale 1.0, la cui chiave non era disponibile. Una futura release deve usare la **stessa chiave delle beta 2.0/2.1** per aggiornarle mantenendo i dati. La chiave di prova è conservata separatamente, esclusa dai sorgenti da pubblicare; non generarne un'altra se vuoi aggiornare la beta già installata. Il flusso non gestisce la rotazione delle firme.

## `pubblica.html` e file sensibili

Non sono state trovate API key nei sorgenti. Il `pubblica.html` esaminato blocca i nomi di file che iniziano con `.env` o terminano con `.env`, oltre ai percorsi non sicuri. Il precedente archivio conteneva una chiave di firma di prova: ora sono esclusi tutte le chiavi, password letterali, APK e build. Non è dimostrato che quella chiave causasse il messaggio specifico, perché la regola esaminata riguarda i nomi `.env`. Le protezioni di pubblica.html non sono state indebolite.

`python3 tools/check-public.py /percorso/sorgenti.zip` controlla file riservati, materiali di firma e alcuni formati comuni di credenziali senza stamparne i valori. È un controllo mirato, non un riconoscitore universale di segreti. Se il blocco continua serve il testo esatto dell'errore e il nome del file segnalato.

## Verifiche di questa revisione

Compilazione Java contro Android API 35 superata. Test JVM GPX, filtri GPS, proiezione, URL/checksum/versioni, cache, zoom MBTiles e gestione blocchi HTTP superati. Test SQLite di recupero ed esportazione superati. Richiesta live effettuata dal medesimo client: vera tessera OpenTopoMap verificata visivamente; PNG di blocco OSM rifiutato. Audit finale del ZIP e delle regole di percorso del publisher.

**Nessuna compilazione DEX/APK, firma, installazione o test su telefono/emulatore per la 2.2.** La procedura Gradle non è stata eseguita. Grafica nativa, GPS, fotocamera, permessi, installer e MBTiles reali richiedono ancora verifica sul dispositivo. L'anteprima è un'illustrazione dell'impaginazione.

## Compilazione futura

Apri in Android Studio con JDK 17, SDK 35, AGP 8.7.3 e Gradle compatibile 8.9. Il progetto originale non aveva Gradle Wrapper. Lo script Linux opzionale `tools/build-apk.sh` richiede SDK/build-tools 35.0.0, JDK completo, Python e zip; legge la chiave esterna da `SIGNING_KEYSTORE`, password dalle variabili `SIGNING_STORE_PASSWORD`/`SIGNING_KEY_PASSWORD`, alias opzionale `SIGNING_KEY_ALIAS`. Non inserire questi valori nel repository. Lo script non è stato eseguito in questa revisione.

Dopo una futura compilazione firmata, `tools/make-update.py --apk /percorso/release.apk --url https://tuo-server/release.apk` genera update.json e hash dell'APK effettivo. Pubblica prima l'APK, poi JSON. I file su GitHub devono essere serviti via un vero URL HTTPS diretto (es. Pages), non dalla pagina di anteprima del codice.

Test JVM con JDK completo:

```bash
mkdir -p build/tests
javac -encoding UTF-8 -d build/tests app/src/main/java/it/lorenzo/tracciagps/{Gpx,GpsFilter,MapMath,UpdateRules,TilePolicy,TileTransport,TileSource}.java tests/{GpxTest,CoreTest,UpdateRulesTest,TilePolicyTest,TileTransportTest}.java
java -cp build/tests GpxTest build/tests/esempio.gpx
java -cp build/tests CoreTest
java -cp build/tests UpdateRulesTest
java -cp build/tests TilePolicyTest
java -cp build/tests TileTransportTest
python3 tests/store_test.py
```
