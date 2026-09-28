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

            /*
             * Política de generación:
             *
             * 70% Caja Objetivo
             * 30% Caja de Bloqueo
             */
            if (random.nextDouble()
                    < PROBABILIDAD_OBJETIVO) {

                tipo = TipoCaja.OBJETIVO;

            } else {

                tipo = TipoCaja.BLOQUEO;
            }

            boolean insertado =
                    tablero.insertarCaja(
                            id,
                            tipo,
                            random
                    );

            if (!insertado) {
                break;
            }

            try {

                Thread.sleep(
                        100 + random.nextInt(150)
                );

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
