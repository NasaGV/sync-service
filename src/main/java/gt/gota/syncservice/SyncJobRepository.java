package gt.gota.syncservice;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface SyncJobRepository extends MongoRepository<SyncJob, String> {

    boolean existsByEstado(String estado);

    List<SyncJob> findByEstado(String estado);

    Optional<SyncJob> findFirstByEstado(String estado);
}