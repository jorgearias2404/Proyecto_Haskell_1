import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Tablero {

    public static final int FILAS = 6;
    public static final int COLUMNAS = 6;

    public static final Posicion SALIDA =
            new Posicion(5, 5);

    private final TipoCaja[][] matriz;

    private final List<Robot> robots =
            new ArrayList<>();

    /*
     * Recurso compartido.
     */
    private final ReentrantLock lock =
            new ReentrantLock(true);

    private final Condition espacioDisponible =
            lock.newCondition();

    private int cajasObjetivoExtraidas = 0;

    private int saturaciones = 0;

    private int robotsFinalizados = 0;

    public Tablero() {

        matriz =
                new TipoCaja[FILAS][COLUMNAS];

        for (int i = 0; i < FILAS; i++) {

            for (int j = 0; j < COLUMNAS; j++) {

                matriz[i][j] =
                        TipoCaja.VACIA;
            }
        }
    }

    // =========================================================
    // INICIALIZACIÓN
    // =========================================================

    public void colocarCajaInicial(
            Posicion posicion,
            TipoCaja tipo) {

        lock.lock();

        try {

            if (!dentroDelTablero(posicion)) {

                throw new IllegalArgumentException(
                        "Posición fuera del tablero: "
                        + posicion
                );
            }

            if (posicion.equals(SALIDA)) {

                throw new IllegalArgumentException(
                        "La posición (5,5) está reservada."
                );
            }

            if (matriz[posicion.getFila()]
                    [posicion.getColumna()]
                    != TipoCaja.VACIA) {

                throw new IllegalStateException(
                        "La posición ya está ocupada: "
                        + posicion
                );
            }

            matriz[posicion.getFila()]
                  [posicion.getColumna()] =
                    tipo;

        } finally {

            lock.unlock();
        }
    }

    public void agregarRobot(Robot robot) {

        lock.lock();

        try {

            Posicion posicion =
                    robot.getPosicion();

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

            if (!casillaLibreSinRobots(posicion)) {

                throw new IllegalStateException(
                        "Posición inicial ocupada: "
                        + posicion
                );
            }

            if (hayRobotEn(posicion)) {

                throw new IllegalStateException(
                        "Ya existe un robot en "
                        + posicion
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

            while (
                    noHayEspacio()
                    &&
                    Main.simulacionActiva()
            ) {

                saturaciones++;

                registrar(
                        productorId,
                        "ESPERA_SATURACION",
                        "(Intento de insertar en tablero lleno)"
                );

                try {

                    espacioDisponible.await();

                } catch (InterruptedException e) {

                    Thread.currentThread().interrupt();

                    return false;
                }
            }

            if (!Main.simulacionActiva()) {
                return false;
            }

            List<Posicion> libres =
                    obtenerCasillasLibres();

            if (libres.isEmpty()) {
                return false;
            }

            Posicion posicion =
                    libres.get(
                            random.nextInt(
                                    libres.size()
                            )
                    );

            matriz[posicion.getFila()]
                  [posicion.getColumna()] =
                    tipo;

            String accion;

            if (tipo == TipoCaja.OBJETIVO) {

                accion = "INSERTAR_OBJETIVO";

            } else {

                accion = "INSERTAR_BLOQUEO";
            }

            registrar(
                    productorId,
                    accion,
                    posicion.toString()
            );

            return true;

        } finally {

            lock.unlock();
        }
    }

    // =========================================================
    // TURNO DEL ROBOT
    // =========================================================

    public boolean ejecutarTurnoRobot(
            Robot robot) {

        lock.lock();

        try {

            if (!robot.tieneBateria()) {
                return false;
            }

            /*
             * Primero buscamos el mejor plan disponible.
             */
            Plan mejorPlan =
                    buscarMejorPlan(robot);

            /*
             * Si existe una ruta hacia alguna caja,
             * ejecutamos solamente el primer paso.
             *
             * En el siguiente turno se vuelve a calcular
             * la ruta con el estado actual del tablero.
             */
            if (mejorPlan != null
                    &&
                !mejorPlan.pasos.isEmpty()) {

                Paso paso =
                        mejorPlan.pasos.get(0);

                if (paso.tipo ==
                        TipoPaso.MOVER) {

                    ejecutarMovimiento(
                            robot,
                            paso.destinoRobot
                    );

                    return true;
                }

                if (paso.tipo ==
                        TipoPaso.EMPUJAR) {

                    return ejecutarEmpuje(
                            robot,
                            paso.posicionCaja,
                            paso.destinoCaja
                    );
                }
            }

            /*
             * Si ninguna caja puede ser alcanzada en este
             * momento, el robot explora una casilla libre.
             */
            Posicion movimiento =
                    buscarMovimientoExploracion(robot);

            if (movimiento != null) {

                ejecutarMovimiento(
                        robot,
                        movimiento
                );

                return true;
            }

            return false;

        } finally {

            lock.unlock();
        }
    }

    // =========================================================
    // BUSCAR EL MEJOR PLAN
    // =========================================================

    private Plan buscarMejorPlan(
            Robot robot) {

        Plan mejor = null;

        for (int i = 0; i < FILAS; i++) {

            for (int j = 0; j < COLUMNAS; j++) {

                if (matriz[i][j] !=
                        TipoCaja.OBJETIVO) {

                    continue;
                }

                Posicion caja =
                        new Posicion(i, j);

                Plan plan =
                        buscarPlanParaCaja(
                                robot.getPosicion(),
                                caja
                        );

                if (plan == null) {
                    continue;
                }

                if (mejor == null
                        ||
                    plan.pasos.size()
                        <
                    mejor.pasos.size()) {

                    mejor = plan;
                }
            }
        }

        return mejor;
    }

    // =========================================================
    // BFS ROBOT + CAJA
    // =========================================================

    private Plan buscarPlanParaCaja(
            Posicion robotInicial,
            Posicion cajaInicial) {

        EstadoInicial estadoInicial =
                new EstadoInicial(
                        robotInicial,
                        cajaInicial
                );

        Queue<Estado> cola =
                new ArrayDeque<>();

        Map<Estado, EstadoAnterior> anteriores =
                new HashMap<>();

        Set<Estado> visitados =
                new HashSet<>();

        Estado inicio =
                new Estado(
                        robotInicial,
                        cajaInicial
                );

        cola.add(inicio);

        visitados.add(inicio);

        Estado estadoFinal = null;

        while (!cola.isEmpty()) {

            Estado actual =
                    cola.poll();

            /*
             * Si la caja llegó a la salida,
             * encontramos una solución.
             */
            if (actual.caja.equals(SALIDA)) {

                estadoFinal = actual;
                break;
            }

            for (
                    Direccion direccion :
                    Direccion.values()
            ) {

                Posicion siguienteRobot =
                        sumar(
                                actual.robot,
                                direccion
                        );

                if (!dentroDelTablero(
                        siguienteRobot)) {

                    continue;
                }

                /*
                 * =================================================
                 * CASO 1: movimiento normal del robot
                 * =================================================
                 */

                if (!siguienteRobot.equals(
                        actual.caja)) {

                    if (!puedeRobotOcupar(
                            siguienteRobot,
                            actual.caja
                    )) {

                        continue;
                    }

                    Estado siguiente =
                            new Estado(
                                    siguienteRobot,
                                    actual.caja
                            );

                    if (visitados.add(siguiente)) {

                        anteriores.put(
                                siguiente,
                                new EstadoAnterior(
                                        actual,
                                        new Paso(
                                                TipoPaso.MOVER,
                                                siguienteRobot,
                                                null,
                                                null
                                        )
                                )
                        );

                        cola.add(siguiente);
                    }

                    continue;
                }

                /*
                 * =================================================
                 * CASO 2: el robot está frente a la caja
                 * y quiere empujarla.
                 * =================================================
                 */

                Posicion destinoCaja =
                        sumar(
                                actual.caja,
                                direccion
                        );

                if (!puedeEmpujar(
                        actual.caja,
                        destinoCaja
                )) {

                    continue;
                }

                /*
                 * El robot ocupa la posición que tenía la caja.
                 */
                Estado siguiente =
                        new Estado(
                                actual.caja,
                                destinoCaja
                        );

                if (visitados.add(siguiente)) {

                    anteriores.put(
                            siguiente,
                            new EstadoAnterior(
                                    actual,
                                    new Paso(
                                            TipoPaso.EMPUJAR,
                                            actual.caja,
                                            actual.caja,
                                            destinoCaja
                                    )
                            )
                    );

                    cola.add(siguiente);
                }
            }
        }

        if (estadoFinal == null) {
            return null;
        }

        List<Paso> pasos =
                new ArrayList<>();

        Estado actual =
                estadoFinal;

        while (!actual.equals(inicio)) {

            EstadoAnterior anterior =
                    anteriores.get(actual);

            if (anterior == null) {
                return null;
            }

            pasos.add(
                    anterior.paso
            );

            actual =
                    anterior.estadoAnterior;
        }

        Collections.reverse(pasos);

        return new Plan(
                cajaInicial,
                pasos
        );
    }

    // =========================================================
    // EJECUTAR MOVIMIENTO
    // =========================================================

    private void ejecutarMovimiento(
            Robot robot,
            Posicion destino) {

        robot.setPosicion(destino);

        robot.consumirBateria();

        registrar(
                robot.getId(),
                "MOVER",
                destino +
                " -> Batería: " +
                robot.getBateria()
        );
    }

    // =========================================================
    // EJECUTAR EMPUJE
    // =========================================================
private boolean ejecutarEmpuje(
        Robot robot,
        Posicion caja,
        Posicion destinoCaja) {

    Posicion posicionNecesaria =
            obtenerPosicionRobotParaEmpujar(
                    caja,
                    destinoCaja
            );

    if (!robot.getPosicion().equals(
            posicionNecesaria
    )) {

        return false;
    }

    return realizarEmpuje(
            robot,
            caja,
            destinoCaja
    );
}

    /*
     * Este método realiza el empuje real.
     *
     * La posición del robot debe ser la casilla desde
     * donde empuja, es decir, una casilla adyacente
     * a la caja.
     */
    private boolean realizarEmpuje(
            Robot robot,
            Posicion caja,
            Posicion destinoCaja) {

        if (!dentroDelTablero(destinoCaja)) {
            return false;
        }

        if (!puedeEmpujar(
                caja,
                destinoCaja
        )) {
            return false;
        }

        /*
         * El robot tiene que estar detrás de la caja.
         */
        int df =
                caja.getFila()
                -
                robot.getPosicion().getFila();

        int dc =
                caja.getColumna()
                -
                robot.getPosicion().getColumna();

        int destinoDf =
                destinoCaja.getFila()
                -
                caja.getFila();

        int destinoDc =
                destinoCaja.getColumna()
                -
                caja.getColumna();

        if (df != destinoDf
                ||
            dc != destinoDc) {

            return false;
        }

        /*
         * La salida (5,5) no se almacena como caja:
         * al llegar allí la caja se extrae.
         */
        if (destinoCaja.equals(SALIDA)) {

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

            espacioDisponible.signalAll();

            return true;
        }

        /*
         * Empuje normal.
         */
        matriz[destinoCaja.getFila()]
              [destinoCaja.getColumna()] =
                TipoCaja.OBJETIVO;

        matriz[caja.getFila()]
              [caja.getColumna()] =
                TipoCaja.VACIA;

        robot.setPosicion(caja);

        robot.consumirBateria();

        registrar(
                robot.getId(),
                "EMPUJAR_OBJETIVO",
                caja +
                " -> " +
                destinoCaja +
                " -> Batería: " +
                robot.getBateria()
        );

        return true;
    }

    // =========================================================
    // VALIDAR Y EJECUTAR PASO DE EMPUJE
    // =========================================================

    private boolean puedeEjecutarEmpuje(
            Robot robot,
            Posicion caja,
            Posicion destinoCaja) {

        if (!robot.getPosicion().equals(
                obtenerPosicionRobotParaEmpujar(
                        caja,
                        destinoCaja
                )
        )) {

            return false;
        }

        return puedeEmpujar(
                caja,
                destinoCaja
        );
    }

    private Posicion obtenerPosicionRobotParaEmpujar(
            Posicion caja,
            Posicion destinoCaja) {

        int df =
                destinoCaja.getFila()
                -
                caja.getFila();

        int dc =
                destinoCaja.getColumna()
                -
                caja.getColumna();

        return new Posicion(
                caja.getFila() - df,
                caja.getColumna() - dc
        );
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

        for (Posicion vecino : vecinos) {

            if (puedeRobotOcupar(
                    vecino,
                    null
            )) {

                return vecino;
            }
        }

        return null;
    }

    // =========================================================
    // VALIDACIONES DEL TABLERO
    // =========================================================

    private boolean puedeRobotOcupar(
            Posicion posicion,
            Posicion cajaMovil) {

        if (!dentroDelTablero(posicion)) {
            return false;
        }

        /*
         * La salida está reservada para la caja.
         */
        if (posicion.equals(SALIDA)) {
            return false;
        }

        /*
         * No puede entrar en la caja que está moviendo.
         */
        if (cajaMovil != null
                &&
            posicion.equals(cajaMovil)) {

            return false;
        }

        if (matriz[posicion.getFila()]
                  [posicion.getColumna()]
                != TipoCaja.VACIA) {

            return false;
        }

        return !hayRobotEn(posicion);
    }

    private boolean puedeEmpujar(
            Posicion caja,
            Posicion destino) {

        if (!dentroDelTablero(destino)) {
            return false;
        }

        /*
         * La caja puede entrar en la salida.
         */
        if (destino.equals(SALIDA)) {
            return true;
        }

        /*
         * Para un empuje normal, la casilla destino
         * debe estar completamente libre.
         */
        if (matriz[destino.getFila()]
                  [destino.getColumna()]
                != TipoCaja.VACIA) {

            return false;
        }

        /*
         * Tampoco puede empujarse una caja encima
         * de otro robot.
         */
        if (hayRobotEn(destino)) {
            return false;
        }

        return true;
    }

    private boolean casillaLibreSinRobots(
            Posicion posicion) {

        if (!dentroDelTablero(posicion)) {
            return false;
        }

        if (posicion.equals(SALIDA)) {
            return false;
        }

        return matriz[posicion.getFila()]
                    [posicion.getColumna()]
                == TipoCaja.VACIA;
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
                    random.nextInt(
                            libres.size()
                    )
            );

        } finally {

            lock.unlock();
        }
    }

    // =========================================================
    // VECINOS
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

    private Posicion sumar(
            Posicion posicion,
            Direccion direccion) {

        switch (direccion) {

            case ARRIBA:
                return posicion.arriba();

            case ABAJO:
                return posicion.abajo();

            case IZQUIERDA:
                return posicion.izquierda();

            case DERECHA:
                return posicion.derecha();

            default:
                throw new IllegalStateException();
        }
    }

    // =========================================================
    // REGISTRO
    // =========================================================

    private void registrar(
            String hilo,
            String accion,
            String detalles) {

        System.out.println(
                "[Tick-" +
                String.format(
                        "%02d",
                        Main.siguienteTick()
                ) +
                "] [" +
                hilo +
                "] " +
                accion +
                " -> " +
                detalles
        );
    }

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

    public void registrarRobotFinalizado(
            Robot robot) {

        lock.lock();

        try {

            robotsFinalizados++;

            registrar(
                    robot.getId(),
                    "BATERIA_AGOTADA",
                    "-> Posición final " +
                    robot.getPosicion()
            );

        } finally {

            lock.unlock();
        }
    }

    // =========================================================
    // REPORTE FINAL
    // =========================================================

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

    // =========================================================
    // CLASES INTERNAS PARA BFS
    // =========================================================

    private enum Direccion {
        ARRIBA,
        ABAJO,
        IZQUIERDA,
        DERECHA
    }

    private enum TipoPaso {
        MOVER,
        EMPUJAR
    }

    private static class Paso {

        private final TipoPaso tipo;
        private final Posicion destinoRobot;
        private final Posicion posicionCaja;
        private final Posicion destinoCaja;

        public Paso(
                TipoPaso tipo,
                Posicion destinoRobot,
                Posicion posicionCaja,
                Posicion destinoCaja) {

            this.tipo = tipo;
            this.destinoRobot = destinoRobot;
            this.posicionCaja = posicionCaja;
            this.destinoCaja = destinoCaja;
        }
    }

    private static class Plan {

        private final Posicion caja;
        private final List<Paso> pasos;

        public Plan(
                Posicion caja,
                List<Paso> pasos) {

            this.caja = caja;
            this.pasos = pasos;
        }
    }

    private static class Estado {

        private final Posicion robot;
        private final Posicion caja;

        public Estado(
                Posicion robot,
                Posicion caja) {

            this.robot = robot;
            this.caja = caja;
        }

        @Override
        public boolean equals(Object obj) {

            if (this == obj) {
                return true;
            }

            if (!(obj instanceof Estado)) {
                return false;
            }

            Estado otro =
                    (Estado) obj;

            return robot.equals(otro.robot)
                    &&
                   caja.equals(otro.caja);
        }

        @Override
        public int hashCode() {

            return robot.hashCode() * 31
                    +
                   caja.hashCode();
        }
    }

    private static class EstadoAnterior {

        private final Estado estadoAnterior;
        private final Paso paso;

        public EstadoAnterior(
                Estado estadoAnterior,
                Paso paso) {

            this.estadoAnterior =
                    estadoAnterior;

            this.paso = paso;
        }
    }

    /*
     * Clase auxiliar solamente para documentar
     * claramente el estado inicial.
     */
    private static class EstadoInicial {

        private final Posicion robot;
        private final Posicion caja;

        public EstadoInicial(
                Posicion robot,
                Posicion caja) {

            this.robot = robot;
            this.caja = caja;
        }
    }
}