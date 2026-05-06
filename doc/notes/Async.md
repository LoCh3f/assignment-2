

## 1. Introduzione alla Programmazione Asincrona

Nell'ingegneria del software contemporanea, la programmazione asincrona si è evoluta da semplice tecnica di gestione dell'I/O a paradigma architetturale dominante. Essa rappresenta una strategia fondamentale per l'astrazione dei thread di sistema, permettendo la gestione di computazioni e processi distribuiti senza vincolare le risorse del processore all'attesa passiva di eventi esterni.

### L'Evoluzione dei Pattern e la Stabilità dei Framework

L'evoluzione dei pattern asincroni, particolarmente documentata nell'ecosistema Microsoft .NET, illustra una transizione critica verso astrazioni di livello sempre più elevato. Il passaggio dall'**Asynchronous Programming Model (APM)** all'**Event-based Asynchronous Pattern (EAP)**, fino all'attuale **Task-based Asynchronous Pattern (TAP)**, non è meramente estetico. L'introduzione del TAP ha fornito un'astrazione unificata (il `Task` o `Future`), trasformando l'operazione asincrona in un "oggetto di prima classe" (first-class object). Questo cambiamento è vitale per la stabilità e la manutenibilità dei framework moderni (Java, .NET, iOS, Android), poiché consente una composizione modulare delle operazioni che i modelli precedenti, basati su frammentazione di callback o eventi sparsi, rendevano estremamente fragile.

I meccanismi cardine del panorama attuale includono:

- **Event-driven programming:** Architetture basate su loop di controllo e Reactor pattern.
- **Async Functions (CPS):** Funzioni in cui il controllo è passato esplicitamente tramite continuazioni.
- **Async/Await:** Costrutti sintattici che mimano il flusso sincrono pur mantenendo un core asincrono.
- **Coroutine:** Meccanismi di multitasking cooperativo per la gestione di thread leggeri.

La comprensione di questi modelli è il prerequisito analitico per affrontare il dibattito storico tra eventi e thread.

--------------------------------------------------------------------------------

## 2. Il Dibattito Architetturale: Eventi vs. Thread

La scelta tra un'architettura basata su thread e una basata su eventi è stata oggetto di un lungo dibattito accademico. Se John Ousterhout (1996) sosteneva che i thread fossero intrinsecamente complessi per la maggior parte degli scopi, von Behren et al. (2003) evidenziavano i limiti del modello a eventi nei server ad alta concorrenza. Tuttavia, l'analisi di **Adya et al. (2002)** ha chiarito che la programmazione event-driven non è l'opposto polare della programmazione a thread; la distinzione risiede piuttosto nel modo in cui vengono gestiti lo stato e lo stack. La sfida architetturale moderna è definibile come "Cooperative Task Management without Manual Stack Management".

### Implicazioni del Non-Determinismo e del Call Stack

Il modello event-driven opera in un regime di **non-determinismo**, dove il flusso è governato dall'accadimento di eventi ambientali. Una caratteristica distintiva è la **programmazione senza call stack**: al termine dell'esecuzione di ogni handler, lo stack di chiamata si svuota completamente. Poiché gli handler sono eseguiti in modo atomico dall'event loop, non vi è concorrenza interna durante l'elaborazione di un singolo evento, il che garantisce una reattività superiore a patto di rispettare rigorosi vincoli di esecuzione.

### L'Astrazione dell'Event Loop

L'Event Loop rappresenta l'architettura di controllo single-threaded che orchestra il sistema:

```javascript
loop {
   Event ev = waitForEvent(eventQueue); // Attesa su coda eventi
   Handler handler = selectHandler(ev); // Selezione logica
   execute(handler); // Esecuzione atomica: lo stack deve svuotarsi al termine
}
```

--------------------------------------------------------------------------------

## 3. Il Reactor Pattern e l'Architettura di Dispacciamento

Formalizzato da Douglas Schmidt, il **Reactor Pattern** è il pilastro per il demultiplexing e il dispacciamento degli handle. Esso permette di gestire richieste simultanee provenienti da molteplici sorgenti senza l'oneroso **avvicendamento di contesto (context switching)** tipico dei thread preemptive.

### Initiation Dispatcher, Acceptor e Handler

L'architettura si articola su componenti specializzati:

1. **Handle:** Risorse di sistema (es. socket) che fungono da chiavi per identificare le sorgenti di eventi.
2. **Initiation Dispatcher:** Il cuore del sistema che innesca i metodi hook degli handler.
3. **Event Handler:** L'interfaccia che definisce il comportamento applicativo.

In uno scenario di **Logging Server Reattivo**, la distinzione tra i ruoli è netta:

- Il **Logging Acceptor** è l'entità dedicata alla fase di connessione: riceve la richiesta, crea un nuovo _Logging Handler_ e registra l'handle del socket presso il dispatcher.
- Il **Logging Handler** si occupa esclusivamente dei record di logging successivi. Quando il dispatcher rileva dati pronti sull'handle, richiama il metodo hook dell'handler associato.

--------------------------------------------------------------------------------

## 4. La "Never-Blocking Rule" e la Starvation del Sistema

La regola aurea dei sistemi asincroni è il **Never-Blocking**: un handler non deve mai eseguire chiamate bloccanti o cicli infiniti. Poiché l'intero sistema poggia su un singolo thread di controllo, una violazione di questa regola causa la **starvation** (indisponibilità) dell'intera coda di eventi.

### Impatto Sistemico della Sostituzione Bloccante

Quando l'event loop è occupato in un compito pesante o bloccante, l'applicazione perde la capacità di disegnare la grafica, gestire input utente (click, tastiera) o reagire all'I/O di rete. Sostituire chiamate bloccanti con richieste asincrone significa delegare il compito a thread esterni (spesso gestiti dal kernel o da thread pool dedicati) che, al completamento, inseriranno un evento nella coda, permettendo all'applicazione di riprendere il lavoro solo quando il loop sarà libero.

--------------------------------------------------------------------------------

## 5. Callback Model e Continuation Passing Style (CPS)

Per gestire i risultati delle operazioni asincrone senza bloccare il flusso, si ricorre al **Continuation Passing Style (CPS)**, introdotto in Scheme nel 1975. In questo modello, il controllo è passato esplicitamente tramite una "continuazione" (callback), eliminando la dipendenza dallo stack tradizionale.

### Closure e Gestione dello Stato sull'Heap

Le **closure** sono lo strumento tecnico fondamentale del CPS. Esse non sono semplici funzioni, ma meccanismi che catturano l'ambiente (lexical scope) nel momento della creazione. Poiché lo stack viene distrutto dopo la chiamata iniziale, le closure permettono di mappare le variabili libere e spostare il contesto di esecuzione dal **stack all'heap**, garantendo la sopravvivenza dei dati necessari alla continuazione.

Esempio di funzione CPS generica:

```javascript
void sum(x, y, cont) { 
    cont(x + y); // Il risultato viene "ritornato" tramite la continuazione
}
```

Esempio di transizione asincrona:

```javascript
// Sincrona (Bloccante)
function loadUserPic(userId) {
   let user = findUserById(userId);
   return loadPic(user.picId);
}

// Asincrona CPS (Non-Bloccante)
function loadUserPic(userId, callback) {
   findUserById(userId, (user) => {
     loadPic(user.picId, callback); // Nidificazione necessaria
   });
}
```

--------------------------------------------------------------------------------

## 6. Dai Promises alla Gestione dei Microtask

L'eccessiva nidificazione del CPS porta al **Callback Hell** (o Pyramid of Doom), frammentando la logica e degradando la modularità. I **Promises** (concettualizzati nel 1976 e standardizzati come _Thenables_ in ECMA-262) risolvono questo problema trattando l'asincronia come un valore futuro immutabile.

### Stati e Priorità: Il Microtask Queue

Un Promise può trovarsi in tre stati: **Pending**, **Resolved (Fulfilled)** o **Rejected**. Una volta risolto o rigettato, è considerato **Settled**. Architetturalmente, la risoluzione di un Promise non viene inserita nella coda degli eventi standard (Task), ma nella **Microtask Queue**. I microtask hanno priorità assoluta: vengono processati immediatamente dopo il task corrente e prima di qualsiasi altro evento I/O o rendering. Una catena infinita di `.then()` può quindi "congelare" l'interfaccia utente esattamente come una chiamata bloccante.

**Limiti intrinseci:**

- **Eagerness:** Il lavoro inizia immediatamente alla creazione del Promise.
- **Inanità dei cicli:** Difficoltà di integrazione con `for` o `while` tradizionali.
- **Impossibilità di cancellazione.**

--------------------------------------------------------------------------------

## 7. Async/Await: Sintassi Sincrona e Design Clash

L'introduzione di **Async/Await** (ES2017, .NET TAP) permette di scrivere codice con semantica asincrona usando uno stile sincrono. L'operatore `await` sospende l'esecuzione della funzione, effettuando lo **yielding** del controllo al chiamante e salvando lo stato della computazione per un ripristino futuro.

### Problematiche di Modularità e Design Clash

Mischiare stili sincroni e asincroni genera un **Design Clash** che intacca l'incapsulamento. Se una funzione asincrona non viene invocata correttamente con `await`, la sequenza logica collassa.

**Esempio di Modularity Failure:**

```javascript
async function f() {
   console.log("started f()");
   await delay(2000);
   console.log("done f()");
}

function g() { // Errore: g non attende f()
   console.log("started g");
   f(); 
   console.log("done g");
}

// Output errato: "started g", "started f()", "done g", "done f()"
```

L'output dimostra come la funzione `g()` termini prima di `f()`, rompendo la modularità attesa da un chiamante sincrono.

--------------------------------------------------------------------------------

## 8. Coroutine, Fiber e Multitasking Cooperativo

Le **Coroutine** rappresentano una generalizzazione delle subroutine per il multitasking cooperativo, capaci di sospendersi e riprendersi mantenendo lo stato.

- **Coroutine:** Costrutto di **livello linguaggio** (controllo di flusso).
- **Fiber:** Costrutto di **livello sistema** (thread leggeri gestiti dallo scheduler dell'applicazione o dell'OS).

### Il Modello Kotlin

Kotlin implementa questo paradigma tramite `Suspending functions` e `Builders` (`launch`, `async`). **Analisi di esecuzione (Toy Example):** In un blocco `runBlocking`, un comando `async` avvia una computazione che non blocca il flusso principale. L'output tipico vede l'esecuzione di "after the async call" _prima_ che il corpo del task asincrono completi il suo `delay`, dimostrando come le coroutine si avvicendino sul thread senza preemption forzata.

--------------------------------------------------------------------------------

## 9. Conclusioni: Virtual Threads vs. Asincronia

La visione di Brian Goetz (2022) con l'introduzione dei **Virtual Threads** (Project Loom, Java JDK 19) propone un ritorno alla semplicità del modello "thread-per-request". L'obiettivo è eliminare il carico cognitivo della "Never-Blocking Rule" per lo sviluppatore, offrendo un'efficienza paragonabile alle coroutine con la modularità dei thread tradizionali.

Tuttavia, i Virtual Threads non elevano gli eventi a cittadini di prima classe dell'architettura; la gestione reattiva rimane una necessità applicativa. In ultima analisi, la scelta tra un paradigma asincrono puro (basato su eventi e continuazioni) e uno basato su thread leggeri dipende dal trade-off tra la necessità di prestazioni estreme (bassa **impronta di memoria**) e la manutenibilità di una base di codice meno complessa.