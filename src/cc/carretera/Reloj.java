package cc.carretera;

public class Reloj extends Thread {
  private static int MS_POR_TICK
    = 1000;
  private Carretera cr;

  public Reloj(Carretera carretera) {
    this.cr = carretera;
  }

  public void run() {
    while (true) {
      try { sleep(MS_POR_TICK); }
      catch (Exception e) { }
      cr.tick();
    }
  }
}
