import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * SimulacionRobotsSokoban
 * -------------------------
 * Punto de entrada del programa (hilo main). Responsabilidades:
 *   1. Leer y parsear el archivo de configuración (TXT) recibido como
 *      argumento.
 *   2. Validar que la configuración inicial sea físicamente posible
 *      (que la cantidad total de entidades no exceda la capacidad del
 *      tablero de 6x6 = 36 casillas).
 *   3. Instanciar el MonitorTablero (recurso compartido) y colocar en
 *      él, en posiciones aleatorias vacías, las cajas y robots
 *      iniciales.
 *   4. Crear y arrancar los hilos Productor y Robot.
 *   5. Esperar (join) a que todos los ROBOTS terminen su ejecución.
 *   6. Señalar a los productores que la simulación terminó y esperarlos
 *      también (join), garantizando un cierre ordenado sin hilos huérfanos.
 *   7. Imprimir el Reporte Final y el Estado Final del tablero.
 *
 * Uso:
 *   java SimulacionRobotsSokoban archivo_entrada.txt
 */
public class SimulacionRobotsSokoban {

    private static final int N = 6; // tablero 6x6 (0,0) a (5,5)

    // Rango de retardo (ms) entre intentos de inserción de cada productor.
    private static final int PRODUCTOR_DELAY_MIN_MS = 300;
    private static final int PRODUCTOR_DELAY_MAX_MS = 900;

    // Ritmo (ms) de cada turno de robot cuando sí actúa.
    private static final int ROBOT_VELOCIDAD_MS = 200;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Uso: java SimulacionRobotsSokoban <archivo_entrada.txt>");
            return;
        }

        Config cfg;
        try {
            cfg = leerConfiguracion(args[0]);
        } catch (IOException e) {
            System.out.println("Error al leer el archivo de entrada '" + args[0] + "': " + e.getMessage());
            return;
        } catch (NumberFormatException e) {
            System.out.println("Error de formato en el archivo de entrada: " + e.getMessage());
            return;
        }

        int capacidad = N * N;
        int totalEntidades = cfg.cajasObjetivo + cfg.cajasBloqueo + cfg.numRobots;
        if (totalEntidades > capacidad) {
            System.out.println("Error de configuración: se solicitaron " + totalEntidades
                    + " entidades iniciales (Cajas Objetivo=" + cfg.cajasObjetivo
                    + ", Cajas Bloqueo=" + cfg.cajasBloqueo + ", Robots=" + cfg.numRobots
                    + "), pero el tablero de " + N + "x" + N + " solo tiene " + capacidad + " casillas.");
            System.out.println("Corrija el archivo de entrada y vuelva a ejecutar el programa.");
            return;
        }
        if (cfg.numRobots <= 0) {
            System.out.println("Error de configuración: debe existir al menos 1 robot.");
            return;
        }

        System.out.println("=== Iniciando simulación: Almacén Robótico Concurrente ===");
        System.out.println("Tablero: " + N + "x" + N + " | Meta de extracción: (5,5)");
        System.out.println("Cajas Objetivo iniciales: " + cfg.cajasObjetivo
                + " | Cajas Bloqueo iniciales: " + cfg.cajasBloqueo);
        System.out.println("Robots: " + cfg.numRobots + " (batería inicial " + cfg.bateriaInicial + ")"
                + " | Productores: " + cfg.numProductores);
        System.out.println("=============================================================");
        System.out.println();

        MonitorTablero monitor = new MonitorTablero(N, cfg.numProductores);

        for (int i = 0; i < cfg.cajasObjetivo; i++) {
            monitor.colocarCajaInicial('O');
        }
        for (int i = 0; i < cfg.cajasBloqueo; i++) {
            monitor.colocarCajaInicial('X');
        }

        List<Thread> hilosRobots = new ArrayList<>();
        for (int i = 1; i <= cfg.numRobots; i++) {
            RobotCarga r = new RobotCarga(i, cfg.bateriaInicial, monitor, ROBOT_VELOCIDAD_MS);
            monitor.colocarRobotInicial(i);
            Thread t = new Thread(r, r.getNombre());
            hilosRobots.add(t);
        }

        List<Thread> hilosProductores = new ArrayList<>();
        for (int i = 1; i <= cfg.numProductores; i++) {
            ProductorSokoban p = new ProductorSokoban(i, monitor, PRODUCTOR_DELAY_MIN_MS, PRODUCTOR_DELAY_MAX_MS);
            Thread t = new Thread(p, p.getNombre());
            hilosProductores.add(t);
        }

        for (Thread t : hilosProductores) t.start();
        for (Thread t : hilosRobots) t.start();

        // El hilo main espera a que TODOS los robots terminen (por batería
        // agotada o por la condición de parada adicional documentada).
        for (Thread t : hilosRobots) {
            try {
                t.join();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        // Ya no hay robots activos: se detienen los productores de forma
        // ordenada (bandera + notifyAll para despertar a cualquiera que
        // estuviera esperando por saturación) y se espera su cierre.
        monitor.detenerSimulacion();
        for (Thread t : hilosProductores) {
            try {
                t.join();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        monitor.imprimirReporteFinal();
    }

    private static Config leerConfiguracion(String ruta) throws IOException {
        Config cfg = new Config();
        try (BufferedReader br = new BufferedReader(new FileReader(ruta))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                linea = linea.trim();
                if (linea.isEmpty()) continue;
                String[] partes = linea.split(",");
                for (int i = 0; i < partes.length; i++) partes[i] = partes[i].trim();
                String clave = partes[0];
                switch (clave) {
                    case "Cajas_Objetivo_Iniciales":
                        cfg.cajasObjetivo = Integer.parseInt(partes[1]);
                        break;
                    case "Cajas_Bloqueo_Iniciales":
                        cfg.cajasBloqueo = Integer.parseInt(partes[1]);
                        break;
                    case "Robots":
                        cfg.numRobots = Integer.parseInt(partes[1]);
                        cfg.bateriaInicial = Integer.parseInt(partes[2]);
                        break;
                    case "Productores":
                        cfg.numProductores = Integer.parseInt(partes[1]);
                        break;
                    default:
                        // línea desconocida: se ignora para tolerar comentarios/espacios
                        break;
                }
            }
        }
        return cfg;
    }

    /** Estructura simple para los valores leídos del archivo de entrada. */
    private static class Config {
        int cajasObjetivo = 0;
        int cajasBloqueo = 0;
        int numRobots = 0;
        int bateriaInicial = 0;
        int numProductores = 0;
    }
}
