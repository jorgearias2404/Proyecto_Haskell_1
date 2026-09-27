import java.util.Random;

/**
 * ProductorSokoban (Productor / Cinta transportadora)
 * -----------------------------------------------------
 * Cada instancia es un hilo que, mientras la simulación esté activa,
 * espera un tiempo aleatorio (fuera de cualquier candado, para no
 * bloquear a nadie mientras "duerme") y luego intenta insertar una caja
 * nueva en el tablero a través del monitor compartido.
 *
 * Política de temporización (documentada y justificada también en
 * README.md): se usa una espera aleatoria uniforme entre delayMinMs y
 * delayMaxMs entre cada intento de inserción. Esto evita que un
 * productor sature el tablero en milisegundos y deja tiempo a los
 * robots para liberar casillas entre inserciones.
 */
public class ProductorSokoban implements Runnable {

    private final int id;
    private final MonitorTablero monitor;
    private final Random rand = new Random();
    private final int delayMinMs;
    private final int delayMaxMs;

    public ProductorSokoban(int id, MonitorTablero monitor, int delayMinMs, int delayMaxMs) {
        this.id = id;
        this.monitor = monitor;
        this.delayMinMs = delayMinMs;
        this.delayMaxMs = delayMaxMs;
    }

    public String getNombre() {
        return "Productor-" + id;
    }

    @Override
    public void run() {
        try {
            while (monitor.isActiva()) {
                int espera = delayMinMs + rand.nextInt(delayMaxMs - delayMinMs + 1);
                Thread.sleep(espera);
                if (!monitor.isActiva()) break;
                monitor.insertarCaja(this);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
