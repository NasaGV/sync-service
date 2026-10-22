package gt.gota.syncservice;

import java.util.Map;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {

    private final JdbcTemplate jdbc;
    private final MongoTemplate mongo;

    public PingController(JdbcTemplate jdbc, MongoTemplate mongo) {
        this.jdbc = jdbc;
        this.mongo = mongo;
    }

    @GetMapping("/api/ping")
    public Map<String, Object> ping() {
        Integer pagos = jdbc.queryForObject("SELECT COUNT(*) FROM Pagos", Integer.class);
        Document respuesta = mongo.executeCommand("{ ping: 1 }");
        return Map.of("mysqlPagos", pagos, "mongoOk", respuesta.get("ok"));
    }
}