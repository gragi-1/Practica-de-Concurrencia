package cc.carretera;

import org.jcsp.lang.*;
import java.util.*;

public class CarreteraCSP implements Carretera, CSProcess {
    // Canales para cada operación
    private final Any2OneChannel entrarCh = Channel.any2one();
    private final Any2OneChannel avanzarCh = Channel.any2one();
    private final Any2OneChannel salirCh = Channel.any2one();
    private final Any2OneChannel circulandoCh = Channel.any2one();
    private final Any2OneChannel tickCh = Channel.any2one();

    // Configuración de la carretera
    private final int segmentos;
    private final int carriles;

    public CarreteraCSP(int segmentos, int carriles) {
        this.segmentos = segmentos;
        this.carriles = carriles;
        new ProcessManager(this).start();
    }

    // Mensajes para cada operación
    private static class EntrarMsg {
        String id;
        int tks;
        ChannelOutput resp;
        EntrarMsg(String id, int tks, ChannelOutput resp) {
            this.id = id; this.tks = tks; this.resp = resp;
        }
    }
    private static class AvanzarMsg {
        String id;
        int tks;
        ChannelOutput resp;
        AvanzarMsg(String id, int tks, ChannelOutput resp) {
            this.id = id; this.tks = tks; this.resp = resp;
        }
    }
    private static class SalirMsg {
        String id;
        SalirMsg(String id) { this.id = id; }
    }
    private static class CirculandoMsg {
        String id;
        ChannelOutput resp;
        CirculandoMsg(String id, ChannelOutput resp) { this.id = id; this.resp = resp; }
    }
    private static class TickMsg {
        TickMsg() {}
    }

    // Métodos cliente
    public Pos entrar(String car, int tks) {
        One2OneChannel resp = Channel.one2one();
        entrarCh.out().write(new EntrarMsg(car, tks, resp.out()));
        return (Pos) resp.in().read();
    }
    public Pos avanzar(String car, int tks) {
        One2OneChannel resp = Channel.one2one();
        avanzarCh.out().write(new AvanzarMsg(car, tks, resp.out()));
        return (Pos) resp.in().read();
    }
    public void salir(String car) {
        salirCh.out().write(new SalirMsg(car));
    }
    public void circulando(String car) {
        One2OneChannel resp = Channel.one2one();
        circulandoCh.out().write(new CirculandoMsg(car, resp.out()));
        resp.in().read();
    }
    public void tick() {
        tickCh.out().write(new TickMsg());
    }

    // Código del servidor
    public void run() {
        // Estado de la carretera
        boolean[][] ocupado = new boolean[segmentos+1][carriles+1]; // [segmento][carril]
        Map<String, Pos> posiciones = new HashMap<>();
        Map<String, Integer> ticksRem = new HashMap<>();

        // Colas de espera para operaciones bloqueantes
        Queue<EntrarMsg> colaEntrar = new LinkedList<>();
        Queue<AvanzarMsg> colaAvanzar = new LinkedList<>();
        Queue<CirculandoMsg> colaCirculando = new LinkedList<>();

        Guard[] guards = new Guard[] {
            entrarCh.in(), avanzarCh.in(), salirCh.in(), circulandoCh.in(), tickCh.in()
        };
        Alternative servicios = new Alternative(guards);

        while (true) {
            // Calcular guardas activas
            boolean[] enabled = new boolean[5];
            enabled[0] = true; // entrar siempre puede recibir
            enabled[1] = true; // avanzar siempre puede recibir
            enabled[2] = true; // salir siempre puede recibir
            enabled[3] = true; // circulando siempre puede recibir
            enabled[4] = true; // tick siempre puede recibir

            int servicio = servicios.fairSelect(enabled);

            switch (servicio) {
                case 0: { // entrar
                    EntrarMsg msg = (EntrarMsg) entrarCh.in().read();
                    boolean colocado = false;
                    for (int l = 1; l <= carriles; l++) {
                        if (!ocupado[1][l]) {
                            ocupado[1][l] = true;
                            posiciones.put(msg.id, new Pos(1, l));
                            ticksRem.put(msg.id, msg.tks);
                            msg.resp.write(new Pos(1, l));
                            colocado = true;
                            break;
                        }
                    }
                    if (!colocado) {
                        colaEntrar.add(msg);
                    }
                    break;
                }
                case 1: { // avanzar
                    AvanzarMsg msg = (AvanzarMsg) avanzarCh.in().read();
                    Pos cur = posiciones.get(msg.id);
                    int nextSeg = cur.getSegmento() + 1;
                    if (nextSeg > segmentos) {
                        ocupado[cur.getSegmento()][cur.getCarril()] = false;
                        posiciones.remove(msg.id);
                        ticksRem.remove(msg.id);
                        msg.resp.write(new Pos(nextSeg, cur.getCarril()));
                        // Intentar atender coches esperando entrar
                        atenderEntrar(ocupado, posiciones, ticksRem, colaEntrar);
                    } else {
                        boolean colocado = false;
                        for (int l = 1; l <= carriles; l++) {
                            if (!ocupado[nextSeg][l]) {
                                ocupado[cur.getSegmento()][cur.getCarril()] = false;
                                ocupado[nextSeg][l] = true;
                                posiciones.put(msg.id, new Pos(nextSeg, l));
                                ticksRem.put(msg.id, msg.tks);
                                msg.resp.write(new Pos(nextSeg, l));
                                colocado = true;
                                // Intentar atender coches esperando entrar
                                atenderEntrar(ocupado, posiciones, ticksRem, colaEntrar);
                                break;
                            }
                        }
                        if (!colocado) {
                            colaAvanzar.add(msg);
                        }
                    }
                    break;
                }
                case 2: { // salir
                    SalirMsg msg = (SalirMsg) salirCh.in().read();
                    Pos cur = posiciones.get(msg.id);
                    if (cur != null) {
                        ocupado[cur.getSegmento()][cur.getCarril()] = false;
                        posiciones.remove(msg.id);
                        ticksRem.remove(msg.id);
                        // Intentar atender coches esperando entrar
                        atenderEntrar(ocupado, posiciones, ticksRem, colaEntrar);
                    }
                    break;
                }
                case 3: { // circulando
                    CirculandoMsg msg = (CirculandoMsg) circulandoCh.in().read();
                    if (ticksRem.get(msg.id) != null && ticksRem.get(msg.id) > 0) {
                        colaCirculando.add(msg);
                    } else {
                        msg.resp.write(null);
                    }
                    break;
                }
                case 4: { // tick
                    tickCh.in().read();
                    Set<String> coches = new HashSet<>(ticksRem.keySet());
                    for (String id : coches) {
                        Integer t = ticksRem.get(id);
                        if (t != null && t > 0) {
                            ticksRem.put(id, t - 1);
                        }
                    }
                    // Atender circulando si corresponde
                    Iterator<CirculandoMsg> it = colaCirculando.iterator();
                    while (it.hasNext()) {
                        CirculandoMsg msg = it.next();
                        Integer t = ticksRem.get(msg.id);
                        if (t != null && t <= 0) {
                            msg.resp.write(null);
                            it.remove();
                        }
                    }
                    // Atender avanzar si corresponde
                    Iterator<AvanzarMsg> it2 = colaAvanzar.iterator();
                    while (it2.hasNext()) {
                        AvanzarMsg msg = it2.next();
                        Pos cur = posiciones.get(msg.id);
                        if (cur == null) { it2.remove(); continue; }
                        int nextSeg = cur.getSegmento() + 1;
                        if (nextSeg > segmentos) {
                            ocupado[cur.getSegmento()][cur.getCarril()] = false;
                            posiciones.remove(msg.id);
                            ticksRem.remove(msg.id);
                            msg.resp.write(new Pos(nextSeg, cur.getCarril()));
                            it2.remove();
                            atenderEntrar(ocupado, posiciones, ticksRem, colaEntrar);
                        } else {
                            boolean colocado = false;
                            for (int l = 1; l <= carriles; l++) {
                                if (!ocupado[nextSeg][l]) {
                                    ocupado[cur.getSegmento()][cur.getCarril()] = false;
                                    ocupado[nextSeg][l] = true;
                                    posiciones.put(msg.id, new Pos(nextSeg, l));
                                    ticksRem.put(msg.id, msg.tks);
                                    msg.resp.write(new Pos(nextSeg, l));
                                    colocado = true;
                                    it2.remove();
                                    atenderEntrar(ocupado, posiciones, ticksRem, colaEntrar);
                                    break;
                                }
                            }
                            if (!colocado) {
                                // sigue esperando
                            }
                        }
                    }
                    break;
                }
            }
        }
    }

    // Método auxiliar para atender coches esperando entrar
    private void atenderEntrar(boolean[][] ocupado, Map<String, Pos> posiciones, Map<String, Integer> ticksRem, Queue<EntrarMsg> colaEntrar) {
        Iterator<EntrarMsg> it = colaEntrar.iterator();
        while (it.hasNext()) {
            EntrarMsg msg = it.next();
            boolean colocado = false;
            for (int l = 1; l <= carriles; l++) {
                if (!ocupado[1][l]) {
                    ocupado[1][l] = true;
                    posiciones.put(msg.id, new Pos(1, l));
                    ticksRem.put(msg.id, msg.tks);
                    msg.resp.write(new Pos(1, l));
                    colocado = true;
                    it.remove();
                    break;
                }
            }
            if (!colocado) break;
        }
    }
}