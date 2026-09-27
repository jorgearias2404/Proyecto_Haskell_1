import java.util.Random;

public final class ProductorSokoban extends Thread {
    private final MonitorTablero monitor;
    private final Random aleatorio;
    private final long pausaMs;

    public ProductorSokoban(int id, MonitorTablero monitor, long pausaMs) {
        super("Productor-" + id);
        this.monitor = monitor;
        this.aleatorio = new Random();
        this.pausaMs = pausaMs;
    }

    @Override
    public void run() {
        throw new UnsupportedOperationException(
                "TODO: producir cajas con pausa, registrar saturacion y respetar el cierre");
    }
}