# Snake Race — ARSW Lab #2 (Java 21, Virtual Threads)

**Escuela Colombiana de Ingeniería – Arquitecturas de Software**  
Laboratorio de programación concurrente: condiciones de carrera, sincronización y colecciones seguras.

---
## Autores
Tomás Quiceno 
Deisy Guzmán

## Requisitos

- **JDK 21** (Temurin recomendado)
- **Maven 3.9+**
- SO: Windows, macOS o Linux

---

## Cómo ejecutar

```bash
mvn clean verify
mvn -q -DskipTests exec:java -Dsnakes=4
```

- `-Dsnakes=N` → inicia el juego con **N** serpientes (por defecto 2).
- **Controles**:
  - **Flechas**: serpiente **0** (Jugador 1).
  - **WASD**: serpiente **1** (si existe).
  - **Espacio** o botón **Action**: Pausar / Reanudar.

---

## Reglas del juego (resumen)

- **N serpientes** corren de forma autónoma (cada una en su propio hilo).
- **Ratones**: al comer uno, la serpiente **crece** y aparece un **nuevo obstáculo**.
- **Obstáculos**: si la cabeza entra en un obstáculo hay **rebote**.
- **Teletransportadores** (flechas rojas): entrar por uno te **saca por su par**.
- **Rayos (Turbo)**: al pisarlos, la serpiente obtiene **velocidad aumentada** temporal.
- Movimiento con **wrap-around** (el tablero “se repite” en los bordes).

---

## Arquitectura (carpetas)

```
co.eci.snake
├─ app/                 # Bootstrap de la aplicación (Main)
├─ core/                # Dominio: Board, Snake, Direction, Position
├─ core/engine/         # GameClock (ticks, Pausa/Reanudar)
├─ concurrency/         # SnakeRunner (lógica por serpiente con virtual threads)
└─ ui/legacy/           # UI estilo legado (Swing) con grilla y botón Action
```

---

# Actividades del laboratorio

## Parte I — (Calentamiento) `wait/notify` en un programa multi-hilo

1. Toma el programa [**PrimeFinder**](https://github.com/ARSW-ECI/wait-notify-excercise).
2. Modifícalo para que **cada _t_ milisegundos**:
   - Se **pausen** todos los hilos trabajadores.
   - Se **muestre** cuántos números primos se han encontrado.
   - El programa **espere ENTER** para **reanudar**.
3. La sincronización debe usar **`synchronized`**, **`wait()`**, **`notify()` / `notifyAll()`** sobre el **mismo monitor** (sin _busy-waiting_).
4. Entrega en el reporte de laboratorio **las observaciones y/o comentarios** explicando tu diseño de sincronización (qué lock, qué condición, cómo evitas _lost wakeups_).

> Objetivo didáctico: practicar suspensión/continuación **sin** espera activa y consolidar el modelo de monitores en Java.

## Solución Parte I

### Modificaciones en PrimeFinderThread.java

Se necesita que cada hilo consulte periódicamente si debe pausarse. Se le pasa una referencia de Control y se llama a un método que evalúe la condición en cada iteración del ciclo.

![alt text](img/image.png)
![alt text](img/image1.png)

### Modificaciones en Control.java

En el controlador, se define la variable de estado (isPaused), el método sincronizado para que los hilos esperen, y el ciclo de suspensión/reanudación usando temporizadores y la lectura del teclado.

![alt text](img/image2.png)
![alt text](img/image3.png)
![alt text](img/image4.png)

-  ¿Qué Lock se utilizó?: Se utilizó el monitor de la instancia de la clase Control (representado por this dentro de sus métodos sincronizados). Al pasar esta referencia a cada PrimeFinderThread, garantizamos que tanto el hilo coordinador como los hilos trabajadores sincronicen sobre el mismo objeto en memoria.

-  ¿Qué condición dicta la pausa?: La variable booleana isPaused. Su acceso y modificación están protegidos por el bloque synchronized, asegurando visibilidad y previniendo condiciones de carrera.

- ¿Cómo se evitan los lost wakeups (despertares perdidos) y spurious wakeups?: En lugar de usar un simple bloque if (isPaused), el método wait() se implementó dentro de un ciclo while (isPaused). Si un hilo recibe un notify de forma accidental (spurious wakeup) o antes de que realmente deba despertar, el ciclo while forzará a que vuelva a evaluar la variable isPaused. Si sigue siendo true, el hilo volverá a invocar wait() inmediatamente de forma segura.

---

## Parte II — SnakeRace concurrente (núcleo del laboratorio)

### 1) Análisis de concurrencia

- Explica **cómo** el código usa hilos para dar autonomía a cada serpiente.
- **Identifica** y documenta en **`el reporte de laboratorio`**:
  - Posibles **condiciones de carrera**.
  - **Colecciones** o estructuras **no seguras** en contexto concurrente.
  - Ocurrencias de **espera activa** (busy-wait) o de sincronización innecesaria.

### 2) Correcciones mínimas y regiones críticas

- **Elimina** esperas activas reemplazándolas por **señales** / **estados** o mecanismos de la librería de concurrencia.
- Protege **solo** las **regiones críticas estrictamente necesarias** (evita bloqueos amplios).
- Justifica en **`el reporte de laboratorio`** cada cambio: cuál era el riesgo y cómo lo resuelves.

### 3) Control de ejecución seguro (UI)

- Implementa la **UI** con **Iniciar / Pausar / Reanudar** (ya existe el botón _Action_ y el reloj `GameClock`).
- Al **Pausar**, muestra de forma **consistente** (sin _tearing_):
  - La **serpiente viva más larga**.
  - La **peor serpiente** (la que **primero murió**).
- Considera que la suspensión **no es instantánea**; coordina para que el estado mostrado no quede “a medias”.

### 4) Robustez bajo carga

- Ejecuta con **N alto** (`-Dsnakes=20` o más) y/o aumenta la velocidad.
- El juego **no debe romperse**: sin `ConcurrentModificationException`, sin lecturas inconsistentes, sin _deadlocks_.
- Si habilitas **teleports** y **turbo**, verifica que las reglas no introduzcan carreras.

> Entregables detallados más abajo.

## Solución Parte II — SnakeRace concurrente

### 1) Análisis de concurrencia

- La autonomía se logra asignando a cada serpiente su propio flujo de ejecución independiente. En la clase SnakeApp, se utiliza una fábrica de hilos moderna mediante Executors.newVirtualThreadPerTaskExecutor(). Por cada instancia de Snake creada, se envía una tarea SnakeRunner (que implementa Runnable) a este ejecutor. En su método run(), cada hilo virtual mantiene un ciclo infinito donde calcula su dirección, avanza un paso consultando al tablero, y luego se suspende a sí mismo usando Thread.sleep() por una cantidad de milisegundos que varía si está en modo "turbo". Esto permite que múltiples serpientes se muevan y pausen de forma concurrente sin bloquear el hilo principal ni la interfaz gráfica.

- **Condiciones carrera:**
  En la clase Snake existe un riesgo crítico de inconsistencia entre el hilo virtual de la serpiente y el hilo despachador de eventos de Swing (EDT). El hilo virtual modifica constantemente el cuerpo de la serpiente llamando al método advance(). Simultáneamente, el hilo de la UI llama a snapshot() (desde GamePanel.paintComponent) para dibujar la serpiente en pantalla. Como ambas operaciones acceden al mismo ArrayDeque sin ningún mecanismo de exclusión mutua, se puede generar un estado inconsistente o una ConcurrentModificationException en tiempo de ejecución.

  **Condiciones o estructuras no seguras:**
  - ArrayDeque <Position> en Snake, esta colección no es thread-safe y está siendo accedida concurrentemente para lectura (UI) y escritura (Virtual Threads).
  - HashSet y HashMap en Board, el tablero utiliza HashSet para los ratones, obstáculos y turbos, y un HashMap para los teletransportadores. Estas colecciones no deberían funcionar bajo acceso concurrente no coordinado.

  **Sincronización innecesaria y problemas de espera:**
  - Sincronización excesiva en la clase Board, el método step(Snake snake) y todos los métodos de acceso (mice(), obstacles(), etc.) tienen el modificador synchronized en su firma. Esto significa que utilizan el lock de la instancia del objeto Board entero (un coarse-grained lock). Si se ejecuta el juego con -Dsnakes=20, las 20 serpientes chocarán intentando entrar al método step(); 19 hilos virtuales se bloquearán esperando a que 1 sola serpiente termine de moverse. Esto destruye por completo el beneficio de la concurrencia.

  - Ausencia de mecanismo de pausa en los hilos, el botón "Action" de la UI invoca clock.pause(), deteniendo únicamente el repintado gráfico de la aplicación, pero los hilos SnakeRunner ignoran este estado y continúan ejecutándose de fondo. Para implementar la pausa correctamente en la Parte II, se requerirá un mecanismo que detenga los hilos sin caer en espera activa (busy-waiting).

### 2) Correcciones mínimas y regiones críticas

### Modificaciones en Board.java

El problema principal en Board es el synchronized en toda la firma del método step(), lo que obligaba a todas las serpientes a moverse de a una por vez. Al usar estructuras del paquete java.util.concurrent, las colecciones gestionan sus propios bloqueos internos a nivel de segmento (nodos o baldes), permitiendo el acceso múltiple.

![alt text](img/image5.png)
![alt text](img/image6.png)
![alt text](img/image7.png)

### Modificaciones en Snake.java

Se cambia el ArrayDeque por un ConcurrentLinkedDeque. De esta manera, el hilo virtual puede agregar y eliminar posiciones del cuerpo sin interrumpir al hilo de Swing (la UI) cuando este lee la colección para dibujar la serpiente.

![alt text](img/image8.png)
![alt text](img/image9.png)

- **Riesgo:** El método step en Board.java tenía un modificador synchronized a nivel de método. Esto causaba contención masiva (coarse-grained locking), serializando el movimiento de todas las serpientes e impidiendo la concurrencia real. Además, el uso de colecciones no seguras como HashSet y ArrayDeque exponía el programa a ConcurrentModificationException.

- **Solución y Regiones Críticas:** Se eliminó el modificador synchronized de todos los métodos en Board. La protección de la región crítica se delegó internamente a las colecciones del paquete java.util.concurrent (como ConcurrentHashMap.newKeySet() y ConcurrentLinkedDeque).

- **Alcance Mínimo:** Esta solución es de "alcance mínimo" (lock striping) porque las colecciones concurrentes solo bloquean los nodos específicos de memoria que están siendo modificados (por ejemplo, cuando dos serpientes comen ratones en coordenadas distintas), permitiendo que el resto del tablero siga siendo leído y escrito simultáneamente por otros hilos sin bloqueos globales.

### 3) Control de ejecución seguro (UI)

### Clase GameController.java

![alt text](img/image10.png)

### Modificaciones de SnakeRunner.java

![alt text](img/image11.png)

### Modificaciones de SnakeApp.java

![alt text](img/image12.png)
![alt text](img/image13.png)
![alt text](img/image14.png)
![alt text](img/image15.png)
![alt text](img/image16.png)
![alt text](img/image17.png)
![alt text](img/image18.png)
![alt text](img/image19.png)

- **Problema abordado:** Si la UI calculara la serpiente más larga inmediatamente al pulsar el botón, ocurriría tearing, ya que los Virtual Threads podrían estar a mitad del método step() o durmiendo, alterando su tamaño fracciones de segundo después de calcular las estadísticas.

- **Solución implementada:** Se diseñó un mecanismo de sincronización condicional (GameController) basado en contadores (pausedCount).

- **¿Por qué evita bloqueos amplios?:** Porque la UI lanza la solicitud de pausa (gameController.pause()) dentro de un hilo virtual temporal, dejando libre el Hilo de Eventos de Swing (EDT) para que la ventana no se congele. Solo cuando el Monitor detecta que el 100% de los hilos trabajadores entraron al estado wait(), se le notifica a la UI, garantizando que el acceso a los datos de tamaño de las serpientes es estrictamente secuencial y seguro en ese momento del tiempo.

### 4) Robustez bajo carga

### Capturas de ejecución 

![alt text](img/image20.png)
![alt text](img/image21.png)
![alt text](img/image22.png)

- **Ausencia de ConcurrentModificationException:**
Al someter el juego a un N alto (ej. 25 serpientes), el uso original de HashSet y ArrayDeque habría causado colapsos inmediatos. Esto se superó al migrar el modelo de datos a la familia java.util.concurrent. El uso de ConcurrentHashMap.newKeySet() en el tablero y ConcurrentLinkedDeque en el cuerpo de las serpientes garantiza que los iteradores no fallen si un hilo modifica la estructura mientras otro la recorre (son weakly consistent).

- **Prevención de Lecturas Inconsistentes:**
A altas velocidades, si el hilo de Swing calculara las estadísticas al mismo tiempo que los Virtual Threads calculan su movimiento, las longitudes registradas serían inexactas. El patrón de monitor implementado en GameController actúa como una barrera de sincronización: el cálculo de la serpiente más larga y más corta solo ocurre cuando la variable pausedCount iguala al total de hilos, garantizando que el sistema entero esté en reposo absoluto (estado determinista) antes de la lectura.

- **Prevención de Deadlocks (Abrazos mortales):**
El código está libre de deadlocks porque se eliminó la anidación de bloqueos. Las colecciones concurrentes manejan sus propios locks a nivel interno (lock striping) sin bloquear toda la estructura. El único monitor explícito del sistema (GameController) tiene métodos sincronizados independientes que no invocan otros recursos sincronizados de terceros, eliminando la posibilidad de espera circular.

- **Ausencia de Condiciones de Carrera en las Reglas (Ratones y Turbos):**
¿Qué sucede si dos o más serpientes llegan a la misma coordenada exacta de un ratón o un turbo en el mismo milisegundo? Gracias a que la evaluación se hace con el método remove(Object o) sobre un conjunto concurrente, la operación es atómica. El ConcurrentHashMap internamente garantiza que solo un hilo reciba el retorno true al remover el ratón, mientras que los demás hilos recibirán false. Por lo tanto, el evento de "crecer y generar un nuevo obstáculo" se dispara una sola vez, manteniendo la consistencia lógica del juego sin duplicar entidades.

## Entregables

1. **Código fuente** funcionando en **Java 21**.
2. Todo de manera clara en **`**el reporte de laboratorio**`** con:
   - Data races encontradas y su solución.
   - Colecciones mal usadas y cómo se protegieron (o sustituyeron).
   - Esperas activas eliminadas y mecanismo utilizado.
   - Regiones críticas definidas y justificación de su **alcance mínimo**.
3. UI con **Iniciar / Pausar / Reanudar** y estadísticas solicitadas al pausar.

---

## Criterios de evaluación (10)

- (3) **Concurrencia correcta**: sin data races; sincronización bien localizada.
- (2) **Pausa/Reanudar**: consistencia visual y de estado.
- (2) **Robustez**: corre **con N alto** y sin excepciones de concurrencia.
- (1.5) **Calidad**: estructura clara, nombres, comentarios; sin _code smells_ obvios.
- (1.5) **Documentación**: **`reporte de laboratorio`** claro, reproducible;

---

## Tips y configuración útil

- **Número de serpientes**: `-Dsnakes=N` al ejecutar.
- **Tamaño del tablero**: cambiar el constructor `new Board(width, height)`.
- **Teleports / Turbo**: editar `Board.java` (métodos de inicialización y reglas en `step(...)`).
- **Velocidad**: ajustar `GameClock` (tick) o el `sleep` del `SnakeRunner` (incluye modo turbo).

---

## Cómo correr pruebas

```bash
mvn clean verify
```

Incluye compilación y ejecución de pruebas JUnit. Si tienes análisis estático, ejecútalo en `verify` o `site` según tu `pom.xml`.

---

## Créditos

Este laboratorio es una adaptación modernizada del ejercicio **SnakeRace** de ARSW. El enunciado de actividades se conserva para mantener los objetivos pedagógicos del curso.

**Base construida por el Ing. Javier Toquica.**
