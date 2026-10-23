package gt.gota.syncservice;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "sync_jobs")
public class SyncJob {

    @Id
    private String id;
    private String estado;
    private int progreso;
    private int total;
    private int insertados;
    private int actualizados;
    private int sinCambios;
    private String error;
    private LocalDateTime iniciadoEn;
    private LocalDateTime terminadoEn;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public int getProgreso() { return progreso; }
    public void setProgreso(int progreso) { this.progreso = progreso; }

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }

    public int getInsertados() { return insertados; }
    public void setInsertados(int insertados) { this.insertados = insertados; }

    public int getActualizados() { return actualizados; }
    public void setActualizados(int actualizados) { this.actualizados = actualizados; }

    public int getSinCambios() { return sinCambios; }
    public void setSinCambios(int sinCambios) { this.sinCambios = sinCambios; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public LocalDateTime getIniciadoEn() { return iniciadoEn; }
    public void setIniciadoEn(LocalDateTime iniciadoEn) { this.iniciadoEn = iniciadoEn; }

    public LocalDateTime getTerminadoEn() { return terminadoEn; }
    public void setTerminadoEn(LocalDateTime terminadoEn) { this.terminadoEn = terminadoEn; }
}