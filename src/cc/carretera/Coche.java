package cc.carretera;

public class Coche extends Thread {
  private Carretera cr;
  private String id;
  private int segmentos;
  private int tks;

  public Coche(Carretera cr,
               String id,
               int segmentos,
               int tks) {
    this.cr = cr;
    this.id = id;
    this.segmentos = segmentos;
    this.tks = tks;
  }

  public void run() {
    cr.entrar(id, tks);
    cr.circulando(id);
    for (int i = 0; i < segmentos; i++) {
      cr.avanzar(id, tks);
      cr.circulando(id);
    }
    cr.salir(id);
  }
}
