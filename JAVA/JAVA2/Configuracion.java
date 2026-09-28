public class Configuracion {

    private int cajasObjetivoIniciales;
    private int cajasBloqueoIniciales;
    private int cantidadRobots;
    private int bateriaInicial;
    private int cantidadProductores;

    public int getCajasObjetivoIniciales() {
        return cajasObjetivoIniciales;
    }

    public void setCajasObjetivoIniciales(int cantidad) {
        this.cajasObjetivoIniciales = cantidad;
    }

    public int getCajasBloqueoIniciales() {
        return cajasBloqueoIniciales;
    }

    public void setCajasBloqueoIniciales(int cantidad) {
        this.cajasBloqueoIniciales = cantidad;
    }

    public int getCantidadRobots() {
        return cantidadRobots;
    }

    public void setCantidadRobots(int cantidad) {
        this.cantidadRobots = cantidad;
    }

    public int getBateriaInicial() {
        return bateriaInicial;
    }

    public void setBateriaInicial(int bateria) {
        this.bateriaInicial = bateria;
    }

    public int getCantidadProductores() {
        return cantidadProductores;
    }

    public void setCantidadProductores(int cantidad) {
        this.cantidadProductores = cantidad;
    }
}