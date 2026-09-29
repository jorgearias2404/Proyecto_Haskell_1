# Proyecto #3 — Programación Concurrente
## Almacén Robótico Concurrente

Simulación concurrente en Java (hilos + monitores) de un almacén 6x6 en el
que múltiples **Robots de Carga** (consumidores) buscan Cajas Objetivo y
las empujan hasta la zona de extracción (5,5), mientras varias **Cintas
Transportadoras** (productoras) introducen aleatoriamente nuevas cajas
(Objetivo o de Bloqueo) en las casillas vacías del tablero.

---

## 1. Estructura del proyecto

```
Proyecto_Haskell_1_JAVA/
│
├── out/                          ← .class compilados
│   ├── Logger.class
│   ├── MonitorTablero.class
│   ├── MonitorTablero$ResultadoTurno.class
│   ├── ProductorSokoban.class
│   ├── RobotCarga.class
│   └── SimulacionRobotsSokoban.class
│
├── Logger.java                   Utilidad de traza (log) thread-safe
├── MonitorTablero.java           Monitor / recurso crítico (el tablero)
├── ProductorSokoban.java         Hilo Productor (Cinta transportadora)
├── RobotCarga.java                Hilo Consumidor (Robot de carga)
├── SimulacionRobotsSokoban.java   Clase principal (main)
│
├── entrada_ejemplo.txt           Caso de ejemplo (el del enunciado)
├── entrada_estres.txt            Caso de prueba de estrés
├── compilar.bat                  Script de compilación (Windows)
└── README.md                     Este documento
```

---

## 2. Instrucciones de uso

### Compilar

**Windows** (doble clic o desde consola):
```
compilar.bat
```

**Linux / macOS**:
```
javac -d out *.java
```

### Ejecutar

```
java -cp out SimulacionRobotsSokoban entrada_ejemplo.txt
java -cp out SimulacionRobotsSokoban entrada_estres.txt
```

El programa **no depende de ningún IDE**: compila y corre desde una
terminal cualquiera únicamente con el JDK instalado, y no usa interfaces
gráficas (toda la interacción es por consola, tal como exige el
enunciado).

---

## 3. Formato del archivo de entrada

```
Cajas_Objetivo_Iniciales, 3
Cajas_Bloqueo_Iniciales, 5
Robots, 4, 15
Productores, 2
```

- Línea 1: cantidad de Cajas Objetivo a colocar aleatoriamente al iniciar.
- Línea 2: cantidad de Cajas de Bloqueo a colocar aleatoriamente al iniciar.
- Línea 3: cantidad de robots y batería inicial (movimientos/empujes) de
  **cada uno** de ellos.
- Línea 4: cantidad de cintas transportadoras (productores) activas.

Si la suma de cajas iniciales + robots supera las 36 casillas del
tablero (6x6), el programa reporta un **error de configuración** y
finaliza sin iniciar ningún hilo.

---

## 4. Procesos, recursos críticos y sincronización

### 4.1 Procesos (hilos)

| Hilo | Rol | Clase |
|---|---|---|
| `Productor-i` | Productor: inserta cajas en casillas vacías | `ProductorSokoban` |
| `Robot-i` | Consumidor: busca y empuja Cajas Objetivo hacia (5,5) | `RobotCarga` |
| `main` | Orquesta la simulación, arranca/espera hilos, imprime el reporte final | `SimulacionRobotsSokoban` |

### 4.2 Recurso crítico

El **único** recurso crítico del sistema es el **tablero** (matriz 6x6),
encapsulado íntegramente dentro de la clase `MonitorTablero`. Todo el
estado mutable compartido vive ahí:

- `cajas[r][c]`: contenido de la casilla (`.` vacía, `O` objetivo, `X` bloqueo).
- `robots[r][c]`: id del robot que ocupa la casilla, o `-1`.
- Posiciones actuales de cada robot y sus reservas de objetivo.
- Contadores de estadísticas (extraídas, saturaciones, robots agotados).

No existe ningún otro objeto compartido mutable entre hilos: `Logger`
solo comparte un contador de tick protegido con su propio candado, y cada
`RobotCarga`/`ProductorSokoban` solo modifica sus propios campos
(batería, id) desde su propio hilo.

**Operaciones de acceso al recurso crítico** (todas son métodos
`synchronized` de `MonitorTablero`, es decir, secciones críticas):

- `colocarCajaInicial(tipo)` / `colocarRobotInicial(id)` — configuración inicial.
- `insertarCaja(productor)` — un productor intenta insertar una caja.
- `turno(robot)` — un robot decide y ejecuta como máximo una acción
  (esperar, moverse, empujar o extraer) por invocación.
- `retirarRobot(robot)` — saca al robot del tablero al agotar batería.
- `imprimirReporteFinal()` — lectura de solo el hilo `main`, al final.

### 4.3 Puntos de sincronización (exclusión mutua y coordinación)

- **Exclusión mutua:** todos los métodos anteriores son `synchronized`
  sobre la misma instancia de `MonitorTablero`, que actúa como monitor
  único. Esto garantiza que nunca dos hilos lean/escriban `cajas` o
  `robots` al mismo tiempo, evitando así:
  - dos robots terminando en la misma casilla,
  - un productor insertando una caja donde un robot se está moviendo,
  - una caja "duplicándose" o "perdiéndose" por una carrera de datos.
- **Coordinación (variables de condición con `wait()`/`notifyAll()`):**
  - Un **Productor** llama `wait()` cuando, al revisar el tablero, lo
    encuentra completamente lleno (ver definición exacta de
    "saturación" en la sección 5). Se despierta con `notifyAll()` cada
    vez que cualquier movimiento, empuje, extracción o retiro de robot
    libera una casilla.
  - Un **Robot** llama `wait()` cuando no existe ninguna Caja Objetivo
    en el tablero. Se despierta con `notifyAll()` cada vez que un
    productor inserta una caja nueva (sea Objetivo o Bloqueo, ya que
    cualquier inserción puede cambiar la disponibilidad de casillas y
    conviene reevaluar).
  - Siempre se usa `notifyAll()` (nunca `notify()`) porque en el
    monitor conviven hilos esperando por condiciones distintas
    ("tablero lleno" vs. "no hay objetivo"); usar `notify()` podría
    despertar al hilo equivocado y dejar al otro esperando para
    siempre (señal perdida).
  - Todas las esperas usan la forma `while (condición) { wait(); }`
    (nunca `if`), como exige la práctica correcta de monitores en
    Java, para protegerse de *spurious wakeups* y de que la condición
    haya vuelto a cambiar antes de que el hilo despertado retome el
    candado.

---

## 5. Política de Productores y definición de Saturación

### 5.1 Temporización (evitar que un productor llene el tablero en milisegundos)

Cada `ProductorSokoban`, **fuera** de cualquier sección `synchronized`
(para no bloquear a nadie mientras "duerme"), espera un tiempo aleatorio
uniforme entre **300 ms y 900 ms** (`Thread.sleep`) antes de cada intento
de inserción. Esto:

- da tiempo a los robots a procesar y liberar casillas entre inserciones,
- evita que el tablero se sature de inmediato al arrancar la simulación,
- y con varios productores corriendo en paralelo sigue produciendo un
  flujo constante mientras mantiene la simulación observable en consola.

### 5.2 Probabilidad Objetivo vs. Bloqueo

Al insertar, cada productor genera una **Caja Objetivo con 55% de
probabilidad** y una **Caja de Bloqueo con 45%**. Se eligió una leve
mayoría a favor del Objetivo para que los robots tengan trabajo
disponible con más frecuencia (y así el sistema tienda a progresar y
terminar en un tiempo razonable), sin dejar de introducir obstáculos que
hagan el problema interesante y fuercen a los robots a esquivar cajas de
bloqueo. Esta política es un parámetro de diseño documentado, no un
requisito estricto del enunciado.

### 5.3 Definición exacta de "Saturación"

Siguiendo estrictamente el enunciado: la saturación **no** es un estado
persistente, sino un **evento puntual**. Ocurre única y exclusivamente
cuando:

1. Un hilo Productor se activa (termina su espera aleatoria) para
   intentar insertar una caja,
2. adquiere el monitor (`synchronized`) y revisa el tablero,
3. y confirma que **no existe ninguna casilla vacía** en ese instante.

En ese momento exacto se incrementa el contador de saturaciones y se
imprime la línea `ESPERA_SATURACION`, y el hilo pasa a `wait()`. Si al
despertar (por un `notifyAll()`) el tablero **sigue** lleno, se cuenta
como un **nuevo** intento fallido (se vuelve a loguear), ya que el
productor efectivamente "se activó de nuevo, revisó y confirmó" la
saturación otra vez.

---

## 6. Reglas de movimiento y empuje (mecánica tipo Sokoban)

- Un robot solo puede entrar a una casilla **vacía** (sin caja y sin otro
  robot).
- Para desplazar una Caja Objetivo, el robot debe ubicarse en la casilla
  contigua al lado opuesto de la dirección de empuje y la casilla de
  destino de la caja debe estar vacía; entonces el robot avanza a la
  posición que dejó la caja y la caja avanza una casilla más allá
  (regla física estándar de "empujar", sin poder atravesar cajas ni
  otros robots, ni empujar dos cajas a la vez).
- El robot elige, en cada turno, la dirección de empuje que más reduzca
  la distancia (Manhattan) de la caja hacia la meta (5,5); si esa
  dirección no es físicamente posible (fuera de los límites del
  tablero) **o** su casilla destino está ocupada en ese instante (por
  otra caja o robot), se evalúan las demás direcciones válidas en orden
  de cuánto acercan la caja a la meta, en vez de quedar esperando
  indefinidamente una única dirección.
- Cuando la caja llega exactamente a (5,5) se considera **extraída**: se
  retira del tablero, se contabiliza y el robot queda ocupando esa
  casilla.
- **Reserva de objetivos:** cada robot "reserva" la Caja Objetivo que
  decide perseguir, para que dos robots no compitan indefinidamente por
  la misma caja. La reserva se libera automáticamente si la caja es
  extraída, si deja de existir, o si resulta inalcanzable.

### Limitación conocida (documentada, no oculta)

Con movimiento "codicioso" (greedy, sin backtracking ni pathfinding
completo tipo A\*), una Caja Objetivo que quede exactamente en una
esquina del tablero opuesta a la meta —por ejemplo (0,0)— puede quedar
en un **deadlock físico** irresoluble: para empujarla en cualquier
dirección se necesitaría que un robot se parara fuera de los límites
del tablero, lo cual es físicamente imposible (esto es una limitación
conocida y documentada del Sokoban clásico, no un error del programa).
En ese caso, el robot que la tenía reservada la abandona automáticamente
y busca otra caja; la simulación **no se bloquea** por esto, simplemente
esa caja puntual no podrá extraerse.

---

## 7. Manejo y prevención de interbloqueos (deadlocks)

Se tomaron las siguientes decisiones de diseño específicamente para
minimizar o evitar interbloqueos:

1. **Un único candado (monitor) para todo el recurso compartido.** Al
   existir un solo objeto `MonitorTablero` y ningún hilo intenta adquirir
   dos candados distintos en un orden cruzado, se elimina por
   construcción el clásico interbloqueo circular de múltiples locks.
   `Logger` usa un candado propio y completamente independiente
   (`Logger.class`), y nunca se llama de vuelta a `MonitorTablero` desde
   dentro de `Logger`, por lo que no hay orden de adquisición cruzado
   posible entre ambos candados.
2. **Ningún hilo duerme (`Thread.sleep`) mientras sostiene el candado del
   monitor.** El `Thread.sleep` de los productores y de los robots
   (ritmo de simulación) ocurre siempre **fuera** de los métodos
   `synchronized`, así que un hilo "dormido" nunca bloquea a los demás.
3. **Uso de `while` (no `if`) en toda espera condicional**, y siempre
   `notifyAll()`, evitando señales perdidas que dejarían a un hilo
   esperando para siempre.
4. **Condición de apagado ordenado (`activa`):** al terminar todos los
   robots, `main` marca la simulación como inactiva y hace un
   `notifyAll()` final, despertando a cualquier productor que hubiera
   quedado esperando por saturación, para que pueda revisar la bandera,
   salir de su ciclo y terminar su hilo limpiamente (evita hilos
   productores "zombis" que nunca serían unidos con `join()`).
5. **Condición de parada adicional para robots** (definida por el
   equipo, permitida por el enunciado): si ya no hay ninguna Caja
   Objetivo en el tablero **y** la configuración indica 0 productores
   (por lo tanto nunca podrá haber una nueva), el robot finaliza su
   ejecución en lugar de esperar indefinidamente algo que nunca llegará.
   Este caso se reporta con la acción `RETIRO_SIN_TRABAJO_DISPONIBLE` en
   la traza y **no** se contabiliza como "batería agotada" en el reporte
   final.
6. **Ninguna espera cruzada entre robots.** Los robots no se esperan
   entre sí ni sostienen recursos parciales: cada `turno()` es una
   operación atómica de "todo o nada" dentro del monitor, por lo que no
   puede quedar un robot a medio camino sosteniendo el candado mientras
   espera a otro robot.

Con este diseño, el único hilo que puede quedar bloqueado por tiempo
prolongado (no indefinido, salvo el caso 5 ya cubierto) es un robot
esperando una nueva Caja Objetivo mientras existan productores activos
— y ese bloqueo se resuelve tan pronto un productor logre insertar una,
lo cual está garantizado mientras el tablero no esté permanentemente
lleno de Cajas de Bloqueo (riesgo estadístico bajo dado el 45% de
probabilidad de bloqueo vs. 55% de objetivo, y aceptado como parte de la
aleatoriedad inherente al problema, tal como lo describe el enunciado).

---

## 8. Formato de salida

### 8.1 Traza de ejecución (tiempo real)

```
[Tick-01] [Productor-1] INSERTAR_OBJETIVO -> (1,2)
[Tick-02] [Productor-2] INSERTAR_BLOQUEO -> (3,3)
[Tick-03] [Robot-1] MOVER (0,0) -> (0,1) | Batería: 14
[Tick-04] [Robot-2] EMPUJAR_OBJETIVO (2,2) -> (2,3) | Batería: 9
[Tick-05] [Productor-1] ESPERA_SATURACION (Intento de insertar en tablero lleno)
[Tick-06] [Robot-1] EXTRACCION_EXITOSA -> (5,5)
[Tick-07] [Robot-2] BATERIA_AGOTADA -> Retiro del tablero
```

### 8.2 Reporte final

```
=== REPORTE FINAL DEL ALMACÉN ===
- Cajas Objetivo extraídas (llevadas a 5,5): [Cantidad]
- Saturaciones del tablero (intentos fallidos de productores): [Cantidad]
- Robots que finalizaron por batería agotada: [Cantidad]

=== ESTADO FINAL DEL TABLERO ===
. . X O . .
R . . . . .
. . X O . .
. . . . . .
. . . . . .
. . . . . .
```

Nomenclatura: `R` = Robot, `O` = Caja Objetivo, `X` = Caja de Bloqueo,
`.` = Casilla Vacía.

---

## 9. Resumen de decisiones de diseño

| Decisión | Justificación |
|---|---|
| Un solo monitor (`MonitorTablero`) para todo el tablero | Simplicidad y eliminación estructural de deadlocks por múltiples locks |
| `while` + `notifyAll()` en toda espera | Evita señales perdidas y *spurious wakeups* |
| Batería solo se decrementa en movimiento/empuje real | Cumple literalmente el enunciado ("la espera no consume batería") |
| 55% Objetivo / 45% Bloqueo al insertar | Balance entre progreso garantizado y dificultad del problema |
| Espera aleatoria 300–900 ms entre inserciones | Evita saturación instantánea, mantiene la traza legible |
| Reserva de objetivo por robot | Evita que varios robots persigan y "peleen" por la misma caja |
| Empuje "codicioso" (greedy) por distancia Manhattan | Solución simple, determinística y suficiente para el alcance del proyecto, con la limitación de esquinas documentada explícitamente |
| Condición de parada extra sin productores | Evita esperas infinitas en configuraciones degeneradas |
