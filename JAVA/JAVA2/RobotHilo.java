public class RobotHilo implements Runnable {

    private final Robot robot;
    private final Tablero tablero;

    public RobotHilo(
            Robot robot,
            Tablero tablero) {

        this.robot = robot;
        this.tablero = tablero;
    }
    @Override
    public void run() {

        tablero.registrarInicioRobot(robot);

        while (
                robot.tieneBateria()
                &&
                Main.simulacionActiva()
        ) {

            boolean trabajoRealizado =
                    tablero.ejecutarTurnoRobot(robot);

            /*
             * Si no pudo hacer nada en este turno,
             * espera un poco para no consumir CPU
             * innecesariamente.
             */
            if (!trabajoRealizado) {

                try {
                    Thread.sleep(80);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } else {

                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        tablero.registrarRobotFinalizado(robot);

        Main.robotTerminado();
    }
}