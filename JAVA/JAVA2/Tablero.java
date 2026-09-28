import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Tablero {

    public static final int FILAS = 6;
    public static final int COLUMNAS = 6;

    public static final Posicion SALIDA =
            new Posicion(5, 5);

    private final TipoCaja[][] matriz;

    private final List<Robot> robots;

    /*
     * Lock principal del recurso compartido.
     */
    private final ReentrantLock lock =
            new ReentrantLock(true);

    /*
     * Los productores esperan aquí cuando no existen
     * casillas disponibles.
     */
    private final Condition espacioDisponible =
            lock.newCondition();

    /*
     * Contadores para el reporte final.
     */
    private int cajasObjetivoExtraidas = 0;
    private int saturaciones = 0;
    private int robotsFinalizados = 0;

    public Tablero() {

        matriz = new TipoCaja[FILAS][COLUMNAS];

        robots = new ArrayList<>();

        for (int i = 0; i < FILAS; i++) {

            for (int j = 0; j < COLUMNAS; j++) {

                matriz[i][j] = TipoCaja.VACIA;
            }
        }
    }

    // =========================================================
    // OPERACIONES DE INICIALIZACIÓN
    // =========================================================

    public void colocarCajaInicial(
            Posicion posicion,
            TipoCaja tipo) {

        lock.lock();

        try {

            if (!dentroDelTablero(posicion)) {
                throw new IllegalArgumentException(
                        "Posición fuera del tablero: " + posicion
                );
            }

            if (posicion.equals(SALIDA)) {
                throw new IllegalArgumentException(
                        "La casilla (5,5) está reservada para la salida."
                );
            }

            if (matriz[posicion.getFila()][posicion.getColumna()]
                    != TipoCaja.VACIA) {

                throw new IllegalStateException(
                        "La posición ya contiene una caja: " + posicion
                );
            }

            matriz[posicion.getFila()][posicion.getColumna()] =
                    tipo;

        } finally {
            lock.unlock();
        }
    }

    public void agregarRobot(Robot robot) {

        lock.lock();

        try {

            Posicion posicion = robot.getPosicion();

            if (!dentroDelTablero(posicion)) {

                throw new IllegalArgumentException(
                        "Posición del robot fuera del tablero."
                );
            }

            if (posicion.equals(SALIDA)) {

                throw new IllegalArgumentException(
                        "Un robot no puede comenzar en (5,5)."
                );
            }

            if (matriz[posicion.getFila()][posicion.getColumna()]
                    != TipoCaja.VACIA) {

                throw new IllegalStateException(
                        "El robot comienza sobre una caja."
                );
            }

            if (hayRobotEn(posicion)) {

                throw new IllegalStateException(
                        "Ya existe un robot en " + posicion
                );
            }

            robots.add(robot);

        } finally {
            lock.unlock();
        }
    }

    // =========================================================
    // PRODUCTORES
    // =========================================================

    public boolean insertarCaja(
            String productorId,
            TipoCaja tipo,
            Random random) {

        lock.lock();

        try {

            /*
             * Si no hay espacio, el productor espera.
             */
            while (noHayEspacio()) {

                saturaciones++;

                System.out.println(
                        "[Tick-" + Main.siguienteTick() + "] " +
                        "[" + productorId + "] " +
                        "ESPERA_SATURACION -> " +
                        "(Intento de insertar en tablero lleno)"
                );

                try {

                    espacioDisponible.await();

                } catch (InterruptedException e) {

                    Thread.currentThread().interrupt();

                    return false;
                }
            }

            /*
             * Buscamos las casillas realmente libres.
             */
            List<Posicion> libres =
                    obtenerCasillasLibres();

            if (libres.isEmpty()) {
                return false;
            }

            Posicion posicion =
                    libres.get(
                            random.nextInt(libres.size())
                    );

            matriz[posicion.getFila()][posicion.getColumna()] =
                    tipo;

            String nombre;

            if (tipo == TipoCaja.OBJETIVO) {
                nombre = "INSERTAR_OBJETIVO";
            } else {
                nombre = "INSERTAR_BLOQUEO";
            }

            System.out.println(
                    "[Tick-" + Main.siguienteTick() + "] " +
                    "[" + productorId + "] " +
                    nombre +
                    " -> " + posicion
            );

            return true;

        } finally {
            lock.unlock();
        }
    }

    // =========================================================
    // ROBOTS
    // =========================================================

    public boolean ejecutarMovimientoRobot(
            Robot robot) {

        lock.lock();

        try {

            if (!robot.tieneBateria()) {
                return false;
            }

            /*
             * Primero buscamos una caja objetivo.
             */
            Posicion objetivo =
                    buscarObjetivoMasCercano(robot);

            /*
             * Si no existe objetivo, el robot explora.
             */
            if (objetivo == null) {

                Posicion movimiento =
                        buscarMovimientoExploracion(robot);

                if (movimiento == null) {
                    return false;
                }

                moverRobot(robot, movimiento);

                robot.consumirBateria();

                registrar(
                        robot.getId(),
                        "MOVER",
                        movimiento +
                        " -> Batería: " +
                        robot.getBateria()
                );

                return true;
            }

            /*
             * Si la caja ya está en (5,5), se considera entregada.
             * En condiciones normales esto no debería ocurrir porque
             * la extracción se realiza al empujarla.
             */
            if (objetivo.equals(SALIDA)) {

                matriz[objetivo.getFila()]
                      [objetivo.getColumna()] =
                        TipoCaja.VACIA;

                cajasObjetivoExtraidas++;

                espacioDisponible.signalAll();

                return true;
            }

            /*
             * ¿Podemos empujarla inmediatamente?
             */
            if (puedeEmpujar(robot, objetivo)) {

                empujarCaja(robot, objetivo);

                return true;
            }

            /*
             * Buscamos la mejor posición desde donde empujar.
             */
            Posicion posicionEmpuje =
                    obtenerPosicionEmpuje(objetivo);

            if (posicionEmpuje != null) {

                List<Posicion> camino =
                        buscarCamino(
                                robot.getPosicion(),
                                posicionEmpuje
                        );

                if (camino != null &&
                    camino.size() >= 2) {

                    Posicion siguiente =
                            camino.get(1);

                    moverRobot(robot, siguiente);

                    robot.consumirBateria();

                    registrar(
                            robot.getId(),
                            "MOVER",
                            siguiente +
                            " -> Batería: " +
                            robot.getBateria()
                    );

                    return true;
                }
            }

            /*
             * Si no puede llegar a la posición de empuje,
             * intenta explorar.
             */
            Posicion exploracion =
                    buscarMovimientoExploracion(robot);

            if (exploracion != null) {

                moverRobot(robot, exploracion);

                robot.consumirBateria();

                registrar(
                        robot.getId(),
                        "MOVER",
                        exploracion +
                        " -> Batería: " +
                        robot.getBateria()
                );

                return true;
            }

            return false;

        } finally {
            lock.unlock();
        }
    }

    // =========================================================
    // BUSCAR OBJETIVO
    // =========================================================

    private Posicion buscarObjetivoMasCercano(
            Robot robot) {

        Posicion mejor = null;

        int menorDistancia =
                Integer.MAX_VALUE;

        for (int i = 0; i < FILAS; i++) {

            for (int j = 0; j < COLUMNAS; j++) {

                if (matriz[i][j] !=
                        TipoCaja.OBJETIVO) {

                    continue;
                }

                Posicion posicion =
                        new Posicion(i, j);

                int distancia =
                        Math.abs(
                                robot.getPosicion().getFila()
                                - i
                        )
                        +
                        Math.abs(
                                robot.getPosicion().getColumna()
                                - j
                        );

                if (distancia < menorDistancia) {

                    menorDistancia = distancia;

                    mejor = posicion;
                }
            }
        }

        return mejor;
    }

    // =========================================================
    // POSICIÓN DESDE LA QUE SE PUEDE EMPUJAR
    // =========================================================

    private Posicion obtenerPosicionEmpuje(
        Posicion caja) {

    List<Posicion> candidatos =
            new ArrayList<>();

    /*
     * Posición necesaria para empujar hacia abajo.
     */
    if (caja.getFila() > 0 &&
        caja.getFila() < 5) {

        candidatos.add(
                new Posicion(
                        caja.getFila() - 1,
                        caja.getColumna()
                )
        );
    }

    /*
     * Posición necesaria para empujar hacia la derecha.
     */
    if (caja.getColumna() > 0 &&
        caja.getColumna() < 5) {

        candidatos.add(
                new Posicion(
                        caja.getFila(),
                        caja.getColumna() - 1
                )
        );
    }

    /*
     * Buscamos una posición válida.
     */
    for (Posicion candidato : candidatos) {

        if (casillaLibreParaRobot(candidato)) {
            return candidato;
        }
    }

    return null;
}

    // =========================================================
    // EMPUJAR
    // =========================================================

    private boolean puedeEmpujar(
            Robot robot,
            Posicion caja) {

        Posicion robotPos =
                robot.getPosicion();

        /*
         * Empujar hacia la derecha.
         */
        if (robotPos.getFila() ==
                caja.getFila()
                &&
            robotPos.getColumna() ==
                caja.getColumna() - 1) {

            Posicion destino =
                    new Posicion(
                            caja.getFila(),
                            caja.getColumna() + 1
                    );

            return dentroDelTablero(destino)
                    &&
                    casillaLibreParaRobot(destino);
        }

        /*
         * Empujar hacia abajo.
         */
        if (robotPos.getColumna() ==
                caja.getColumna()
                &&
            robotPos.getFila() ==
                caja.getFila() - 1) {

            Posicion destino =
                    new Posicion(
                            caja.getFila() + 1,
                            caja.getColumna()
                    );

            /*
             * (5,5) es especial: es la salida.
             * No se considera una casilla normal.
             */
            if (destino.equals(SALIDA)) {
                return true;
            }

            return dentroDelTablero(destino)
                    &&
                    casillaLibreParaRobot(destino);
        }

        return false;
    }

    private void empujarCaja(
            Robot robot,
            Posicion caja) {

        Posicion destino;

        /*
         * Derecha.
         */
        if (robot.getPosicion().getFila() ==
                caja.getFila()
                &&
            robot.getPosicion().getColumna() ==
                caja.getColumna() - 1) {

            destino =
                    new Posicion(
                            caja.getFila(),
                            caja.getColumna() + 1
                    );

        /*
         * Abajo.
         */
        } else {

            destino =
                    new Posicion(
                            caja.getFila() + 1,
                            caja.getColumna()
                    );
        }

        /*
         * Si llega a (5,5), se extrae.
         */
        if (destino.equals(SALIDA)) {

            matriz[caja.getFila()]
                  [caja.getColumna()] =
                    TipoCaja.VACIA;

            robot.setPosicion(caja);

            robot.consumirBateria();

            cajasObjetivoExtraidas++;

            registrar(
                    robot.getId(),
                    "EXTRACCION_EXITOSA",
                    "-> (5,5)"
            );

            /*
             * Acabamos de liberar una casilla.
             */
            espacioDisponible.signalAll();

            return;
        }

        /*
         * Empuje normal.
         */
        matriz[destino.getFila()]
              [destino.getColumna()] =
                TipoCaja.OBJETIVO;

        matriz[caja.getFila()]
              [caja.getColumna()] =
                TipoCaja.VACIA;

        robot.setPosicion(caja);

        robot.consumirBateria();

        registrar(
                robot.getId(),
                "EMPUJAR_OBJETIVO",
                caja + " -> " + destino +
                " -> Batería: " +
                robot.getBateria()
        );
    }

    // =========================================================
    // CAMINO BFS
    // =========================================================

    private List<Posicion> buscarCamino(
            Posicion inicio,
            Posicion destino) {

        Queue<Posicion> cola =
                new LinkedList<>();

        Map<Posicion, Posicion> padre =
                new HashMap<>();

        Set<Posicion> visitados =
                new HashSet<>();

        cola.add(inicio);

        visitados.add(inicio);

        while (!cola.isEmpty()) {

            Posicion actual =
                    cola.poll();

            if (actual.equals(destino)) {

                return reconstruirCamino(
                        padre,
                        inicio,
                        destino
                );
            }

            for (Posicion vecino :
                    obtenerVecinos(actual)) {

                if (!dentroDelTablero(vecino)) {
                    continue;
                }

                if (visitados.contains(vecino)) {
                    continue;
                }

                /*
                 * El destino debe ser libre.
                 */
                if (!vecino.equals(destino)
                        &&
                    !casillaLibreParaRobot(vecino)) {

                    continue;
                }

                visitados.add(vecino);

                padre.put(vecino, actual);

                cola.add(vecino);
            }
        }

        return null;
    }

    private List<Posicion> reconstruirCamino(
            Map<Posicion, Posicion> padre,
            Posicion inicio,
            Posicion destino) {

        LinkedList<Posicion> camino =
                new LinkedList<>();

        Posicion actual = destino;

        while (actual != null) {

            camino.addFirst(actual);

            if (actual.equals(inicio)) {
                break;
            }

            actual = padre.get(actual);
        }

        return camino;
    }

    // =========================================================
    // MOVIMIENTO DE EXPLORACIÓN
    // =========================================================

    private Posicion buscarMovimientoExploracion(
            Robot robot) {

        List<Posicion> vecinos =
                obtenerVecinos(
                        robot.getPosicion()
                );

        Collections.shuffle(vecinos);

        for (Posicion posicion : vecinos) {

            if (casillaLibreParaRobot(posicion)) {
                return posicion;
            }
        }

        return null;
    }

    private void moverRobot(
            Robot robot,
            Posicion destino) {

        robot.setPosicion(destino);
    }

    // =========================================================
    // CASILLAS Y VECINOS
    // =========================================================

    private List<Posicion> obtenerVecinos(
            Posicion posicion) {

        List<Posicion> vecinos =
                new ArrayList<>();

        vecinos.add(posicion.arriba());
        vecinos.add(posicion.abajo());
        vecinos.add(posicion.izquierda());
        vecinos.add(posicion.derecha());

        return vecinos;
    }

    private boolean casillaLibreParaRobot(
            Posicion posicion) {

        if (!dentroDelTablero(posicion)) {
            return false;
        }

        /*
         * La salida no se usa como casilla normal.
         */
        if (posicion.equals(SALIDA)) {
            return false;
        }

        if (matriz[posicion.getFila()]
                  [posicion.getColumna()]
                != TipoCaja.VACIA) {

            return false;
        }

        return !hayRobotEn(posicion);
    }

    private boolean hayRobotEn(
            Posicion posicion) {

        for (Robot robot : robots) {

            if (robot.getPosicion()
                    .equals(posicion)) {

                return true;
            }
        }

        return false;
    }

    private boolean dentroDelTablero(
            Posicion posicion) {

        return posicion.getFila() >= 0
                &&
               posicion.getFila() < FILAS
                &&
               posicion.getColumna() >= 0
                &&
               posicion.getColumna() < COLUMNAS;
    }

    private boolean noHayEspacio() {

        for (int i = 0; i < FILAS; i++) {

            for (int j = 0; j < COLUMNAS; j++) {

                Posicion posicion =
                        new Posicion(i, j);

                if (posicion.equals(SALIDA)) {
                    continue;
                }

                if (matriz[i][j] ==
                        TipoCaja.VACIA
                        &&
                    !hayRobotEn(posicion)) {

                    return false;
                }
            }
        }

        return true;
    }

    public Posicion obtenerPosicionLibreAleatoria(
            Random random) {

        lock.lock();

        try {

            List<Posicion> libres =
                    obtenerCasillasLibres();

            if (libres.isEmpty()) {
                return null;
            }

            return libres.get(
                    random.nextInt(libres.size())
            );

        } finally {
            lock.unlock();
        }
    }

    private List<Posicion> obtenerCasillasLibres() {

        List<Posicion> libres =
                new ArrayList<>();

        for (int i = 0; i < FILAS; i++) {

            for (int j = 0; j < COLUMNAS; j++) {

                Posicion posicion =
                        new Posicion(i, j);

                if (posicion.equals(SALIDA)) {
                    continue;
                }

                if (matriz[i][j] ==
                        TipoCaja.VACIA
                        &&
                    !hayRobotEn(posicion)) {

                    libres.add(posicion);
                }
            }
        }

        return libres;
    }

    // =========================================================
    // TRAZAS
    // =========================================================

    private void registrar(
            String hilo,
            String accion,
            String detalles) {

        System.out.println(
                "[Tick-" +
                Main.siguienteTick() +
                "] [" +
                hilo +
                "] " +
                accion +
                " -> " +
                detalles
        );
    }

    // =========================================================
    // REPORTE FINAL
    // =========================================================

    public void registrarInicioRobot(
        Robot robot) {

    registrar(
            robot.getId(),
            "INICIO",
            "Posición inicial " +
            robot.getPosicion() +
            " -> Batería: " +
            robot.getBateria()
    );
}
    public void registrarRobotFinalizado() {

        lock.lock();

        try {
            robotsFinalizados++;
        } finally {
            lock.unlock();
        }
    }

    public void imprimirReporteFinal() {

        lock.lock();

        try {

            System.out.println();
            System.out.println(
                    "=== REPORTE FINAL DEL ALMACEN ==="
            );

            System.out.println(
                    "Cajas objetivo extraídas " +
                    "(llevadas a 5,5): " +
                    cajasObjetivoExtraidas
            );

            System.out.println(
                    "Saturaciones del tablero " +
                    "(intentos fallidos de productores): " +
                    saturaciones
            );

            System.out.println(
                    "Robots que finalizaron por batería agotada: " +
                    robotsFinalizados
            );

            System.out.println();

            System.out.println(
                    "=== ESTADO FINAL DEL TABLERO ==="
            );

            for (int i = 0; i < FILAS; i++) {

                for (int j = 0; j < COLUMNAS; j++) {

                    Posicion posicion =
                            new Posicion(i, j);

                    char simbolo = '.';

                    Robot robot =
                            obtenerRobot(posicion);

                    if (robot != null) {

                        simbolo = 'R';

                    } else if (
                            matriz[i][j] ==
                            TipoCaja.OBJETIVO) {

                        simbolo = 'O';

                    } else if (
                            matriz[i][j] ==
                            TipoCaja.BLOQUEO) {

                        simbolo = 'X';
                    }

                    System.out.print(
                            simbolo + " "
                    );
                }

                System.out.println();
            }

        } finally {
            lock.unlock();
        }
    }

    private Robot obtenerRobot(
            Posicion posicion) {

        for (Robot robot : robots) {

            if (robot.getPosicion()
                    .equals(posicion)) {

                return robot;
            }
        }

        return null;
    }
}