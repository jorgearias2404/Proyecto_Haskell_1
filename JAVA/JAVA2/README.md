# Almacén robótico concurrente

Este programa simula un pequeño almacén en el que varios robots intentan llevar cajas objetivo hasta una salida, mientras otros hilos introducen cajas nuevas. Está escrito en Java y funciona desde la consola.

## Qué hace el programa

El almacén es un tablero de 6 × 6. Las cajas objetivo deben llegar a la casilla de salida `(5,5)`, donde se consideran extraídas. Las cajas de bloqueo y los demás robots son obstáculos. La salida está reservada: no se coloca allí ninguna entidad al inicio y los robots no pueden ocuparla.

Al comenzar, el programa distribuye al azar las cajas indicadas en el archivo de entrada y crea los robots en casillas libres. Durante la simulación:

- Cada robot dispone de una batería inicial. Moverse o empujar una caja consume una unidad.
- Los robots buscan una ruta hacia una caja objetivo mediante búsqueda en anchura (BFS), considerando la posición del robot y la caja. Ejecutan el primer paso de la ruta y vuelven a calcularla en el siguiente turno, para tener en cuenta los cambios en el tablero.
- Si no encuentran un plan disponible, intentan moverse a una casilla vecina libre elegida al azar. Si no pueden realizar una acción, esperan un momento y vuelven a intentarlo.
- Los productores generan cajas objetivo con un 70 % de probabilidad y cajas de bloqueo con un 30 %. Después de cada inserción esperan entre 120 y 299 milisegundos antes del siguiente intento.
- Si no quedan casillas disponibles, el productor registra una saturación y espera a que se libere espacio. La simulación se detiene cuando terminan los hilos de todos los robots; entonces se paran los productores y se imprime el reporte final.

La planificación se vuelve a calcular en cada turno, pero no asegura que siempre sea posible extraer todas las cajas: los obstáculos, la posición de las cajas y la batería disponible pueden impedirlo.

## Contenido de la carpeta

| Archivo              | Función                                                                                            |
| -------------------- | -------------------------------------------------------------------------------------------------- |
| `Main.java`          | Lee y valida la configuración, prepara el tablero y coordina los hilos.                            |
| `Tablero.java`       | Mantiene el estado compartido, sincroniza las operaciones y resuelve movimientos, empujes y rutas. |
| `Robot.java`         | Guarda el identificador, la posición y la batería de cada robot.                                   |
| `RobotHilo.java`     | Ejecuta los turnos de un robot en su propio hilo.                                                  |
| `Productor.java`     | Ejecuta un hilo productor e intenta insertar cajas.                                                |
| `Configuracion.java` | Almacena los valores leídos del archivo de entrada.                                                |
| `Posicion.java`      | Representa una fila y una columna del tablero.                                                     |
| `TipoCaja.java`      | Define los estados de una casilla: vacía, objetivo o bloqueo.                                      |
| `entrada.txt`        | Configuración de ejemplo para iniciar la simulación.                                               |
| `Makefile`           | Compila todos los archivos Java con un solo comando.                                               |

## Requisitos, compilación y ejecución

Se necesita un JDK de Java 11 o posterior. Para compilar con el Makefile también se necesita GNU Make instalado y disponible en el `PATH`. Abre una terminal en la carpeta `JAVA2` y ejecuta:

```sh
make
```

Este comando compila todos los archivos `.java` de la carpeta. También puedes usar `make compile`. Si no tienes Make instalado, compila directamente con el JDK:

```sh
javac *.java
```

Después de compilar, inicia el programa pasando como argumento el archivo de configuración:

```sh
java Main entrada.txt
```

También puedes indicar la ruta a otro archivo, por ejemplo:

```sh
java Main casos/caso_sin_cajas.txt
```

Los comandos `javac` y `java` están disponibles en Windows, Linux y macOS al instalar un JDK y añadirlo al `PATH`. `make` funciona en esos sistemas si GNU Make está instalado. Cada vez que cambies el código, vuelve a compilar antes de iniciar el programa.

## Formato del archivo de entrada

Cada parámetro ocupa una línea y los valores se separan con comas. Por ejemplo, el archivo `entrada.txt` contiene:

```text
Cajas_Objetivo_Iniciales, 3
Cajas_Bloqueo_Iniciales, 5
Robots, 4, 15
Productores, 2
```

Los parámetros significan:

- `Cajas_Objetivo_Iniciales`: cantidad de cajas objetivo que se colocan al inicio.
- `Cajas_Bloqueo_Iniciales`: cantidad de cajas de bloqueo que se colocan al inicio.
- `Robots`: cantidad de robots y batería inicial de cada uno.
- `Productores`: cantidad de hilos productores.

Debe haber al menos un robot y un productor, y la batería debe ser mayor que cero. Las cantidades de cajas no pueden ser negativas. La suma de cajas iniciales y robots no puede superar 35, ya que una casilla del tablero está reservada para la salida. Los parámetros deben escribirse con esos nombres y respetar el formato mostrado.

## Casos de prueba manuales

Guarda cada configuración de ejemplo en un archivo `.txt` dentro de `JAVA2` y ejecútalo con `java Main nombre_del_archivo.txt`. No hay pruebas automatizadas configuradas en esta carpeta; estos casos sirven para comprobar manualmente los escenarios principales.

1. **Ejecución normal:** ejecuta `java Main entrada.txt`. Deben aparecer acciones con ticks, y al terminar, el reporte final y el tablero. La cantidad de cajas extraídas puede cambiar entre ejecuciones.

2. **Inicio sin cajas:** guarda lo siguiente como `caso_sin_cajas.txt` y ejecútalo. Comprueba que se inicia con el tablero sin cajas y que los productores empiezan a introducirlas.

   ```text
   Cajas_Objetivo_Iniciales, 0
   Cajas_Bloqueo_Iniciales, 0
   Robots, 1, 5
   Productores, 1
   ```

3. **Varios hilos:** guarda la configuración como `caso_concurrencia.txt` y ejecútala. En la consola deben intercalarse acciones de varios robots y productores; el resultado concreto depende del orden en que se ejecuten los hilos.

   ```text
   Cajas_Objetivo_Iniciales, 2
   Cajas_Bloqueo_Iniciales, 2
   Robots, 3, 30
   Productores, 2
   ```

4. **Cantidad de robots inválida:** guarda este ejemplo como `caso_robot_invalido.txt`. El programa debe informar que debe existir al menos un robot y terminar sin iniciar la simulación.

   ```text
   Cajas_Objetivo_Iniciales, 0
   Cajas_Bloqueo_Iniciales, 0
   Robots, 0, 10
   Productores, 1
   ```

5. **Demasiadas entidades iniciales:** guarda este ejemplo como `caso_exceso_entidades.txt`. Como la suma es mayor que 35, el programa debe informar del error de configuración y no iniciar los hilos.

   ```text
   Cajas_Objetivo_Iniciales, 20
   Cajas_Bloqueo_Iniciales, 14
   Robots, 2, 10
   Productores, 1
   ```

Los casos válidos incluyen decisiones aleatorias: las posiciones iniciales, las cajas producidas y el orden de los hilos cambian entre ejecuciones. Por eso, conviene comprobar el comportamiento general y los mensajes, no esperar una secuencia idéntica o un número fijo de extracciones.

## Concurrencia

Para implementar la concurrencia, hicimos que cada robot y cada productor trabajara en su propio hilo. `Main` crea esos hilos, inicia primero los productores y después los robots. Así, ambos tipos de tarea pueden avanzar al mismo tiempo.

El tablero es el recurso que comparten todos esos hilos. Para evitar que dos acciones cambien el tablero a la vez y dejen posiciones inconsistentes, protegí sus operaciones con un `ReentrantLock` justo. Por ejemplo, antes de insertar una caja o ejecutar un turno de un robot, el hilo toma el bloqueo; al terminar, lo libera. De esta manera, aunque haya varios hilos activos, cada operación sobre el estado del tablero se realiza de forma segura.

También agregué una `Condition` llamada `espacioDisponible`. Si un productor encuentra el tablero lleno, espera en esa condición en vez de seguir intentando insertar cajas. Cuando una caja objetivo llega a la salida y se extrae, queda una casilla libre y el tablero avisa a los productores para que vuelvan a comprobar si pueden insertar. Si la simulación termina mientras alguno está esperando, `Main` lo interrumpe para que pueda salir y finalizar.

Los hilos de robot consumen batería al moverse o empujar. Cuando ya no tienen batería, terminan su tarea y avisan a `Main`. Cuando han terminado todos los robots, `Main` marca la simulación como inactiva, detiene y espera a los productores y, por último, pide al tablero que imprima el reporte final. El contador global de ticks y la cantidad de robots activos usan variables atómicas para actualizar esos valores compartidos con seguridad.

## Símbolos del tablero final

- `.`: casilla vacía.
- `O`: caja objetivo que permanece en el tablero.
- `X`: caja de bloqueo.
- `R`: robot.
