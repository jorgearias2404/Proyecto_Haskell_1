import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MonitorTablero
 * ---------------
 * Recurso crítico principal del sistema: la matriz 6x6 del almacén.
 * Implementa el patrón "monitor" clásico de Java: TODO acceso o
 * modificación del tablero pasa por métodos `synchronized` de esta única
 * instancia, que actúa como el candado (lock) intrínseco compartido por
 * todos los hilos (Robots y Productores).
 *
 * Estado interno representado con dos matrices paralelas:
 *   - cajas[r][c]  : '.' vacío, 'O' caja objetivo, 'X' caja de bloqueo.
 *   - robots[r][c] : -1 si no hay robot, o el id del robot que la ocupa.
 *
 * Una casilla se considera VACÍA únicamente si cajas=='.' Y robots==-1.
 * Un robot solo puede desplazarse a una casilla vacía, o "empujar" una
 * caja objetivo hacia una casilla vacía contigua (regla física de Sokoban).
 *
 * Sincronización:
 *   - Todos los métodos que leen o escriben `cajas`/`robots` son
 *     `synchronized` sobre `this`, garantizando exclusión mutua total
 *     (un solo hilo dentro del monitor a la vez) y por lo tanto evitando
 *     colisiones (dos robots ocupando la misma celda, un productor
 *     insertando sobre una celda que un robot está ocupando, etc.).
 *   - Los Productores usan `wait()` cuando el tablero está lleno
 *     (saturación) y se despiertan con `notifyAll()` cada vez que se libera
 *     una celda (movimiento, empuje o retiro de un robot).
 *   - Los Robots usan `wait()` cuando no existe ninguna Caja Objetivo en
 *     el tablero, y se despiertan con `notifyAll()` cada vez que se inserta
 *     una caja nueva.
 *   - Se usa siempre `notifyAll()` (nunca `notify()`) para evitar el
 *     problema de "señal perdida" (lost wakeup), dado que hay múltiples
 *     hilos con distintas condiciones de espera compitiendo por el mismo
 *     monitor.
 *   - Ningún hilo realiza `Thread.sleep()` mientras sostiene este candado
 *     (el sleep de los productores se hace fuera del bloque synchronized,
 *     en ProductorSokoban), evitando así bloquear innecesariamente a los
 *     demás hilos y reduciendo drásticamente el riesgo de interbloqueo.
 */
public class MonitorTablero {

    public static final int META_FILA = 5;
    public static final int META_COL = 5;

    private final int n;
    private final char[][] cajas;
    private final int[][] robots;
    private final Random rand = new Random();

    // Posición actual de cada robot (id -> {fila, col})
    private final Map<Integer, int[]> posiciones = new HashMap<>();
    // Caja objetivo actualmente "reservada" (asignada) a cada robot, para
    // que dos robots no compitan indefinidamente por la misma caja.
    private final Map<Integer, int[]> objetivoDeRobot = new HashMap<>();
    private final Set<String> reservados = new HashSet<>();

    // Estadísticas para el reporte final
    private int cajasExtraidas = 0;
    private int saturaciones = 0;
    private int robotsAgotados = 0;

    // Cantidad de productores configurados (permite detectar la condición
    // de parada extra: "no hay objetivos y nunca podrá haberlos").
    private final int numProductores;

    // Bandera de control de fin de simulación
    private final AtomicBoolean activa = new AtomicBoolean(true);

    public MonitorTablero(int n, int numProductores) {
        this.n = n;
        this.numProductores = numProductores;
        cajas = new char[n][n];
        robots = new int[n][n];
        for (int i = 0; i < n; i++) {
            Arrays.fill(cajas[i], '.');
            Arrays.fill(robots[i], -1);
        }
    }

    // ---------------------------------------------------------------
    // Configuración inicial (llamada solo por el hilo main, antes de
    // arrancar los demás hilos, por lo que no requiere sincronización
    // estricta, pero se deja synchronized por consistencia y seguridad).
    // ---------------------------------------------------------------

    public synchronized boolean colocarCajaInicial(char tipo) {
        int[] celda = celdaVaciaAleatoria();
        if (celda == null) return false;
        cajas[celda[0]][celda[1]] = tipo;
        return true;
    }

    public synchronized int[] colocarRobotInicial(int idRobot) {
        int[] celda = celdaVaciaAleatoria();
        if (celda == null) return null;
        robots[celda[0]][celda[1]] = idRobot;
        posiciones.put(idRobot, celda);
        return celda;
    }

    public boolean isActiva() {
        return activa.get();
    }

    public void detenerSimulacion() {
        activa.set(false);
        synchronized (this) {
            notifyAll();
        }
    }

    // ---------------------------------------------------------------
    // Productor
    // ---------------------------------------------------------------

    /**
     * Intenta insertar una caja nueva (Objetivo o Bloqueo, según
     * probabilidad) en una casilla vacía aleatoria.
     *
     * Definición de SATURACIÓN (según enunciado): ocurre únicamente en el
     * instante en que el productor se activa, revisa el monitor y
     * confirma que NO hay casillas vacías disponibles. En ese instante se
     * registra el intento fallido y el hilo vuelve a estado de espera.
     */
    public synchronized void insertarCaja(ProductorSokoban productor) throws InterruptedException {
        while (tableroLleno() && activa.get()) {
            saturaciones++;
            Logger.log(productor.getNombre(), "ESPERA_SATURACION (Intento de insertar en tablero lleno)");
            wait();
        }
        if (!activa.get()) return; // la simulación está terminando, no insertar más

        int[] celda = celdaVaciaAleatoria();
        if (celda == null) return; // defensivo; no debería ocurrir aquí

        // Política de productores (documentada también en README.md):
        // 55% de probabilidad de generar Caja Objetivo, 45% Caja de
        // Bloqueo. Se favorece levemente el Objetivo para asegurar que
        // los robots tengan trabajo disponible con más frecuencia, sin
        // dejar de introducir obstáculos que hagan interesante el problema.
        boolean esObjetivo = rand.nextInt(100) < 55;
        char tipo = esObjetivo ? 'O' : 'X';
        cajas[celda[0]][celda[1]] = tipo;

        String accion = (esObjetivo ? "INSERTAR_OBJETIVO" : "INSERTAR_BLOQUEO")
                + " -> (" + celda[0] + "," + celda[1] + ")";
        Logger.log(productor.getNombre(), accion);

        notifyAll(); // puede haber robots esperando una Caja Objetivo
    }

    // ---------------------------------------------------------------
    // Robot (consumidor)
    // ---------------------------------------------------------------

    /**
     * Ejecuta un turno atómico del robot: decide y realiza como máximo
     * UNA acción (esperar, moverse, empujar o extraer) y retorna si
     * efectivamente actuó (para que el hilo llamador sepa si debe
     * consumir tiempo de "pensar" antes de reintentar).
     */
    /** Resultado posible de un turno de robot. */
    public enum ResultadoTurno { ACTUO, SIN_ACCION, FINALIZAR_SIN_TRABAJO }

    public synchronized ResultadoTurno turno(RobotCarga robot) throws InterruptedException {
        int id = robot.getId();

        int[] objetivo = objetivoDeRobot.get(id);
        if (objetivo != null && cajas[objetivo[0]][objetivo[1]] != 'O') {
            // La caja que este robot perseguía ya no está allí (fue
            // extraída o movida por otro hilo entre turnos) -> liberar.
            liberarObjetivo(id);
            objetivo = null;
        }

        if (objetivo == null) {
            // Condición de parada adicional documentada en README.md:
            // si ya no hay cajas objetivo y no existen productores que
            // puedan generar más, no tiene sentido seguir esperando
            // indefinidamente -> el robot da por terminado su trabajo,
            // aunque le quede batería.
            if (!hayObjetivoEnTablero() && numProductores == 0) {
                return ResultadoTurno.FINALIZAR_SIN_TRABAJO;
            }
            while (!hayObjetivoEnTablero()) {
                if (!activa.get()) return ResultadoTurno.FINALIZAR_SIN_TRABAJO;
                wait();
            }
            objetivo = elegirObjetivoLibre(id);
            if (objetivo == null) {
                // Todas las cajas visibles ya están reservadas por otros
                // robots; se espera un instante breve y se reintenta.
                wait(30);
                return ResultadoTurno.SIN_ACCION;
            }
            objetivoDeRobot.put(id, objetivo);
            reservados.add(clave(objetivo));
        }

        int[][] empuje = calcularEmpuje(objetivo);
        if (empuje == null) {
            // Caja físicamente inalcanzable hacia la meta (p. ej. quedó
            // encajada en una esquina): se abandona y se busca otra.
            liberarObjetivo(id);
            return ResultadoTurno.SIN_ACCION;
        }
        int[] from = empuje[0];
        int[] to = empuje[1];
        int[] pos = posiciones.get(id);

        if (pos[0] == from[0] && pos[1] == from[1]) {
            // El robot ya está en posición de empuje.
            if (!estaVacia(to[0], to[1])) {
                return ResultadoTurno.SIN_ACCION; // destino momentáneamente ocupado
            }
            robots[pos[0]][pos[1]] = -1;
            cajas[objetivo[0]][objetivo[1]] = '.';
            boolean esMeta = (to[0] == META_FILA && to[1] == META_COL);

            if (esMeta) {
                robots[to[0]][to[1]] = id;
                posiciones.put(id, to);
                cajasExtraidas++;
                liberarObjetivo(id);
                robot.consumirBateria();
                Logger.log(robot.getNombre(), "EXTRACCION_EXITOSA -> (" + to[0] + "," + to[1] + ")");
                notifyAll();
                return ResultadoTurno.ACTUO;
            } else {
                cajas[to[0]][to[1]] = 'O';
                robots[to[0]][to[1]] = id;
                posiciones.put(id, to);
                reservados.remove(clave(objetivo));
                reservados.add(clave(to));
                objetivoDeRobot.put(id, to);
                robot.consumirBateria();
                Logger.log(robot.getNombre(), "EMPUJAR_OBJETIVO (" + objetivo[0] + "," + objetivo[1]
                        + ") -> (" + to[0] + "," + to[1] + ") | Batería: " + robot.getBateria());
                notifyAll();
                return ResultadoTurno.ACTUO;
            }
        } else {
            int[] siguiente = pasoHacia(pos, from);
            if (siguiente == null) {
                return ResultadoTurno.SIN_ACCION; // camino bloqueado momentáneamente
            }
            robots[pos[0]][pos[1]] = -1;
            robots[siguiente[0]][siguiente[1]] = id;
            posiciones.put(id, siguiente);
            robot.consumirBateria();
            Logger.log(robot.getNombre(), "MOVER (" + pos[0] + "," + pos[1] + ") -> ("
                    + siguiente[0] + "," + siguiente[1] + ") | Batería: " + robot.getBateria());
            notifyAll();
            return ResultadoTurno.ACTUO;
        }
    }

    public synchronized void retirarRobot(RobotCarga robot) {
        int id = robot.getId();
        int[] pos = posiciones.remove(id);
        if (pos != null) {
            robots[pos[0]][pos[1]] = -1;
        }
        liberarObjetivo(id);
        if (robot.getBateria() <= 0) {
            robotsAgotados++;
            Logger.log(robot.getNombre(), "BATERIA_AGOTADA -> Retiro del tablero");
        } else {
            // Se retira sin haber agotado la batería: condición de parada
            // adicional (no quedan ni quedarán Cajas Objetivo). No se
            // contabiliza como "batería agotada" en el reporte final.
            Logger.log(robot.getNombre(), "RETIRO_SIN_TRABAJO_DISPONIBLE -> Retiro del tablero");
        }
        notifyAll();
    }

    // ---------------------------------------------------------------
    // Reporte final (llamado por el hilo main una vez que todos los
    // hilos han terminado su ejecución)
    // ---------------------------------------------------------------

    public synchronized void imprimirReporteFinal() {
        System.out.println();
        System.out.println("=== REPORTE FINAL DEL ALMACÉN ===");
        System.out.println("- Cajas Objetivo extraídas (llevadas a 5,5): " + cajasExtraidas);
        System.out.println("- Saturaciones del tablero (intentos fallidos de productores): " + saturaciones);
        System.out.println("- Robots que finalizaron por batería agotada: " + robotsAgotados);
        System.out.println();
        System.out.println("=== ESTADO FINAL DEL TABLERO ===");
        for (int i = 0; i < n; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < n; j++) {
                char c = (robots[i][j] != -1) ? 'R' : cajas[i][j];
                sb.append(c);
                if (j < n - 1) sb.append(' ');
            }
            System.out.println(sb.toString());
        }
        System.out.println();
        System.out.println("(Nomenclatura: R = Robot, O = Caja Objetivo, X = Caja de Bloqueo, . = Casilla Vacía)");
    }

    // ---------------------------------------------------------------
    // Utilidades internas (siempre invocadas ya dentro de un método
    // synchronized, por lo que no requieren su propio candado)
    // ---------------------------------------------------------------

    private boolean dentro(int r, int c) {
        return r >= 0 && r < n && c >= 0 && c < n;
    }

    private boolean estaVacia(int r, int c) {
        return dentro(r, c) && cajas[r][c] == '.' && robots[r][c] == -1;
    }

    private boolean tableroLleno() {
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                if (cajas[i][j] == '.' && robots[i][j] == -1) return false;
        return true;
    }

    private boolean hayObjetivoEnTablero() {
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                if (cajas[i][j] == 'O') return true;
        return false;
    }

    private int[] celdaVaciaAleatoria() {
        List<int[]> libres = new ArrayList<>();
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                if (cajas[i][j] == '.' && robots[i][j] == -1) libres.add(new int[]{i, j});
        if (libres.isEmpty()) return null;
        return libres.get(rand.nextInt(libres.size()));
    }

    private int[] elegirObjetivoLibre(int idRobot) {
        int[] pos = posiciones.get(idRobot);
        int[] mejor = null;
        int mejorDist = Integer.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (cajas[i][j] == 'O' && !reservados.contains(clave(i, j))) {
                    int d = Math.abs(pos[0] - i) + Math.abs(pos[1] - j);
                    if (d < mejorDist) {
                        mejorDist = d;
                        mejor = new int[]{i, j};
                    }
                }
            }
        }
        return mejor;
    }

    private void liberarObjetivo(int idRobot) {
        int[] o = objetivoDeRobot.remove(idRobot);
        if (o != null) reservados.remove(clave(o));
    }

    /**
     * Calcula, para una caja objetivo dada, desde qué casilla debe
     * empujarla un robot y a qué casilla se moverá la caja, de modo que
     * se acerque lo más posible a la meta (5,5). Si la dirección óptima
     * no es físicamente posible (por ejemplo, la caja está pegada a un
     * borde), se intenta con las siguientes direcciones en orden de
     * menor a mayor distancia resultante a la meta. Si ninguna dirección
     * es válida, la caja quedó atrapada (deadlock físico tipo esquina) y
     * se retorna null.
     */
    private int[][] calcularEmpuje(int[] caja) {
        final int br = caja[0], bc = caja[1];
        int[][] direcciones = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        List<int[]> lista = new ArrayList<>(Arrays.asList(direcciones));
        lista.sort(Comparator.comparingInt(d ->
                Math.abs(META_FILA - (br + d[0])) + Math.abs(META_COL - (bc + d[1]))));

        for (int[] d : lista) {
            int fr = br - d[0], fc = bc - d[1];
            int tr = br + d[0], tc = bc + d[1];
            if (dentro(fr, fc) && dentro(tr, tc)) {
                return new int[][]{{fr, fc}, {tr, tc}};
            }
        }
        return null;
    }

    /** Calcula el siguiente paso (una celda) desde `actual` hacia `destino`,
     *  moviéndose por el eje que más reduce la distancia primero, y solo
     *  si la celda candidata está realmente vacía. */
    private int[] pasoHacia(int[] actual, int[] destino) {
        int dr = Integer.compare(destino[0], actual[0]);
        int dc = Integer.compare(destino[1], actual[1]);
        List<int[]> candidatos = new ArrayList<>();
        int distFila = Math.abs(destino[0] - actual[0]);
        int distCol = Math.abs(destino[1] - actual[1]);
        if (distFila >= distCol) {
            if (dr != 0) candidatos.add(new int[]{actual[0] + dr, actual[1]});
            if (dc != 0) candidatos.add(new int[]{actual[0], actual[1] + dc});
        } else {
            if (dc != 0) candidatos.add(new int[]{actual[0], actual[1] + dc});
            if (dr != 0) candidatos.add(new int[]{actual[0] + dr, actual[1]});
        }
        for (int[] c : candidatos) {
            if (estaVacia(c[0], c[1])) return c;
        }
        return null;
    }

    private String clave(int r, int c) {
        return r + "," + c;
    }

    private String clave(int[] p) {
        return p[0] + "," + p[1];
    }
}
