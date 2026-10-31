package gt.gota.syncservice;

import java.util.Map;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {

    private final JdbcTemplate jdbc; // conexion a MySQL
    private final MongoTemplate mongo; // conexion a Mongo

    public PingController(JdbcTemplate jdbc, MongoTemplate mongo) {
        this.jdbc = jdbc;
        this.mongo = mongo;
    }

    // GET /api/ping: comprueba que MySQL y Mongo responden
    @GetMapping("/api/ping")
    public Map<String, Object> ping() {
        // cuenta los pagos en MySQL
        Integer pagos = jdbc.queryForObject("SELECT COUNT(*) FROM Pagos", Integer.class);
        // ping a Mongo
        Document respuesta = mongo.executeCommand("{ ping: 1 }");
        return Map.of("mysqlPagos", pagos, "mongoOk", respuesta.get("ok"));
    }
}