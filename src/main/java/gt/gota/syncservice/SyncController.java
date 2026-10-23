package gt.gota.syncservice;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync/jobs")
public class SyncController {

    private final SyncService service;

    public SyncController(SyncService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> iniciar() {
        return service.iniciar()
                .map(job -> ResponseEntity.accepted().body(Map.of("jobId", job.getId())))
                .orElseGet(() -> {
                    String enCurso = service.enCurso().map(j -> j.getId()).orElse("desconocido");
                    return ResponseEntity.status(409).body(Map.of(
                            "error", "Ya hay una sincronizacion en proceso",
                            "jobId", enCurso));
                });
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> consultar(@PathVariable String id) {
        return service.buscar(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(404).body(Map.of("error", "Job no encontrado")));
    }
}