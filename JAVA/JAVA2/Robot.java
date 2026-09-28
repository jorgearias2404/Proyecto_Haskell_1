public class Robot {

    private final String id;
    private final int numero;

    private int bateria;
    private Posicion posicion;

    public Robot(
            String id,
            int numero,
            int bateria,
            Posicion posicion) {

        this.id = id;
        this.numero = numero;
        this.bateria = bateria;
        this.posicion = posicion;
    }

    public String getId() {
        return id;
    }

    public int getNumero() {
        return numero;
    }

    public int getBateria() {
        return bateria;
    }

    public Posicion getPosicion() {
        return posicion;
    }

    public void setPosicion(Posicion posicion) {
        this.posicion = posicion;
    }

    public void consumirBateria() {

        if (bateria > 0) {
            bateria--;
        }
    }

    public boolean tieneBateria() {
        return bateria > 0;
    }

    @Override
    public String toString() {
        return id;
    }
}