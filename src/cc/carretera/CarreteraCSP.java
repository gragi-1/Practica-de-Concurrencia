package cc.carretera;

import org.jcsp.lang.*;
import java.util.*;

public class CarreteraCSP implements Carretera, CSProcess {
    // Canales de petición
    private final Any2OneChannel entrarCh = Channel.any2one();
    private final Any2OneChannel avanzarCh = Channel.any2one();
    private final Any2OneChannel circulandoCh = Channel.any2one();
    private final Any2OneChannel salirCh = Channel.any2one();
    private final Any2OneChannel tickCh = Channel.any2one();

    // Estado interno
    private final int segmentos, carriles;
    private final String[][] ocupacion;
    private final Map<String, Pos> posiciones = new HashMap<>();
    private final Map<String, Integer> ticksRem = new HashMap<>();

    // Colas de peticiones aplazadas
    private final Queue<EntrarMsg> pendingEntrar = new LinkedList<>();
    private final Queue<AvanzarMsg> pendingAvanzar = new LinkedList<>();
    private final Queue<CirculandoMsg> pendingCirc = new LinkedList<>();

    public CarreteraCSP(int segmentos, int carriles) {
        this.segmentos = segmentos;
        this.carriles = carriles;
        this.ocupacion = new String[segmentos][carriles];
        new ProcessManager(this).start();
    }

    // Mensajes de petición
    private static class EntrarMsg {
        final String id;
        final int tks;
        final ChannelOutput resp;
        EntrarMsg(String id, int tks, ChannelOutput resp) {
            this.id = id;
            this.tks = tks;
            this.resp = resp;
        }
    }
    private static class AvanzarMsg {
        final String id;
        final int tks;
        final ChannelOutput resp;
        AvanzarMsg(String id, int tks, ChannelOutput resp) {
            this.id = id;
            this.tks = tks;
            this.resp = resp;
        }
    }
    private static class CirculandoMsg {
        final String id;
        final ChannelOutput resp;
        CirculandoMsg(String id, ChannelOutput resp) {
            this.id = id;
            this.resp = resp;
        }
    }
    private static class SalirMsg { final String id; SalirMsg(String id) { this.id = id; } }
    private static class TickMsg { }

    // Métodos cliente: envían péticion y esperan respuesta si aplica
    public Pos entrar(String id, int tks) {
        One2OneChannel resp = Channel.one2one();
        entrarCh.out().write(new EntrarMsg(id, tks, resp.out()));
        return (Pos) resp.in().read();
    }
    public Pos avanzar(String id, int tks) {
        One2OneChannel resp = Channel.one2one();
        avanzarCh.out().write(new AvanzarMsg(id, tks, resp.out()));
        return (Pos) resp.in().read();
    }
    public void circulando(String id) {
        One2OneChannel resp = Channel.one2one();
        circulandoCh.out().write(new CirculandoMsg(id, resp.out()));
        resp.in().read();
    }
    public void salir(String id) {
        salirCh.out().write(new SalirMsg(id));
    }
    public void tick() {
        tickCh.out().write(new TickMsg());
    }

    // Utility de búsqueda
    private int buscarCarrilLibre(int seg) {
        for (int c = 0; c < carriles; c++) {
            if (ocupacion[seg][c] == null) return c;
        }
        return -1;
    }

    public void run() {
        Guard[] guards = new Guard[]{
            entrarCh.in(), avanzarCh.in(), salirCh.in(), circulandoCh.in(), tickCh.in()
        };
        Alternative alt = new Alternative(guards);
        
        while (true) {
            int sel = alt.fairSelect();
            switch (sel) {
                case 0: { // entrar
                    EntrarMsg m = (EntrarMsg) entrarCh.in().read();
                    if (!procesarEntrar(m)) pendingEntrar.add(m);
                    break;
                }
                case 1: { // avanzar
                    AvanzarMsg m = (AvanzarMsg) avanzarCh.in().read();
                    if (!procesarAvanzar(m)) pendingAvanzar.add(m);
                    break;
                }
                case 2: { // salir
                    SalirMsg m = (SalirMsg) salirCh.in().read();
                    procesarSalir(m.id);
                    break;
                }
                case 3: { // circulando
                    CirculandoMsg m = (CirculandoMsg) circulandoCh.in().read();
                    if (!procesarCirculando(m)) pendingCirc.add(m);
                    break;
                }
                case 4: { // tick
                    tickCh.in().read();
                    ticksRem.keySet().forEach(id ->
                        ticksRem.put(id, Math.max(0, ticksRem.get(id) - 1))
                    );
                    break;
                }
            }
            // Tras cualquier evento, intentar despachar colas
            boolean progreso;
            do {
                progreso = false;
                Iterator<EntrarMsg> itE = pendingEntrar.iterator();
                while (itE.hasNext()) {
                    if (procesarEntrar(itE.next())) { itE.remove(); progreso = true; break; }
                }
                Iterator<AvanzarMsg> itA = pendingAvanzar.iterator();
                while (itA.hasNext()) {
                    if (procesarAvanzar(itA.next())) { itA.remove(); progreso = true; break; }
                }
                Iterator<CirculandoMsg> itC = pendingCirc.iterator();
                while (itC.hasNext()) {
                    if (procesarCirculando(itC.next())) { itC.remove(); progreso = true; break; }
                }
            } while (progreso);
        }
    }

    // Procesamiento de cada operación: devuelve true si se atendió
    private boolean procesarEntrar(EntrarMsg m) {
        int carril = buscarCarrilLibre(0);
        if (carril < 0) return false;
        ocupacion[0][carril] = m.id;
        posiciones.put(m.id, new Pos(1, carril+1));
        ticksRem.put(m.id, m.tks);
        m.resp.write(new Pos(1, carril+1));
        return true;
    }

    private boolean procesarAvanzar(AvanzarMsg m) {
        Pos p = posiciones.get(m.id);
        if (p == null) return false;
        if (ticksRem.get(m.id) > 0) return false;
        int seg = p.getSegmento() - 1;
        int next = seg + 1;
        // salir
        if (next >= segmentos) {
            ocupacion[seg][p.getCarril()-1] = null;
            posiciones.remove(m.id);
            ticksRem.remove(m.id);
            m.resp.write(new Pos(next+1, p.getCarril()));
            return true;
        }
        int carril = buscarCarrilLibre(next);
        if (carril < 0) return false;
        ocupacion[seg][p.getCarril()-1] = null;
        ocupacion[next][carril] = m.id;
        posiciones.put(m.id, new Pos(next+1, carril+1));
        ticksRem.put(m.id, m.tks);
        m.resp.write(new Pos(next+1, carril+1));
        return true;
    }

    private boolean procesarCirculando(CirculandoMsg m) {
        Integer t = ticksRem.get(m.id);
        if (t != null && t <= 0) {
            m.resp.write(null);
            return true;
        }
        return false;
    }

    private void procesarSalir(String id) {
        Pos p = posiciones.remove(id);
        if (p != null) {
            ocupacion[p.getSegmento()-1][p.getCarril()-1] = null;
            ticksRem.remove(id);
        }
    }
}
