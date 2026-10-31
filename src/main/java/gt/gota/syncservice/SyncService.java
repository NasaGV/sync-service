package gt.gota.syncservice;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SyncService {

    private static final String EN_PROCESO = "EN_PROCESO";

    // clientes tal cual estan en MySQL
    private static final String SQL_CLIENTES =
            "SELECT id, nombre, telefono, direccion, email, fecha_registro, activo "
            + "FROM Clientes ORDER BY id";

    // pagos con su lectura, contador y cliente (JOIN)
    private static final String SQL_PAGOS =
            "SELECT p.id, p.monto, p.fecha_pago, p.metodo, p.estado, p.comprobante, p.observaciones, "
            + "l.id AS lectura_id, l.periodo, l.consumo, l.monto_total, "
            + "c.id AS contador_id, c.codigo, c.sector, "
            + "cl.id AS cliente_id, cl.nombre "
            + "FROM Pagos p "
            + "JOIN Lecturas l ON l.id = p.lectura_id "
            + "JOIN Contadores c ON c.id = l.contador_id "
            + "JOIN Clientes cl ON cl.id = c.cliente_id "
            + "ORDER BY p.id";

    private final SyncJobRepository repo; // jobs en Mongo
    private final JdbcTemplate jdbc; // lectura de MySQL
    private final MongoTemplate mongo; // escritura en Mongo

    // pausa entre registros, solo para la demo (0 = sin pausa)
    @Value("${sync.demora-ms:0}")
    private long demoraMs;

    public SyncService(SyncJobRepository repo, JdbcTemplate jdbc, MongoTemplate mongo) {
        this.repo = repo;
        this.jdbc = jdbc;
        this.mongo = mongo;
    }

    // al arrancar: marca como FALLIDO los jobs que quedaron EN_PROCESO
    @EventListener(ApplicationReadyEvent.class)
    public void limpiarTrabajosHuerfanos() {
        for (SyncJob j : repo.findByEstado(EN_PROCESO)) {
            j.setEstado("FALLIDO");
            j.setError("Interrumpido por reinicio del servicio");
            j.setTerminadoEn(LocalDateTime.now());
            repo.save(j);
        }
    }

    // crea el job y lanza la copia en segundo plano (vacio si ya hay uno corriendo)
    public synchronized Optional<SyncJob> iniciar() {
        // si ya hay uno en proceso, no se crea otro
        if (repo.existsByEstado(EN_PROCESO)) {
            return Optional.empty();
        }
        SyncJob job = new SyncJob();
        job.setEstado(EN_PROCESO);
        job.setProgreso(0);
        job.setIniciadoEn(LocalDateTime.now());
        job = repo.save(job);

        // la copia corre en otro hilo, por eso el POST responde de inmediato
        String id = job.getId();
        CompletableFuture.runAsync(() -> ejecutar(id));
        return Optional.of(job);
    }

    // job que esta corriendo ahora
    public Optional<SyncJob> enCurso() {
        return repo.findFirstByEstado(EN_PROCESO);
    }

    // busca un job por id
    public Optional<SyncJob> buscar(String id) {
        return repo.findById(id);
    }

    // hace la copia completa (corre en otro hilo)
    private void ejecutar(String id) {
        SyncJob job = repo.findById(id).orElseThrow();
        try {
            // lee MySQL
            List<Map<String, Object>> clientes = jdbc.queryForList(SQL_CLIENTES);
            List<Map<String, Object>> pagos = jdbc.queryForList(SQL_PAGOS);
            job.setTotal(clientes.size() + pagos.size());
            repo.save(job);

            int procesados = 0;

            // clientes
            for (Map<String, Object> f : clientes) {
                // el _id es el id de MySQL
                Document doc = new Document("_id", num(f.get("id")).longValue())
                        .append("nombre", f.get("nombre"))
                        .append("telefono", f.get("telefono"))
                        .append("direccion", f.get("direccion"))
                        .append("email", f.get("email"))
                        .append("fechaRegistro", str(f.get("fecha_registro")))
                        .append("activo", f.get("activo"));
                sincronizar(job, "clientes", doc);
                procesados++;
                actualizarProgreso(job, procesados);
            }

            // pagos, con lectura, contador y cliente incrustados
            for (Map<String, Object> f : pagos) {
                Document lectura = new Document("id", num(f.get("lectura_id")).longValue())
                        .append("periodo", str(f.get("periodo")))
                        .append("consumo", dec(f.get("consumo")))
                        .append("montoTotal", dec(f.get("monto_total")));
                Document contador = new Document("id", num(f.get("contador_id")).longValue())
                        .append("codigo", f.get("codigo"))
                        .append("sector", f.get("sector"));
                Document cliente = new Document("id", num(f.get("cliente_id")).longValue())
                        .append("nombre", f.get("nombre"));

                Document doc = new Document("_id", num(f.get("id")).longValue())
                        .append("monto", dec(f.get("monto")))
                        .append("fechaPago", str(f.get("fecha_pago")))
                        .append("metodo", f.get("metodo"))
                        .append("estado", f.get("estado"))
                        .append("comprobante", f.get("comprobante"))
                        .append("observaciones", f.get("observaciones"))
                        .append("lectura", lectura)
                        .append("contador", contador)
                        .append("cliente", cliente);
                sincronizar(job, "pagos", doc);
                procesados++;
                actualizarProgreso(job, procesados);
            }

            job.setProgreso(100);
            job.setEstado("COMPLETADO");
        } catch (Exception e) {
            // cualquier error deja el job como FALLIDO
            job.setEstado("FALLIDO");
            job.setError(e.getMessage());
        }
        job.setTerminadoEn(LocalDateTime.now());
        repo.save(job);
    }

    // inserta, actualiza u omite un documento segun su hash
    private void sincronizar(SyncJob job, String coleccion, Document doc) throws Exception {
        Object id = doc.get("_id");
        // calcula el hash del registro y lo guarda en _hash
        String hash = calcularHash(doc);
        doc.append("_hash", hash);

        // busca el documento en Mongo por _id
        Document existente = mongo.findById(id, Document.class, coleccion);
        if (existente == null) {
            // no existe: inserta
            mongo.save(doc, coleccion);
            job.setInsertados(job.getInsertados() + 1);
        } else if (!hash.equals(existente.getString("_hash"))) {
            // hash distinto: actualiza
            mongo.save(doc, coleccion);
            job.setActualizados(job.getActualizados() + 1);
        } else {
            // hash igual: no hace nada
            job.setSinCambios(job.getSinCambios() + 1);
        }
    }

    // guarda el porcentaje de avance en el job
    private void actualizarProgreso(SyncJob job, int procesados) {
        job.setProgreso(procesados * 100 / job.getTotal());
        repo.save(job);
        // sin pausa en el ultimo registro
        if (procesados < job.getTotal()) {
            pausa();
        }
    }

    // pausa para la demo
    private void pausa() {
        if (demoraMs > 0) {
            try {
                Thread.sleep(demoraMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // SHA-256 de todos los campos menos _id
    private String calcularHash(Document d) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : d.entrySet()) {
            if (!"_id".equals(e.getKey())) {
                sb.append(e.getKey()).append('=').append(e.getValue()).append('|');
            }
        }
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
    }

    // conversores de tipos (MySQL devuelve Object)
    private static Number num(Object o) {
        return (Number) o;
    }

    private static Double dec(Object o) {
        return o == null ? null : ((Number) o).doubleValue();
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}