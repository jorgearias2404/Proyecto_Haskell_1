import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    private static final AtomicInteger TICK =
            new AtomicInteger(0);

    private static final AtomicBoolean ACTIVA =
            new AtomicBoolean(true);

    private static final AtomicInteger
            ROBOTS_ACTIVOS =
            new AtomicInteger(0);

    // =========================================================
    // MAIN
    // =========================================================

    public static void main(String[] args) {

        if (args.length != 1) {

            System.err.println(
                    "Uso: java Main <archivo_entrada>"
            );

            return;
        }

        try {

            /*
             * 1. Leer archivo.
             */
            Configuracion configuracion =
                    leerArchivo(args[0]);

            /*
             * 2. Validar.
             */
            validarConfiguracion(
                    configuracion
            );

            /*
             * 3. Crear tablero.
             */
            Tablero tablero =
                    new Tablero();

            /*
             * 4. Inicializar cajas.
             */
            inicializarCajas(
                    tablero,
                    configuracion
            );

            /*
             * 5. Crear robots.
             */
            List<Robot> robots =
                    crearRobots(
                            tablero,
                            configuracion
                    );

            ROBOTS_ACTIVOS.set(
                    robots.size()
            );

            ACTIVA.set(true);

            /*
             * 6. Crear hilos.
             */
            List<Thread> productores =
                    new ArrayList<>();

            List<Thread> robotsHilos =
                    new ArrayList<>();

            /*
             * Productores.
             */
            for (
                    int i = 1;
                    i <= configuracion
                            .getCantidadProductores();
                    i++
            ) {

                Thread hilo =
                        new Thread(
                                new Productor(
                                        "Productor-" + i,
                                        tablero
                                ),
                                "Productor-" + i
                        );

                productores.add(hilo);
            }

            /*
             * Robots.
             */
            for (Robot robot : robots) {

                Thread hilo =
                        new Thread(
                                new RobotHilo(
                                        robot,
                                        tablero
                                ),
                                robot.getId()
                        );

                robotsHilos.add(hilo);
            }

            /*
             * 7. Iniciar productores.
             */
            for (Thread hilo : productores) {
                hilo.start();
            }

            /*
             * 8. Iniciar robots.
             */
            for (Thread hilo : robotsHilos) {
                hilo.start();
            }

            /*
             * 9. Esperar a los robots.
             */
            for (Thread hilo : robotsHilos) {

                hilo.join();
            }

            /*
             * 10. Cuando no quedan robots, los productores
             * terminan mediante ACTIVA = false.
             */
            for (Thread hilo : productores) {

                /*
                 * Si algún productor está esperando en
                 * Condition.await(), lo despertamos.
                 */
                hilo.interrupt();
            }

            /*
             * 11. Esperar a los productores.
             */
            for (Thread hilo : productores) {

                hilo.join();
            }

            /*
             * 12. Reporte final.
             */
            tablero.imprimirReporteFinal();

        } catch (IOException e) {

            System.err.println(
                    "Error leyendo el archivo: "
                    + e.getMessage()
            );

        } catch (NumberFormatException e) {

            System.err.println(
                    "Error: uno de los valores del archivo " +
                    "no es un número válido."
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            System.err.println(
                    "La ejecución fue interrumpida."
            );

        } catch (IllegalArgumentException e) {

            System.err.println(
                    "Error en la configuración: "
                    + e.getMessage()
            );
        }
    }

    // =========================================================
    // LECTURA DEL ARCHIVO
    // =========================================================

    private static Configuracion leerArchivo(
            String nombreArchivo)
            throws IOException {

        List<String> lineas =
                Files.readAllLines(
                        Path.of(nombreArchivo)
                );

        Configuracion configuracion =
                new Configuracion();

        for (String linea : lineas) {

            linea = linea.trim();

            if (linea.isEmpty()) {
                continue;
            }

            String[] partes =
                    linea.split(",");

            String parametro =
                    partes[0].trim();

            switch (parametro) {

                case "Cajas_Objetivo_Iniciales":

                    if (partes.length != 2) {

                        throw new IllegalArgumentException(
                                "Formato incorrecto para " +
                                "Cajas_Objetivo_Iniciales."
                        );
                    }

                    configuracion
                            .setCajasObjetivoIniciales(
                                    Integer.parseInt(
                                            partes[1].trim()
                                    )
                            );

                    break;

                case "Cajas_Bloqueo_Iniciales":

                    if (partes.length != 2) {

                        throw new IllegalArgumentException(
                                "Formato incorrecto para " +
                                "Cajas_Bloqueo_Iniciales."
                        );
                    }

                    configuracion
                            .setCajasBloqueoIniciales(
                                    Integer.parseInt(
                                            partes[1].trim()
                                    )
                            );

                    break;

                case "Robots":

                    if (partes.length != 3) {

                        throw new IllegalArgumentException(
                                "Formato incorrecto para Robots. " +
                                "Debe ser: Robots, cantidad, bateria"
                        );
                    }

                    configuracion
                            .setCantidadRobots(
                                    Integer.parseInt(
                                            partes[1].trim()
                                    )
                            );

                    configuracion
                            .setBateriaInicial(
                                    Integer.parseInt(
                                            partes[2].trim()
                                    )
                            );

                    break;

                case "Productores":

                    if (partes.length != 2) {

                        throw new IllegalArgumentException(
                                "Formato incorrecto para Productores."
                        );
                    }

                    configuracion
                            .setCantidadProductores(
                                    Integer.parseInt(
                                            partes[1].trim()
                                    )
                            );

                    break;

                default:

                    throw new IllegalArgumentException(
                            "Parámetro desconocido: "
                            + parametro
                    );
            }
        }

        return configuracion;
    }

    // =========================================================
    // VALIDACIÓN
    // =========================================================

    private static void validarConfiguracion(
            Configuracion c) {

        if (c.getCajasObjetivoIniciales() < 0) {

            throw new IllegalArgumentException(
                    "Las cajas objetivo no pueden ser negativas."
            );
        }

        if (c.getCajasBloqueoIniciales() < 0) {

            throw new IllegalArgumentException(
                    "Las cajas de bloqueo no pueden ser negativas."
            );
        }

        if (c.getCantidadRobots() <= 0) {

            throw new IllegalArgumentException(
                    "Debe existir al menos un robot."
            );
        }

        if (c.getBateriaInicial() <= 0) {

            throw new IllegalArgumentException(
                    "La batería debe ser mayor que cero."
            );
        }

        if (c.getCantidadProductores() <= 0) {

            throw new IllegalArgumentException(
                    "Debe existir al menos un productor."
            );
        }

        /*
         * El (5,5) está reservado.
         *
         * Por tanto, solamente tenemos 35 posiciones
         * disponibles para las entidades iniciales.
         */
        int entidadesIniciales =
                c.getCajasObjetivoIniciales()
                +
                c.getCajasBloqueoIniciales()
                +
                c.getCantidadRobots();

        if (entidadesIniciales > 35) {

            throw new IllegalArgumentException(
                    "Hay demasiadas entidades iniciales. " +
                    "El tablero dispone de 35 posiciones " +
                    "porque (5,5) está reservado."
            );
        }
    }

    // =========================================================
    // INICIALIZAR CAJAS
    // =========================================================

    private static void inicializarCajas(
            Tablero tablero,
            Configuracion c) {

        Random random = new Random();

        List<Posicion> posiciones =
                new ArrayList<>();

        /*
         * El (5,5) queda reservado.
         */
        for (int i = 0; i < Tablero.FILAS; i++) {

            for (int j = 0;
                 j < Tablero.COLUMNAS;
                 j++) {

                Posicion posicion =
                        new Posicion(i, j);

                if (!posicion.equals(
                        Tablero.SALIDA)) {

                    posiciones.add(posicion);
                }
            }
        }

        Collections.shuffle(
                posiciones,
                random
        );

        int indice = 0;

        /*
         * Cajas objetivo.
         */
        for (
                int i = 0;
                i < c.getCajasObjetivoIniciales();
                i++
        ) {

            tablero.colocarCajaInicial(
                    posiciones.get(indice++),
                    TipoCaja.OBJETIVO
            );
        }

        /*
         * Cajas de bloqueo.
         */
        for (
                int i = 0;
                i < c.getCajasBloqueoIniciales();
                i++
        ) {

            tablero.colocarCajaInicial(
                    posiciones.get(indice++),
                    TipoCaja.BLOQUEO
            );
        }
    }

    // =========================================================
    // CREAR ROBOTS
    // =========================================================

    private static List<Robot> crearRobots(
            Tablero tablero,
            Configuracion c) {

        List<Robot> robots =
                new ArrayList<>();

        Random random =
                new Random();

        for (
                int i = 1;
                i <= c.getCantidadRobots();
                i++
        ) {

            Posicion posicion =
                    tablero.obtenerPosicionLibreAleatoria(
                            random
                    );

            if (posicion == null) {

                throw new IllegalArgumentException(
                        "No existe una posición libre " +
                        "para colocar al robot " + i
                );
            }

            Robot robot =
                    new Robot(
                            "Robot-" + i,
                            i,
                            c.getBateriaInicial(),
                            posicion
                    );

            tablero.agregarRobot(robot);

            robots.add(robot);
        }

        return robots;
    }

    // =========================================================
    // CONTROL DE SIMULACIÓN
    // =========================================================

    public static int siguienteTick() {
        return TICK.incrementAndGet();
    }

    public static boolean simulacionActiva() {
        return ACTIVA.get();
    }

    public static void robotTerminado() {

        int restantes =
                ROBOTS_ACTIVOS.decrementAndGet();

        if (restantes <= 0) {

            ACTIVA.set(false);
        }
    }
}