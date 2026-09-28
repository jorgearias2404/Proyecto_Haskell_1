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

    public static void main(String[] args) {

        if (args.length != 1) {

            System.err.println(
                    "Uso: java Main <archivo_entrada>"
            );

            return;
        }

        try {

            Configuracion configuracion =
                    leerArchivo(args[0]);

            validarConfiguracion(
                    configuracion
            );

            Tablero tablero =
                    new Tablero();

            inicializarCajas(
                    tablero,
                    configuracion
            );

            List<Robot> robots =
                    crearRobots(
                            tablero,
                            configuracion
                    );

            ROBOTS_ACTIVOS.set(
                    robots.size()
            );

            ACTIVA.set(true);

            List<Thread> hilosProductores =
                    new ArrayList<>();

            List<Thread> hilosRobots =
                    new ArrayList<>();

            /*
             * Crear productores.
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

                hilosProductores.add(hilo);
            }

            /*
             * Crear robots.
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

                hilosRobots.add(hilo);
            }

            /*
             * Primero iniciamos productores.
             */
            for (Thread hilo :
                    hilosProductores) {

                hilo.start();
            }

            /*
             * Luego iniciamos robots.
             */
            for (Thread hilo :
                    hilosRobots) {

                hilo.start();
            }

            /*
             * Esperamos a todos los robots.
             */
            for (Thread hilo :
                    hilosRobots) {

                hilo.join();
            }

            /*
             * Ya no quedan consumidores.
             * Detenemos productores.
             */
            ACTIVA.set(false);

            /*
             * Despertar productores que puedan estar
             * esperando en Condition.await().
             */
            for (Thread hilo :
                    hilosProductores) {

                hilo.interrupt();
            }

            /*
             * Esperamos a los productores.
             */
            for (Thread hilo :
                    hilosProductores) {

                hilo.join();
            }

            /*
             * Reporte final.
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
    // LECTURA
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
         * 36 casillas - 1 casilla reservada para (5,5).
         */
        int posicionesNecesarias =
                c.getCajasObjetivoIniciales()
                +
                c.getCajasBloqueoIniciales()
                +
                c.getCantidadRobots();

        if (posicionesNecesarias > 35) {

            throw new IllegalArgumentException(
                    "Hay demasiadas entidades iniciales. " +
                    "El máximo es 35 porque (5,5) está reservado."
            );
        }
    }

    // =========================================================
    // CAJAS INICIALES
    // =========================================================

    private static void inicializarCajas(
            Tablero tablero,
            Configuracion c) {

        Random random =
                new Random();

        List<Posicion> posiciones =
                new ArrayList<>();

        for (int i = 0;
             i < Tablero.FILAS;
             i++) {

            for (int j = 0;
                 j < Tablero.COLUMNAS;
                 j++) {

                Posicion posicion =
                        new Posicion(i, j);

                if (!posicion.equals(
                        Tablero.SALIDA
                )) {

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
         * Cajas bloqueo.
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
    // ROBOTS
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
                        "para el Robot-" + i
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
    // ESTADO DE LA SIMULACIÓN
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