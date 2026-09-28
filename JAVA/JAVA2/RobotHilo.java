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

            boolean realizoOperacion =
                    tablero.ejecutarMovimientoRobot(
                            robot
                    );

            /*
             * Si no puede moverse, no consume batería.
             *
             * Esto respeta:
             * "Si no hay Cajas Objetivo en el tablero,
             * el robot debe ingresar nuevamente..."
             */
            if (!realizoOperacion) {

                try {

                    Thread.sleep(80);

                } catch (InterruptedException e) {

                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        tablero.registrarRobotFinalizado();

        Main.robotTerminado();
    }
}
