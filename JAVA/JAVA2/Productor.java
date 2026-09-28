import java.util.Random;

public class Productor implements Runnable {

    private static final double
            PROBABILIDAD_OBJETIVO = 0.70;

    private final String id;
    private final Tablero tablero;
    private final Random random;

    public Productor(
            String id,
            Tablero tablero) {

        this.id = id;
        this.tablero = tablero;

        this.random =
                new Random(
                        System.nanoTime()
                        + id.hashCode()
                );
    }

    @Override
    public void run() {

        while (Main.simulacionActiva()) {

            TipoCaja tipo;

            if (random.nextDouble()
                    < PROBABILIDAD_OBJETIVO) {

                tipo = TipoCaja.OBJETIVO;

            } else {

                tipo = TipoCaja.BLOQUEO;
            }

            boolean insertada =
                    tablero.insertarCaja(
                            id,
                            tipo,
                            random
                    );

            if (!insertada) {
                break;
            }

            try {

                Thread.sleep(
                        120 + random.nextInt(180)
                );

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}