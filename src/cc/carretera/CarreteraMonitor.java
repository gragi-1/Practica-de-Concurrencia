// Nunca cambia la declaracion del package!
package cc.carretera;

import es.upm.babel.cclib.Monitor;

/**
 * Implementación del recurso compartido Carretera con Monitores
 */
public class CarreteraMonitor implements Carretera {
  // TODO: añadir atributos para representar el estado del recurso y
  // la gestión de la concurrencia (monitor y conditions)

  public CarreteraMonitor(int segmentos, int carriles) {
    // TODO: inicializar estado, monitor y conditions
  }

  public Pos entrar(String id, int tks) {
    // TODO: implementar entrar
    return null;
  }

  public Pos avanzar(String id, int tks) {
    // TODO: implementar avanzar
    return null;
  }

  public void circulando(String id) {
    // TODO: implementar circulando
  }

  public void salir(String id) {
    // TODO: implementar salir
  }

  public void tick() {
    // TODO: implementar tick
  }
}
